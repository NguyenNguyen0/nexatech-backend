from __future__ import annotations

import asyncio
import json
import logging
import uuid
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from typing import Any

try:
    import py_eureka_client.eureka_client as eureka_client
except ImportError:  # pragma: no cover
    eureka_client = None

from pydantic import BaseModel, ConfigDict, Field
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
from langgraph.types import Command, Interrupt

from app.config import configure_runtime_environment
from app.core.logging import (
    authorization_ctx,
    configure_logging,
    correlation_id_ctx,
    user_email_ctx,
    user_id_ctx,
    user_role_ctx,
)
from app.core.settings import SETTINGS
from app.graph.agent import compile_graph
from app.mcp_client import MCP_CLIENT
from app.runtime.copilotkit import attach_copilotkit_endpoint
from app.hitl.timeout_registry import cancel_timeout, schedule_timeout
from app.runtime.checkpointer import (
    check_checkpointer_health,
    close_checkpointer,
    initialize_checkpointer,
)
from app.runtime.conversation_store import initialize_conversation_store
from app.runtime.delegation_metrics import DELEGATION_METRICS


configure_runtime_environment()
configure_logging(SETTINGS.service_name, SETTINGS.log_level)
logger = logging.getLogger(SETTINGS.service_name)

READINESS_CHECKS: dict[str, str] = {
    "service": "ok",
    "checkpointer": "pending",
    "conversation_store": "pending",
    "eureka": "pending",
    "graph": "pending",
    "copilotkit": "pending",
}

COMPILED_GRAPH: Any | None = None

_DOMAIN_MCP_URLS: dict[str, str] = {
    "inventory": SETTINGS.mcp_inventory_url,
    "product": SETTINGS.mcp_product_url,
    "order": SETTINGS.mcp_order_url,
    "payment": SETTINGS.mcp_payment_url,
    "promotion": SETTINGS.mcp_promotion_url,
    "news": SETTINGS.mcp_news_url,
    "audit": SETTINGS.mcp_audit_url,
}


class AgentRunRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str
    thread_id: str | None = Field(default=None, alias="threadId")
    caller: dict[str, Any] = Field(default_factory=lambda: {"roles": ["operator"]})
    required_role: str | None = Field(default=None, alias="requiredRole")
    request_sensitivity: str | None = Field(default=None, alias="requestSensitivity")
    selected_tool: str | None = Field(default=None, alias="selectedTool")
    selected_tool_args: dict[str, Any] | None = Field(
        default=None, alias="selectedToolArgs"
    )
    run_id: str | None = Field(default=None, alias="runId")


class AgentResumeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    thread_id: str = Field(alias="threadId")
    decision: str | dict[str, Any]


class AgentInterruptRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    thread_id: str = Field(alias="threadId")


class ConversationSessionCreateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    thread_id: str | None = Field(default=None, alias="threadId")
    title: str | None = None
    metadata: dict[str, Any] | None = None


class ConversationSessionUpdateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    title: str | None = None
    metadata: dict[str, Any] | None = None


def _json_safe(value: Any) -> Any:
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [_json_safe(item) for item in value]
    if hasattr(value, "model_dump"):
        return _json_safe(value.model_dump())
    if hasattr(value, "_asdict"):
        return _json_safe(value._asdict())
    if hasattr(value, "__dict__"):
        return _json_safe(vars(value))
    return str(value)


def _serialize_interrupts(interrupts: tuple[Interrupt, ...]) -> list[dict[str, Any]]:
    return [
        {"id": interrupt.id, "value": _json_safe(interrupt.value)}
        for interrupt in interrupts
    ]


def _graph_config(thread_id: str) -> dict[str, Any]:
    return {"configurable": {"thread_id": thread_id}}


async def _invoke_graph(graph_input: Any, *, thread_id: str) -> dict[str, Any]:
    graph = getattr(app.state, "compiled_graph", None)
    if graph is None:
        raise HTTPException(status_code=503, detail="Graph runtime is not initialized")

    config = _graph_config(thread_id)
    result = await graph.ainvoke(
        graph_input, config=config, durability="exit", version="v2"
    )
    interrupts = getattr(result, "interrupts", ())
    if READINESS_CHECKS.get("checkpointer") == "ok":
        snapshot = await graph.aget_state(config)
        state_payload = {
            "values": _json_safe(snapshot.values),
            "next": list(snapshot.next),
            "interrupts": _serialize_interrupts(snapshot.interrupts),
        }
    else:
        output_value = _json_safe(result.value)
        state_payload = {
            "values": output_value,
            "next": [],
            "interrupts": _serialize_interrupts(interrupts),
        }

    return {
        "threadId": thread_id,
        "status": "interrupted" if interrupts else "completed",
        "output": _json_safe(result.value),
        "interrupts": _serialize_interrupts(interrupts),
        "state": state_payload,
    }


