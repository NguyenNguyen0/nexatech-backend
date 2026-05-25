import logging
import os
import socket
from contextlib import asynccontextmanager

from fastapi import FastAPI
import uvicorn

try:
    import py_eureka_client.eureka_client as eureka_client
except ImportError:  # pragma: no cover
    eureka_client = None


SERVICE_NAME = os.getenv("SERVICE_NAME", "recommendation-service")
PORT = int(os.getenv("PORT", "8095"))
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


app = FastAPI(title="Recommendation Service", version="0.1.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": SERVICE_NAME}


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "recommendation-service is running"}


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=PORT, reload=False)
