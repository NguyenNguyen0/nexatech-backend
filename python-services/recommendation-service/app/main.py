import uuid
from datetime import UTC, datetime
import logging

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.routers.behavior import router as behavior_router
from app.api.routers.health import router as health_router
from app.api.routers.recommend import router as recommend_router
from app.core.config import get_settings
from app.core.lifecycle import lifespan
from app.core.logging import set_request_id


app = FastAPI(
    title="Recommendation Service",
    version="0.1.0",
    lifespan=lifespan,
)

logger = logging.getLogger("recommendation-service")


def _utc_timestamp() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def _resolve_correlation_id(request: Request) -> str:
    return (
        request.headers.get("X-Correlation-Id")
        or request.headers.get("X-Request-Id")
        or str(uuid.uuid4())
    )


@app.middleware("http")
async def request_context_middleware(request: Request, call_next):
    settings = getattr(request.app.state, "settings", get_settings())
    header_name = settings.request_id_header
    correlation_id = _resolve_correlation_id(request)
    request.state.correlation_id = correlation_id
    set_request_id(correlation_id)

    response = await call_next(request)
    response.headers[header_name] = correlation_id
    response.headers["X-Correlation-Id"] = correlation_id
    return response


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    correlation_id = getattr(request.state, "correlation_id", "unknown")
    user_id = request.headers.get("X-User-Id", "anonymous")
    tag = f"REC-{exc.status_code}"

    logger.warning(
        "[%s] %s %s correlationId=%s userId=%s - %s",
        tag,
        request.method,
        request.url.path,
        correlation_id,
        user_id,
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
    tag = "REC-400"
    logger.warning(
        "[%s] %s %s correlationId=%s userId=%s - Validation failed",
        tag,
        request.method,
        request.url.path,
        correlation_id,
        request.headers.get("X-User-Id", "anonymous"),
    )
    return JSONResponse(
        status_code=400,
        content={
            "code": 400,
            "tag": tag,
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
    tag = "REC-500"
    logger.exception(
        "[%s] %s %s correlationId=%s userId=%s - %s",
        tag,
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
            "tag": tag,
            "message": "Internal server error",
            "correlationId": correlation_id,
            "timestamp": _utc_timestamp(),
        },
        headers={"X-Correlation-Id": correlation_id},
    )

app.include_router(health_router)
app.include_router(behavior_router)
app.include_router(recommend_router)