async def _persist_conversation_event(
    *,
    thread_id: str,
    event_type: str,
    payload: dict[str, Any],
    request: Request | None,
) -> None:
    store = getattr(app.state, "conversation_store", None)
    if store is None:
        return

    correlation_id = None
    user_id = None
    user_role = None
    user_email = None
    if request is not None:
        correlation_id = getattr(request.state, "correlation_id", None)
        user_id = request.headers.get("X-User-Id")
        user_role = request.headers.get("X-User-Role")
        user_email = request.headers.get("X-User-Email")

    try:
        await store.append_event(
            thread_id=thread_id,
            event_type=event_type,
            payload=payload,
            correlation_id=correlation_id,
            user_id=user_id,
            user_role=user_role,
            user_email=user_email,
        )
    except Exception as exc:  # pragma: no cover
        logger.warning("Failed to persist conversation event (%s): %s", event_type, exc)


def _extract_roles_from_header(raw_roles: str | None) -> list[str]:
    if not raw_roles:
        return []
    normalized = raw_roles.replace(";", ",")
    return [item.strip().lower() for item in normalized.split(",") if item.strip()]


def _build_caller_context(
    request: AgentRunRequest, raw_request: Request
) -> dict[str, Any]:
    caller = dict(request.caller or {})
    caller_roles = caller.get("roles") if isinstance(caller.get("roles"), list) else []
    normalized_roles = [
        str(item).strip().lower() for item in caller_roles if str(item).strip()
    ]

    header_roles = _extract_roles_from_header(raw_request.headers.get("X-User-Role"))
    merged_roles: list[str] = []
    for role in [*normalized_roles, *header_roles]:
        if role not in merged_roles:
            merged_roles.append(role)

    caller["roles"] = merged_roles or ["operator"]

    user_id = raw_request.headers.get("X-User-Id")
    if user_id and not caller.get("userId"):
        caller["userId"] = user_id

    user_email = raw_request.headers.get("X-User-Email")
    if user_email and not caller.get("email"):
        caller["email"] = user_email

    return caller


def _require_user_id(request: Request) -> str:
    user_id = request.headers.get("X-User-Id")
    if not user_id:
        raise HTTPException(status_code=400, detail="X-User-Id header is required")
    return user_id


def _run_event(
    event_type: str, *, run_id: str, thread_id: str, data: dict[str, Any]
) -> dict[str, Any]:
    return {
        "eventType": event_type,
        "runId": run_id,
        "threadId": thread_id,
        "timestamp": _utc_timestamp(),
        "data": _json_safe(data),
    }


def _sse_frame(event_name: str, payload: dict[str, Any]) -> str:
    return f"event: {event_name}\ndata: {json.dumps(payload, ensure_ascii=True)}\n\n"


def _extract_text_chunks(result: dict[str, Any], *, chunk_size: int = 80) -> list[str]:
    text_candidates: list[str] = []
    output = result.get("output")
    state_values = (result.get("state") or {}).get("values")

    if isinstance(output, dict):
        response_text = output.get("response_text")
        if isinstance(response_text, str) and response_text.strip():
            text_candidates.append(response_text)
    if isinstance(state_values, dict):
        response_text = state_values.get("response_text")
        if isinstance(response_text, str) and response_text.strip():
            text_candidates.append(response_text)

    if not text_candidates:
        text_candidates.append(json.dumps(_json_safe(output), ensure_ascii=True))

    text = text_candidates[0]
    return [
        text[index : index + chunk_size] for index in range(0, len(text), chunk_size)
    ]


def _extract_tool_summary(
    result: dict[str, Any],
) -> tuple[str | None, bool, str | None, int | None]:
    output = result.get("output")
    state_values = (result.get("state") or {}).get("values")

    selected_tool: str | None = None
    has_error = False
    error_message: str | None = None
    status_code: int | None = None

    if isinstance(output, dict):
        selected_tool = output.get("selected_tool") or selected_tool
        tool_errors = output.get("tool_errors")
        if isinstance(tool_errors, list) and tool_errors:
            has_error = True
            first_error = tool_errors[0]
            if isinstance(first_error, dict):
                maybe_error = first_error.get("error")
                if isinstance(maybe_error, dict):
                    error_message = maybe_error.get("message")
                    raw_status = maybe_error.get("statusCode")
                    if isinstance(raw_status, int):
                        status_code = raw_status

    if isinstance(state_values, dict):
        selected_tool = state_values.get("selected_tool") or selected_tool
        tool_errors = state_values.get("tool_errors")
        if isinstance(tool_errors, list) and tool_errors:
            has_error = True
            first_error = tool_errors[0]
            if isinstance(first_error, dict):
                maybe_error = first_error.get("error")
                if isinstance(maybe_error, dict) and not error_message:
                    error_message = maybe_error.get("message")
                    raw_status = maybe_error.get("statusCode")
                    if isinstance(raw_status, int):
                        status_code = raw_status

    return selected_tool, has_error, error_message, status_code


