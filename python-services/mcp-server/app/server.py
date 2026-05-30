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

from app.core.logging import configure_logging, correlation_id_ctx
from app.core.settings import SETTINGS
from app.mcp.server import mcp
from app.tools import audit as _audit_tools  # noqa: F401
from app.tools import health as _health_tools  # noqa: F401
from app.tools import inventory as _inventory_tools  # noqa: F401
from app.tools import news as _news_tools  # noqa: F401
from app.tools import order as _order_tools  # noqa: F401
from app.tools import payment as _payment_tools  # noqa: F401
from app.tools import promotion as _promotion_tools  # noqa: F401


configure_logging(SETTINGS.service_name, SETTINGS.log_level)
logger = logging.getLogger(SETTINGS.service_name)

READINESS_CHECKS: dict[str, str] = {
    "service": "ok",
    "eureka": "pending",
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

    async with mcp.session_manager.run():
        await _register_eureka()
        try:
            yield
        finally:
            await _deregister_eureka()


app = FastAPI(title="MCP Server Skeleton", version="0.1.0", lifespan=lifespan)
app.mount("/mcp", mcp.streamable_http_app())


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
    tag = f"MCP-{exc.status_code}"

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
        "[MCP-400] %s %s correlationId=%s userId=%s - Validation failed",
        request.method,
        request.url.path,
        correlation_id,
        request.headers.get("X-User-Id", "anonymous"),
    )

    return JSONResponse(
        status_code=400,
        content={
            "code": 400,
            "tag": "MCP-400",
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
        "[MCP-500] %s %s correlationId=%s userId=%s - %s",
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
            "tag": "MCP-500",
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
        "message": "mcp-server skeleton is running",
        "mcpEndpoint": "http://localhost:8094/mcp",
        "layout": [
            "app/core",
            "app/mcp",
            "app/clients",
            "app/tools",
            "app/services",
            "app/schemas",
            "app/utils",
        ],
    }