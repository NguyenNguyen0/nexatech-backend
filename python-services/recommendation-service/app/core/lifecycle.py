import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.core.config import get_settings
from app.core.logging import configure_logging

try:
    import py_eureka_client.eureka_client as eureka_client
except ImportError:  # pragma: no cover
    eureka_client = None


logger = logging.getLogger("recommendation-service.lifecycle")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    configure_logging(settings.log_level)

    app.state.settings = settings
    app.state.readiness_checks = {
        "mongo": "not_configured",
        "redis": "not_configured",
        "kafka": "not_configured",
        "model_store": "not_configured",
    }

    if settings.eureka_enabled and eureka_client is not None:
        try:
            await eureka_client.init_async(
                eureka_server=settings.eureka_server_url,
                app_name=settings.service_name,
                instance_port=settings.port,
                instance_host=settings.eureka_instance_hostname,
                health_check_url=f"http://{settings.eureka_instance_hostname}:{settings.port}/health",
                home_page_url=f"http://{settings.eureka_instance_hostname}:{settings.port}",
            )
            logger.info("Registered to Eureka: %s", settings.eureka_server_url)
        except Exception as exc:  # pragma: no cover
            logger.warning("Eureka registration failed: %s", exc)
    elif settings.eureka_enabled:
        logger.warning("py-eureka-client not installed, skip Eureka registration")

    try:
        yield
    finally:
        if settings.eureka_enabled and eureka_client is not None:
            try:
                await eureka_client.stop_async()
            except Exception as exc:  # pragma: no cover
                logger.warning("Eureka deregistration failed: %s", exc)