async def _log_connected_mcp_tools(message: str, *, base_url: str | None = None) -> list[str]:
    """Log and return only tools actually available from MCP server."""
    target_base_url = (base_url or SETTINGS.mcp_server_url).rstrip("/")
    available_tools = await MCP_CLIENT.list_available_tools(base_url=target_base_url)
    health = await MCP_CLIENT.health(base_url=target_base_url)
    if health.get("ok"):
        if available_tools:
            logger.info(
                "[CHAT] incoming='%s' mcp=connected baseUrl=%s tools=%s",
                message,
                target_base_url,
                available_tools,
            )
        else:
            logger.error(
                "[CHAT] incoming='%s' mcp=connected baseUrl=%s tools=[] error=empty_tool_catalog",
                message,
                target_base_url,
            )
    else:
        logger.warning(
            "[CHAT] incoming='%s' mcp=unreachable baseUrl=%s reason=%s tools=%s",
            message,
            target_base_url,
            health.get("message") or "unknown",
            available_tools,
        )
    return available_tools


def _resolve_domain_mcp_url(domain: str | None) -> str | None:
    if not domain:
        return None
    return _DOMAIN_MCP_URLS.get(domain.strip().lower())


def _extract_routed_domain(route_result: dict[str, Any]) -> str | None:
    if not route_result.get("ok"):
        return None
    data = route_result.get("data")
    if not isinstance(data, dict):
        return None
    domains = data.get("domains")
    if not isinstance(domains, list) or not domains:
        return None
    first_domain = domains[0]
    if not isinstance(first_domain, dict):
        return None
    domain_name = first_domain.get("name")
    if isinstance(domain_name, str) and domain_name.strip():
        return domain_name.strip().lower()
    return None


async def _resolve_mcp_runtime_context(message: str) -> dict[str, Any]:
    routed_domain: str | None = None
    target_url: str | None = None

    if SETTINGS.mcp_enable_domain_routing:
        route_result = await MCP_CLIENT.invoke_tool(
            "route_to_mcp_domain",
            {"intent": message},
            base_url=SETTINGS.mcp_meta_url,
        )
        routed_domain = _extract_routed_domain(route_result)
        if routed_domain:
            DELEGATION_METRICS.record("runtime_domain_route_total", domain=routed_domain)
        else:
            DELEGATION_METRICS.record("runtime_domain_route_miss_total", domain="unknown")

    if routed_domain is None:
        routed_domain = SETTINGS.mcp_default_domain.strip().lower() if SETTINGS.mcp_default_domain else None
        if routed_domain:
            DELEGATION_METRICS.record("runtime_default_domain_total", domain=routed_domain)

    target_url = _resolve_domain_mcp_url(routed_domain) or SETTINGS.mcp_server_url
    available_tools = await _log_connected_mcp_tools(message, base_url=target_url)

    # Fallback to default MCP endpoint if selected domain catalog is unavailable.
    if not available_tools and target_url.rstrip("/") != SETTINGS.mcp_server_url.rstrip("/"):
        DELEGATION_METRICS.record("runtime_domain_catalog_empty_total", domain=routed_domain)
        fallback_tools = await _log_connected_mcp_tools(
            message,
            base_url=SETTINGS.mcp_server_url,
        )
        if fallback_tools:
            DELEGATION_METRICS.record("runtime_catalog_fallback_total", domain=routed_domain)
            return {
                "mcp_target_domain": None,
                "mcp_target_url": SETTINGS.mcp_server_url,
                "mcp_meta_url": SETTINGS.mcp_meta_url,
                "available_tools": fallback_tools,
            }

    return {
        "mcp_target_domain": routed_domain,
        "mcp_target_url": target_url,
        "mcp_meta_url": SETTINGS.mcp_meta_url,
        "available_tools": available_tools,
    }


def _utc_timestamp() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


async def _register_eureka() -> None:
    logger.info("[STARTUP] Checking dependencies...")
    if not SETTINGS.eureka_enabled:
        READINESS_CHECKS["eureka"] = "disabled"
        logger.info("[STARTUP] - Eureka: disabled")
        return

    if eureka_client is None:
        READINESS_CHECKS["eureka"] = "client_missing"
        logger.warning(
            "[STARTUP] - Eureka: client missing, running without registration"
        )
        return

    try:
        await eureka_client.init_async(
            eureka_server=SETTINGS.eureka_server_url,
            app_name=SETTINGS.service_name,
            instance_port=SETTINGS.port,
            instance_host=SETTINGS.eureka_instance_hostname,
            health_check_url=f"http://{SETTINGS.eureka_instance_hostname}:{SETTINGS.port}/health",
            home_page_url=f"http://{SETTINGS.eureka_instance_hostname}:{SETTINGS.port}",
        )
        READINESS_CHECKS["eureka"] = "ok"
        logger.info("[STARTUP] + Eureka: registered")
    except Exception as exc:  # pragma: no cover
        READINESS_CHECKS["eureka"] = f"error: {exc.__class__.__name__}"
        logger.warning(
            "[STARTUP] - Eureka: registration failed (%s)", exc.__class__.__name__
        )


