import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.clients.kafka_client import KafkaClient
from app.core.config import get_settings
from app.core.logging import configure_logging
from app.repositories.mongo_repo import MongoRepository
from app.repositories.redis_repo import RedisRepository

try:
    import py_eureka_client.eureka_client as eureka_client
except ImportError:  # pragma: no cover
    eureka_client = None


logger = logging.getLogger("recommendation-service.lifecycle")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    configure_logging(settings.log_level)

    mongo_repo = MongoRepository(settings.mongo_uri, settings.mongo_database)
    redis_repo = RedisRepository(settings.redis_url)
    kafka_client = KafkaClient(settings.kafka_bootstrap_servers)

    app.state.mongo_repo = mongo_repo
    app.state.redis_repo = redis_repo
    app.state.kafka_client = kafka_client
    app.state.settings = settings
    app.state.readiness_checks = {
        "mongo": "pending",
        "redis": "pending",
        "kafka": "pending",
        "model_store": "not_configured",
    }

    try:
        await mongo_repo.init_indexes()
        app.state.readiness_checks["mongo"] = "ok"
    except Exception as exc:  # pragma: no cover
        app.state.readiness_checks["mongo"] = f"error: {exc.__class__.__name__}"
        logger.warning("Mongo index initialization failed: %s", exc)

    try:
        if await redis_repo.ping():
            app.state.readiness_checks["redis"] = "ok"
    except Exception as exc:  # pragma: no cover
        app.state.readiness_checks["redis"] = f"error: {exc.__class__.__name__}"
        logger.warning("Redis ping failed: %s", exc)

    try:
        if await kafka_client.ping():
            app.state.readiness_checks["kafka"] = "ok"
    except Exception as exc:  # pragma: no cover
        app.state.readiness_checks["kafka"] = f"error: {exc.__class__.__name__}"
        logger.warning("Kafka ping failed: %s", exc)

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
        await redis_repo.close()
        await mongo_repo.close()
        if settings.eureka_enabled and eureka_client is not None:
            try:
                await eureka_client.stop_async()
            except Exception as exc:  # pragma: no cover
                logger.warning("Eureka deregistration failed: %s", exc)
