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
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from langgraph.types import Command, Interrupt

from app.config import configure_runtime_environment
from app.core.logging import configure_logging, correlation_id_ctx
from app.core.settings import SETTINGS
from app.graph.agent import compile_graph
from app.runtime.copilotkit import attach_copilotkit_middleware
from app.runtime.checkpointer import close_checkpointer, initialize_checkpointer


configure_runtime_environment()
configure_logging(SETTINGS.service_name, SETTINGS.log_level)
logger = logging.getLogger(SETTINGS.service_name)

READINESS_CHECKS: dict[str, str] = {
    "service": "ok",
    "checkpointer": "pending",
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

    await _register_eureka()
    try:
        yield
    finally:
        await _deregister_eureka()
        close_checkpointer()


app = FastAPI(title="Agent Service", version="0.1.0", lifespan=lifespan)


@app.middleware("http")
async def correlation_id_middleware(request: Request, call_next):
    correlation_id = request.headers.get("X-Correlation-Id") or str(uuid.uuid4())
    token = correlation_id_ctx.set(correlation_id)
    request.state.correlation_id = correlation_id

    try:
        response = await call_next(request)
    finally:
        correlation_id_ctx.reset(token)

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


@app.post("/api/v1/agent/runs")
async def run_agent(request: AgentRunRequest) -> dict[str, Any]:
    thread_id = request.thread_id or str(uuid.uuid4())
    graph_input = {
        "messages": [{"role": "user", "content": request.message}],
        "caller": request.caller,
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
    return await _invoke_graph(graph_input, thread_id=thread_id)


@app.post("/api/v1/agent/runs/resume")
async def resume_agent(request: AgentResumeRequest) -> dict[str, Any]:
    if READINESS_CHECKS.get("checkpointer") != "ok":
        raise HTTPException(status_code=503, detail="Resume requires the LangGraph checkpointer")
    return await _invoke_graph(Command(resume=request.decision), thread_id=request.thread_id)


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