async def _initialize_graph_runtime(app: FastAPI) -> None:
    global COMPILED_GRAPH

    try:
        checkpointer = await initialize_checkpointer(SETTINGS.agent_db_url)
        if checkpointer is None:
            READINESS_CHECKS["checkpointer"] = "disabled"
            logger.info("[STARTUP] - LangGraph checkpointer: disabled")
        else:
            healthy, health_state = await check_checkpointer_health()
            READINESS_CHECKS["checkpointer"] = health_state
            if healthy:
                logger.info("[STARTUP] + LangGraph checkpointer readiness: ok")
            else:
                logger.warning(
                    "[STARTUP] - LangGraph checkpointer readiness: %s",
                    health_state,
                )
        COMPILED_GRAPH = compile_graph(checkpointer=checkpointer)
        app.state.compiled_graph = COMPILED_GRAPH
        READINESS_CHECKS["graph"] = "ok"
    except Exception as exc:
        COMPILED_GRAPH = compile_graph()
        app.state.compiled_graph = COMPILED_GRAPH
        READINESS_CHECKS["checkpointer"] = f"error: {exc.__class__.__name__}"
        READINESS_CHECKS["graph"] = "ok"
        logger.warning(
            "[STARTUP] - LangGraph checkpointer: initialization failed (%s)",
            exc.__class__.__name__,
        )

    copilotkit_paths = [
        "/api/v1/agent/copilotkit",
        "/copilotkit",
    ]
    mounted = False
    for path in copilotkit_paths:
        if attach_copilotkit_endpoint(
            app,
            agent_name=SETTINGS.agui_agent_name,
            agent_description=SETTINGS.agui_agent_description,
            graph=COMPILED_GRAPH,
            path=path,
        ):
            mounted = True

    READINESS_CHECKS["copilotkit"] = "ok" if mounted else "unavailable"


async def _initialize_conversation_runtime(app: FastAPI) -> None:
    store = await initialize_conversation_store(
        enabled=SETTINGS.conversation_store_enabled,
        mongo_uri=SETTINGS.mongo_uri,
        mongo_database=SETTINGS.mongo_database,
        mongo_collection=SETTINGS.mongo_conversation_collection,
        max_events=SETTINGS.conversation_history_max_events,
    )
    app.state.conversation_store = store

    if not SETTINGS.conversation_store_enabled:
        READINESS_CHECKS["conversation_store"] = "disabled"
        logger.info("[STARTUP] - Conversation store: disabled")
    elif store is None:
        READINESS_CHECKS["conversation_store"] = "unavailable"
        logger.warning("[STARTUP] - Conversation store: unavailable")
    else:
        READINESS_CHECKS["conversation_store"] = "ok"
        logger.info("[STARTUP] + Conversation store: mongodb configured")


async def _deregister_eureka() -> None:
    if not SETTINGS.eureka_enabled or eureka_client is None:
        return

    try:
        await eureka_client.stop_async()
    except Exception as exc:  # pragma: no cover
        logger.warning("Failed to deregister from Eureka: %s", exc)


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.settings = SETTINGS
    app.state.readiness_checks = READINESS_CHECKS
    app.state.active_stream_runs = {}
    await _initialize_graph_runtime(app)
    await _initialize_conversation_runtime(app)

    await _register_eureka()
    try:
        yield
    finally:
        await _deregister_eureka()
        await close_checkpointer()
        conversation_store = getattr(app.state, "conversation_store", None)
        if conversation_store is not None:
            await conversation_store.close()


app = FastAPI(title="Agent Service", version="0.1.0", lifespan=lifespan)


