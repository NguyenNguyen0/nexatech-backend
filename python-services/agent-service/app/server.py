from __future__ import annotations

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
from fastapi.responses import JSONResponse
from langgraph.types import Command, Interrupt

from app.config import configure_runtime_environment
from app.core.logging import (
    configure_logging,
    correlation_id_ctx,
    user_email_ctx,
    user_id_ctx,
    user_role_ctx,
)
from app.core.settings import SETTINGS
from app.graph.agent import compile_graph
from app.runtime.copilotkit import attach_copilotkit_middleware
from app.runtime.checkpointer import close_checkpointer, initialize_checkpointer
from app.runtime.conversation_store import initialize_conversation_store


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


class AgentRunRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str
    thread_id: str | None = Field(default=None, alias="threadId")
    caller: dict[str, Any] = Field(default_factory=lambda: {"roles": ["operator"]})
    required_role: str | None = Field(default=None, alias="requiredRole")
    request_sensitivity: str | None = Field(default=None, alias="requestSensitivity")
    selected_tool: str | None = Field(default=None, alias="selectedTool")
    selected_tool_args: dict[str, Any] | None = Field(default=None, alias="selectedToolArgs")


class AgentResumeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    thread_id: str = Field(alias="threadId")
    decision: str | dict[str, Any]


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
    return [{"id": interrupt.id, "value": _json_safe(interrupt.value)} for interrupt in interrupts]


def _graph_config(thread_id: str) -> dict[str, Any]:
    return {"configurable": {"thread_id": thread_id}}


async def _invoke_graph(graph_input: Any, *, thread_id: str) -> dict[str, Any]:
    graph = getattr(app.state, "compiled_graph", None)
    if graph is None:
        raise HTTPException(status_code=503, detail="Graph runtime is not initialized")

    config = _graph_config(thread_id)
    result = await graph.ainvoke(graph_input, config=config, durability="exit", version="v2")
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


def _build_caller_context(request: AgentRunRequest, raw_request: Request) -> dict[str, Any]:
    caller = dict(request.caller or {})
    caller_roles = caller.get("roles") if isinstance(caller.get("roles"), list) else []
    normalized_roles = [str(item).strip().lower() for item in caller_roles if str(item).strip()]

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
        logger.warning("[STARTUP] - Eureka: client missing, running without registration")
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
        logger.warning("[STARTUP] - Eureka: registration failed (%s)", exc.__class__.__name__)


def _initialize_graph_runtime(app: FastAPI) -> None:
    global COMPILED_GRAPH

    try:
        checkpointer = initialize_checkpointer(SETTINGS.agent_db_url)
        if checkpointer is None:
            READINESS_CHECKS["checkpointer"] = "disabled"
            logger.info("[STARTUP] - LangGraph checkpointer: disabled")
        else:
            READINESS_CHECKS["checkpointer"] = "ok"
        COMPILED_GRAPH = compile_graph(checkpointer=checkpointer)
        app.state.compiled_graph = COMPILED_GRAPH
        READINESS_CHECKS["graph"] = "ok"
    except Exception as exc:
        COMPILED_GRAPH = compile_graph()
        app.state.compiled_graph = COMPILED_GRAPH
        READINESS_CHECKS["checkpointer"] = f"error: {exc.__class__.__name__}"
        READINESS_CHECKS["graph"] = "ok"
        logger.warning("[STARTUP] - LangGraph checkpointer: initialization failed (%s)", exc.__class__.__name__)

    if attach_copilotkit_middleware(
        app,
        agent_name=SETTINGS.agui_agent_name,
        agent_description=SETTINGS.agui_agent_description,
        graph=COMPILED_GRAPH,
    ):
        READINESS_CHECKS["copilotkit"] = "ok"
    else:
        READINESS_CHECKS["copilotkit"] = "unavailable"


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
    _initialize_graph_runtime(app)
    await _initialize_conversation_runtime(app)

    await _register_eureka()
    try:
        yield
    finally:
        await _deregister_eureka()
        close_checkpointer()
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

    correlation_token = correlation_id_ctx.set(correlation_id)
    user_id_token = user_id_ctx.set(user_id)
    user_role_token = user_role_ctx.set(user_role)
    user_email_token = user_email_ctx.set(user_email)
    request.state.correlation_id = correlation_id

    try:
        response = await call_next(request)
    finally:
        correlation_id_ctx.reset(correlation_token)
        user_id_ctx.reset(user_id_token)
        user_role_ctx.reset(user_role_token)
        user_email_ctx.reset(user_email_token)

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
def health() -> dict[str, str]:
    return {"status": "UP", "service": SETTINGS.service_name}


@app.get("/ready")
def ready() -> dict[str, object]:
    eureka_state = READINESS_CHECKS.get("eureka", "unknown")
    degraded = eureka_state.startswith("error") or eureka_state == "client_missing"
    return {
        "ready": True,
        "service": SETTINGS.service_name,
        "degraded": degraded,
        "checks": READINESS_CHECKS,
    }


@app.get("/")
def root() -> dict[str, object]:
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


@app.post("/runs")
@app.post("/api/v1/agent/runs")
async def run_agent(request: AgentRunRequest, raw_request: Request) -> dict[str, Any]:
    thread_id = request.thread_id or str(uuid.uuid4())
    caller = _build_caller_context(request, raw_request)
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


@app.post("/runs/resume")
@app.post("/api/v1/agent/runs/resume")
async def resume_agent(request: AgentResumeRequest, raw_request: Request) -> dict[str, Any]:
    if READINESS_CHECKS.get("checkpointer") != "ok":
        raise HTTPException(status_code=503, detail="Resume requires the LangGraph checkpointer")
    await _persist_conversation_event(
        thread_id=request.thread_id,
        event_type="approval_decision",
        payload={"decision": request.decision},
        request=raw_request,
    )
    result = await _invoke_graph(Command(resume=request.decision), thread_id=request.thread_id)
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


@app.get("/runs/{thread_id}")
@app.get("/api/v1/agent/runs/{thread_id}")
async def get_agent_run_state(thread_id: str) -> dict[str, Any]:
    if READINESS_CHECKS.get("checkpointer") != "ok":
        raise HTTPException(status_code=503, detail="Run state requires the LangGraph checkpointer")

    graph = getattr(app.state, "compiled_graph", None)
    if graph is None:
        raise HTTPException(status_code=503, detail="Graph runtime is not initialized")

    try:
        snapshot = await graph.aget_state(_graph_config(thread_id))
    except Exception as exc:
        raise HTTPException(status_code=404, detail=f"Run state not found: {exc.__class__.__name__}") from exc

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
        raise HTTPException(status_code=503, detail="Conversation store is not available")

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
        raise HTTPException(status_code=503, detail="Conversation store is not available")

    effective_user_id = user_id or request.headers.get("X-User-Id")
    if not effective_user_id:
        raise HTTPException(status_code=400, detail="userId is required when X-User-Id is not provided")

    threads = await store.list_threads_by_user(user_id=effective_user_id, limit=limit)
    return {
        "userId": effective_user_id,
        "count": len(threads),
        "threads": threads,
    }