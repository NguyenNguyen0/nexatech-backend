import logging
import os
import socket
from contextlib import asynccontextmanager
import uuid
from datetime import datetime, UTC

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
import uvicorn

try:
    import py_eureka_client.eureka_client as eureka_client
except ImportError:  # pragma: no cover
    eureka_client = None


SERVICE_NAME = os.getenv("SERVICE_NAME", "mcp-server")
PORT = int(os.getenv("PORT", "8094"))
EUREKA_ENABLED = os.getenv("EUREKA_ENABLED", "true").lower() == "true"
EUREKA_SERVER_URL = os.getenv("EUREKA_SERVER_URL", "http://localhost:8761/eureka/")
INSTANCE_HOST = os.getenv("EUREKA_INSTANCE_HOSTNAME", socket.gethostbyname(socket.gethostname()))

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(SERVICE_NAME)

@asynccontextmanager
async def lifespan(_: FastAPI):
    if not EUREKA_ENABLED:
        logger.info("Eureka registration is disabled")
        yield
        return

    if eureka_client is None:
        logger.warning("py-eureka-client is not installed, skip Eureka registration")
        yield
        return

    try:
        await eureka_client.init_async(
            eureka_server=EUREKA_SERVER_URL,
            app_name=SERVICE_NAME,
            instance_port=PORT,
            instance_host=INSTANCE_HOST,
            health_check_url=f"http://{INSTANCE_HOST}:{PORT}/health",
            home_page_url=f"http://{INSTANCE_HOST}:{PORT}",
        )
        logger.info("Registered %s to Eureka at %s", SERVICE_NAME, EUREKA_SERVER_URL)
    except Exception as exc:  # pragma: no cover
        logger.warning("Failed to register to Eureka: %s", exc)

    try:
        yield
    finally:
        try:
            await eureka_client.stop_async()
        except Exception as exc:  # pragma: no cover
            logger.warning("Failed to deregister from Eureka: %s", exc)


app = FastAPI(title="MCP Server", version="0.1.0", lifespan=lifespan)


def _utc_timestamp() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


@app.middleware("http")
async def correlation_id_middleware(request: Request, call_next):
    correlation_id = request.headers.get("X-Correlation-Id") or str(uuid.uuid4())
    request.state.correlation_id = correlation_id

    response = await call_next(request)
    response.headers["X-Correlation-Id"] = correlation_id
    return response


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    correlation_id = getattr(request.state, "correlation_id", "unknown")
    user_id = request.headers.get("X-User-Id", "anonymous")
    tag = f"MCP-{exc.status_code}"

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
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    correlation_id = getattr(request.state, "correlation_id", "unknown")
    tag = "MCP-400"
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
    )


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    correlation_id = getattr(request.state, "correlation_id", "unknown")
    tag = "MCP-500"
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
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": SERVICE_NAME}


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "mcp-server is running"}


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=PORT, reload=False)
