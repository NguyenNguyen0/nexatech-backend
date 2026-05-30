from __future__ import annotations

import logging
import uuid
from contextlib import asynccontextmanager
from datetime import UTC, datetime

try:
    import py_eureka_client.eureka_client as eureka_client
except ImportError:  # pragma: no cover
    eureka_client = None

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import configure_runtime_environment
from app.core.logging import configure_logging, correlation_id_ctx
from app.core.settings import SETTINGS
from app.graph.agent import compile_graph
from app.runtime.copilotkit import attach_copilotkit_middleware


configure_runtime_environment()
configure_logging(SETTINGS.service_name, SETTINGS.log_level)
logger = logging.getLogger(SETTINGS.service_name)

READINESS_CHECKS: dict[str, str] = {
    "service": "ok",
    "eureka": "pending",
    "graph": "pending",
    "copilotkit": "pending",
}

COMPILED_GRAPH = compile_graph()


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

    await _register_eureka()
    try:
        yield
    finally:
        await _deregister_eureka()


app = FastAPI(title="Agent Service", version="0.1.0", lifespan=lifespan)

READINESS_CHECKS["graph"] = "ok"
if attach_copilotkit_middleware(
    app,
    agent_name=SETTINGS.agui_agent_name,
    agent_description=SETTINGS.agui_agent_description,
    graph=COMPILED_GRAPH,
):
    READINESS_CHECKS["copilotkit"] = "ok"
else:
    READINESS_CHECKS["copilotkit"] = "unavailable"


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