@app.middleware("http")
async def correlation_id_middleware(request: Request, call_next):
    correlation_id = request.headers.get("X-Correlation-Id") or str(uuid.uuid4())
    user_id = request.headers.get("X-User-Id") or "-"
    user_role = request.headers.get("X-User-Role") or "-"
    user_email = request.headers.get("X-User-Email") or "-"
    authorization = request.headers.get("Authorization") or "-"

    correlation_token = correlation_id_ctx.set(correlation_id)
    user_id_token = user_id_ctx.set(user_id)
    user_role_token = user_role_ctx.set(user_role)
    user_email_token = user_email_ctx.set(user_email)
    authorization_token = authorization_ctx.set(authorization)
    request.state.correlation_id = correlation_id

    try:
        response = await call_next(request)
    finally:
        correlation_id_ctx.reset(correlation_token)
        user_id_ctx.reset(user_id_token)
        user_role_ctx.reset(user_role_token)
        user_email_ctx.reset(user_email_token)
        authorization_ctx.reset(authorization_token)

    response.headers["X-Correlation-Id"] = correlation_id
    return response


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    correlation_id = getattr(request.state, "correlation_id", "unknown")
    tag = f"AGT-{exc.status_code}"

    logger.warning(
        "[%s] %s %s correlationId=%s userId=%s - %s",
        tag,
        request.method,
        request.url.path,
        correlation_id,
        request.headers.get("X-User-Id", "anonymous"),
        exc.detail,
    )

    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.status_code,
            "tag": tag,
            "message": str(exc.detail),
            "correlationId": correlation_id,
            "timestamp": _utc_timestamp(),
        },
        headers={"X-Correlation-Id": correlation_id},
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    correlation_id = getattr(request.state, "correlation_id", "unknown")
    logger.warning(
        "[AGT-400] %s %s correlationId=%s userId=%s - Validation failed",
        request.method,
        request.url.path,
        correlation_id,
        request.headers.get("X-User-Id", "anonymous"),
    )
    return JSONResponse(
        status_code=400,
        content={
            "code": 400,
            "tag": "AGT-400",
            "message": "Validation failed",
            "correlationId": correlation_id,
            "errors": exc.errors(),
            "timestamp": _utc_timestamp(),
        },
        headers={"X-Correlation-Id": correlation_id},
    )


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    correlation_id = getattr(request.state, "correlation_id", "unknown")
    logger.exception(
        "[AGT-500] %s %s correlationId=%s userId=%s - %s",
        request.method,
        request.url.path,
        correlation_id,
        request.headers.get("X-User-Id", "anonymous"),
        str(exc),
    )
    return JSONResponse(
        status_code=500,
        content={
            "code": 500,
            "tag": "AGT-500",
            "message": "Internal server error",
            "correlationId": correlation_id,
            "timestamp": _utc_timestamp(),
        },
        headers={"X-Correlation-Id": correlation_id},
    )


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP", "service": SETTINGS.service_name}


@app.get("/ready")
async def ready() -> dict[str, object]:
    eureka_state = READINESS_CHECKS.get("eureka", "unknown")
    checkpointer_state = READINESS_CHECKS.get("checkpointer", "unknown")
    degraded = (
        eureka_state.startswith("error")
        or eureka_state == "client_missing"
        or checkpointer_state != "ok"
    )
    return {
        "ready": True,
        "service": SETTINGS.service_name,
        "degraded": degraded,
        "checks": READINESS_CHECKS,
    }


@app.get("/")
async def root() -> dict[str, object]:
    return {
        "message": "agent-service skeleton is running",
        "layout": [
            "app/core",
            "app/graph",
            "app/hitl",
            "app/runtime",
            "app/mcp_client.py",
            "app/config.py",
        ],
    }


@app.get("/metrics/delegation")
@app.get("/api/v1/agent/metrics/delegation")
async def get_delegation_metrics() -> dict[str, Any]:
    return {
        "service": SETTINGS.service_name,
        "metrics": DELEGATION_METRICS.snapshot(),
    }


@app.get("/copilotkit/info")
@app.get("/api/v1/agent/copilotkit/info")
async def copilotkit_info() -> dict[str, Any]:
    """Return runtime metadata used by CopilotKit frontend handshake."""
    return {
        "status": "ok",
        "service": SETTINGS.service_name,
        "runtime": {
            "agui": True,
            "agentName": SETTINGS.agui_agent_name,
            "agentDescription": SETTINGS.agui_agent_description,
        },
        "agents": [
            {
                "id": SETTINGS.agui_agent_name,
                "name": SETTINGS.agui_agent_name,
                "description": SETTINGS.agui_agent_description,
            }
        ],
    }


@app.post("/runs")
@app.post("/api/v1/agent/runs")
async def run_agent(request: AgentRunRequest, raw_request: Request) -> dict[str, Any]:
    thread_id = request.thread_id or str(uuid.uuid4())
    caller = _build_caller_context(request, raw_request)
    mcp_runtime = await _resolve_mcp_runtime_context(request.message)
    graph_input = {
        "messages": [{"role": "user", "content": request.message}],
        "caller": caller,
        "checkpointer_enabled": READINESS_CHECKS.get("checkpointer") == "ok",
        "required_role": request.required_role,
        "request_sensitivity": request.request_sensitivity,
        "selected_tool": request.selected_tool,
        "selected_tool_args": request.selected_tool_args,
        "tool_results": [],
        "tool_errors": [],
        "thread_id": thread_id,
        "request_id": correlation_id_ctx.get(),
        "available_tools": mcp_runtime["available_tools"],
        "mcp_target_domain": mcp_runtime["mcp_target_domain"],
        "mcp_target_url": mcp_runtime["mcp_target_url"],
        "mcp_meta_url": mcp_runtime["mcp_meta_url"],
    }
    await _persist_conversation_event(
        thread_id=thread_id,
        event_type="user_message",
        payload={
            "message": request.message,
            "caller": caller,
        },
        request=raw_request,
    )
    result = await _invoke_graph(graph_input, thread_id=thread_id)
    await _persist_conversation_event(
        thread_id=thread_id,
        event_type="agent_result",
        payload={
            "status": result.get("status"),
            "output": result.get("output"),
            "interrupts": result.get("interrupts"),
        },
        request=raw_request,
    )
    return result


@app.post("/runs/stream")
@app.post("/api/v1/agent/runs/stream")
async def run_agent_stream(
    request: AgentRunRequest, raw_request: Request
) -> StreamingResponse:
    run_id = request.run_id or str(uuid.uuid4())
    thread_id = request.thread_id or str(uuid.uuid4())
    caller = _build_caller_context(request, raw_request)
    mcp_runtime = await _resolve_mcp_runtime_context(request.message)
    graph_input = {
        "messages": [{"role": "user", "content": request.message}],
        "caller": caller,
        "checkpointer_enabled": READINESS_CHECKS.get("checkpointer") == "ok",
        "required_role": request.required_role,
        "request_sensitivity": request.request_sensitivity,
        "selected_tool": request.selected_tool,
        "selected_tool_args": request.selected_tool_args,
        "tool_results": [],
        "tool_errors": [],
        "thread_id": thread_id,
        "request_id": correlation_id_ctx.get(),
        "available_tools": mcp_runtime["available_tools"],
        "mcp_target_domain": mcp_runtime["mcp_target_domain"],
        "mcp_target_url": mcp_runtime["mcp_target_url"],
        "mcp_meta_url": mcp_runtime["mcp_meta_url"],
    }

    await _persist_conversation_event(
        thread_id=thread_id,
        event_type="user_message",
        payload={
            "message": request.message,
            "caller": caller,
            "runId": run_id,
        },
        request=raw_request,
    )

    task = asyncio.create_task(_invoke_graph(graph_input, thread_id=thread_id))
    active_runs: dict[str, dict[str, Any]] = getattr(
        app.state, "active_stream_runs", {}
    )
    active_runs[run_id] = {
        "threadId": thread_id,
        "task": task,
        "cancelled": False,
        "status": "running",
    }

    async def event_generator():
        try:
            yield _sse_frame(
                "run.started",
                _run_event(
                    "run.started",
                    run_id=run_id,
                    thread_id=thread_id,
                    data={"status": "running"},
                ),
            )

            while not task.done():
                run_state = active_runs.get(run_id) or {}
                if run_state.get("cancelled"):
                    task.cancel()
                    active_runs[run_id]["status"] = "cancelled"
                    await _persist_conversation_event(
                        thread_id=thread_id,
                        event_type="run_cancelled",
                        payload={"runId": run_id},
                        request=raw_request,
                    )
                    yield _sse_frame(
                        "run.cancelled",
                        _run_event(
                            "run.cancelled",
                            run_id=run_id,
                            thread_id=thread_id,
                            data={"reason": "cancelled_by_user"},
                        ),
                    )
                    return
                await asyncio.sleep(0.05)

            try:
                result = await task
            except asyncio.CancelledError:
                yield _sse_frame(
                    "run.cancelled",
                    _run_event(
                        "run.cancelled",
                        run_id=run_id,
                        thread_id=thread_id,
                        data={"reason": "cancelled_by_user"},
                    ),
                )
                return
            except Exception as exc:
                active_runs[run_id]["status"] = "error"
                await _persist_conversation_event(
                    thread_id=thread_id,
                    event_type="run_error",
                    payload={
                        "runId": run_id,
                        "message": f"{exc.__class__.__name__}: {str(exc)}",
                    },
                    request=raw_request,
                )
                yield _sse_frame(
                    "run.error",
                    _run_event(
                        "run.error",
                        run_id=run_id,
                        thread_id=thread_id,
                        data={
                            "message": f"{exc.__class__.__name__}: {str(exc)}",
                        },
                    ),
                )
                return

            await _persist_conversation_event(
                thread_id=thread_id,
                event_type="agent_result",
                payload={
                    "runId": run_id,
                    "status": result.get("status"),
                    "output": result.get("output"),
                    "interrupts": result.get("interrupts"),
                },
                request=raw_request,
            )

            selected_tool, has_tool_error, tool_error_message, tool_error_status = (
                _extract_tool_summary(result)
            )
            if selected_tool:
                yield _sse_frame(
                    "tool.started",
                    _run_event(
                        "tool.started",
                        run_id=run_id,
                        thread_id=thread_id,
                        data={
                            "tool": selected_tool,
                            "toolName": selected_tool,
                            "name": selected_tool,
                        },
                    ),
                )
                correlation_id = getattr(raw_request.state, "correlation_id", None)
                last_tool_call = result.get("last_tool_call") or {}
                if has_tool_error:
                    # Log tool call failure with correlationId for grep
                    logger.error(
                        "[TOOL-FAIL][%s] tool=%s status=%s error=%s",
                        correlation_id or "none",
                        selected_tool,
                        tool_error_status,
                        tool_error_message,
                    )
                    yield _sse_frame(
                        "tool.error",
                        _run_event(
                            "tool.error",
                            run_id=run_id,
                            thread_id=thread_id,
                            data={
                                "tool": selected_tool,
                                "toolName": selected_tool,
                                "name": selected_tool,
                                "errorMessage": tool_error_message,
                                "statusCode": tool_error_status,
                                "correlationId": correlation_id,
                                "error": last_tool_call.get("error"),
                            },
                        ),
                    )
                else:
                    yield _sse_frame(
                        "tool.completed",
                        _run_event(
                            "tool.completed",
                            run_id=run_id,
                            thread_id=thread_id,
                            data={
                                "tool": selected_tool,
                                "toolName": selected_tool,
                                "name": selected_tool,
                                "correlationId": correlation_id,
                                "result": last_tool_call.get("result"),
                            },
                        ),
                    )

            for chunk in _extract_text_chunks(result):
                yield _sse_frame(
                    "message.chunk",
                    _run_event(
                        "message.chunk",
                        run_id=run_id,
                        thread_id=thread_id,
                        data={"content": chunk},
                    ),
                )

            interrupts = result.get("interrupts") or []
            for interrupt_item in interrupts:
                interrupt_value = interrupt_item.get("value") or {}
                # Schedule auto-timeout if checkpointer is enabled
                if READINESS_CHECKS.get("checkpointer") == "ok":
                    _timeout_sec = (
                        interrupt_value.get("timeout_seconds")
                        or SETTINGS.approval_timeout_seconds
                    )
                    graph = getattr(app.state, "compiled_graph", None)
                    if graph is not None:
                        schedule_timeout(
                            thread_id,
                            graph=graph,
                            timeout_seconds=_timeout_sec,
                        )
                yield _sse_frame(
                    "hitl.interrupt",
                    _run_event(
                        "hitl.interrupt",
                        run_id=run_id,
                        thread_id=thread_id,
                        data={
                            "interrupt": interrupt_item,
                            "approval_request_id": interrupt_value.get("approval_request_id"),
                            "timeout_seconds": interrupt_value.get("timeout_seconds"),
                            "expires_at": interrupt_value.get("expires_at"),
                        },
                    ),
                )

            active_runs[run_id]["status"] = result.get("status", "completed")
            if interrupts:
                completion_event = "run.interrupted"
                completion_data = {
                    "status": result.get("status"),
                    "interruptCount": len(interrupts),
                }
            else:
                completion_event = "run.completed"
                completion_data = {
                    "status": result.get("status"),
                    "output": result.get("output"),
                }
            yield _sse_frame(
                completion_event,
                _run_event(
                    completion_event,
                    run_id=run_id,
                    thread_id=thread_id,
                    data=completion_data,
                ),
            )
        finally:
            active_runs.pop(run_id, None)

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@app.post("/runs/{run_id}/cancel")
@app.post("/api/v1/agent/runs/{run_id}/cancel")
async def cancel_stream_run(run_id: str) -> dict[str, Any]:
    active_runs: dict[str, dict[str, Any]] = getattr(
        app.state, "active_stream_runs", {}
    )
    run_state = active_runs.get(run_id)
    if run_state is None:
        raise HTTPException(status_code=404, detail="Run not found or already finished")

    run_state["cancelled"] = True
    task = run_state.get("task")
    if isinstance(task, asyncio.Task) and not task.done():
        task.cancel()

    return {
        "runId": run_id,
        "threadId": run_state.get("threadId"),
        "status": "cancellation_requested",
    }


@app.post("/runs/resume")
@app.post("/api/v1/agent/runs/resume")
async def resume_agent(
    request: AgentResumeRequest, raw_request: Request
) -> dict[str, Any]:
    if READINESS_CHECKS.get("checkpointer") != "ok":
        raise HTTPException(
            status_code=503, detail="Resume requires the LangGraph checkpointer"
        )
    # Cancel any pending auto-timeout before processing the decision
    cancel_timeout(request.thread_id)
    await _persist_conversation_event(
        thread_id=request.thread_id,
        event_type="approval_decision",
        payload={"decision": request.decision, "thread_id": request.thread_id},
        request=raw_request,
    )
    result = await _invoke_graph(
        Command(resume=request.decision), thread_id=request.thread_id
    )
    await _persist_conversation_event(
        thread_id=request.thread_id,
        event_type="agent_result",
        payload={
            "status": result.get("status"),
            "output": result.get("output"),
            "interrupts": result.get("interrupts"),
        },
        request=raw_request,
    )
    return result


@app.post("/runs/interrupts")
@app.post("/api/v1/agent/runs/interrupts")
async def get_run_interrupts(request: AgentInterruptRequest) -> dict[str, Any]:
    if READINESS_CHECKS.get("checkpointer") != "ok":
        raise HTTPException(
            status_code=503,
            detail="Interrupt state requires the LangGraph checkpointer",
        )

    graph = getattr(app.state, "compiled_graph", None)
    if graph is None:
        raise HTTPException(status_code=503, detail="Graph runtime is not initialized")

    try:
        snapshot = await graph.aget_state(_graph_config(request.thread_id))
    except Exception as exc:
        raise HTTPException(
            status_code=404, detail=f"Run state not found: {exc.__class__.__name__}"
        ) from exc

    return {
        "threadId": request.thread_id,
        "interrupts": _serialize_interrupts(snapshot.interrupts),
        "next": list(snapshot.next),
    }


@app.get("/runs/{thread_id}")
@app.get("/api/v1/agent/runs/{thread_id}")
async def get_agent_run_state(thread_id: str) -> dict[str, Any]:
    if READINESS_CHECKS.get("checkpointer") != "ok":
        raise HTTPException(
            status_code=503, detail="Run state requires the LangGraph checkpointer"
        )

    graph = getattr(app.state, "compiled_graph", None)
    if graph is None:
        raise HTTPException(status_code=503, detail="Graph runtime is not initialized")

    try:
        snapshot = await graph.aget_state(_graph_config(thread_id))
    except Exception as exc:
        raise HTTPException(
            status_code=404, detail=f"Run state not found: {exc.__class__.__name__}"
        ) from exc

    return {
        "threadId": thread_id,
        "state": {
            "values": _json_safe(snapshot.values),
            "next": list(snapshot.next),
            "interrupts": _serialize_interrupts(snapshot.interrupts),
        },
    }


@app.get("/conversations/{thread_id}")
@app.get("/api/v1/agent/conversations/{thread_id}")
async def get_conversation_thread(
    thread_id: str,
    limit: int = Query(default=100, ge=1, le=1000),
) -> dict[str, Any]:
    store = getattr(app.state, "conversation_store", None)
    if store is None:
        raise HTTPException(
            status_code=503, detail="Conversation store is not available"
        )

    thread = await store.get_thread(thread_id=thread_id, limit=limit)
    if thread is None:
        raise HTTPException(status_code=404, detail="Conversation thread not found")
    return thread


@app.get("/conversations")
@app.get("/api/v1/agent/conversations")
async def list_conversation_threads(
    request: Request,
    user_id: str | None = Query(default=None, alias="userId"),
    limit: int = Query(default=20, ge=1, le=100),
) -> dict[str, Any]:
    store = getattr(app.state, "conversation_store", None)
    if store is None:
        raise HTTPException(
            status_code=503, detail="Conversation store is not available"
        )

    effective_user_id = request.headers.get("X-User-Id") or user_id
    if not effective_user_id:
        raise HTTPException(status_code=400, detail="X-User-Id header is required")

    threads = await store.list_threads_by_user(user_id=effective_user_id, limit=limit)
    return {
        "userId": effective_user_id,
        "count": len(threads),
        "threads": threads,
    }


@app.post("/sessions")
@app.post("/api/v1/agent/sessions")
async def create_conversation_session(
    request: Request,
    payload: ConversationSessionCreateRequest,
) -> dict[str, Any]:
    store = getattr(app.state, "conversation_store", None)
    if store is None:
        raise HTTPException(
            status_code=503, detail="Conversation store is not available"
        )

    owner_user_id = _require_user_id(request)
    thread_id = payload.thread_id or str(uuid.uuid4())
    session = await store.create_session(
        thread_id=thread_id,
        owner_user_id=owner_user_id,
        user_role=request.headers.get("X-User-Role"),
        user_email=request.headers.get("X-User-Email"),
        title=payload.title,
        metadata=payload.metadata,
    )
    return {
        "threadId": thread_id,
        "session": session,
    }


@app.patch("/sessions/{thread_id}")
@app.patch("/api/v1/agent/sessions/{thread_id}")
async def update_conversation_session(
    thread_id: str,
    request: Request,
    payload: ConversationSessionUpdateRequest,
) -> dict[str, Any]:
    store = getattr(app.state, "conversation_store", None)
    if store is None:
        raise HTTPException(
            status_code=503, detail="Conversation store is not available"
        )

    requester_user_id = _require_user_id(request)
    session = await store.update_session(
        thread_id=thread_id,
        requester_user_id=requester_user_id,
        title=payload.title,
        metadata=payload.metadata,
    )
    if session is None:
        raise HTTPException(
            status_code=404, detail="Session not found or access denied"
        )

    return {
        "threadId": thread_id,
        "session": session,
    }


@app.delete("/sessions/{thread_id}")
@app.delete("/api/v1/agent/sessions/{thread_id}")
async def delete_conversation_session(
    thread_id: str, request: Request
) -> dict[str, Any]:
    store = getattr(app.state, "conversation_store", None)
    if store is None:
        raise HTTPException(
            status_code=503, detail="Conversation store is not available"
        )

    requester_user_id = _require_user_id(request)
    deleted = await store.delete_session(
        thread_id=thread_id, requester_user_id=requester_user_id
    )
    if not deleted:
        raise HTTPException(
            status_code=404, detail="Session not found or access denied"
        )

    return {
        "threadId": thread_id,
        "deleted": True,
    }
