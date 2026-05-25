import logging
import asyncio
from contextlib import asynccontextmanager, suppress

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from fastapi import FastAPI

from app.clients.kafka_client import KafkaClient
from app.clients.product_service_client import ProductServiceClient
from app.collector.behavior_consumer import BehaviorConsumer
from app.collector.product_event_consumer import ProductEventConsumer
from app.core.config import get_settings
from app.core.logging import configure_logging
from app.repositories.mongo_repo import MongoRepository
from app.repositories.redis_repo import RedisRepository
from app.services.product_enrichment import ProductRecommendationEnricher
from app.sync.product_catalog_sync import ProductCatalogSync
from app.training.pipeline import TrainingPipeline
from app.training.pipeline import TrainingSettings

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
    product_service_client = ProductServiceClient(
        base_url=settings.product_service_base_url,
        catalog_path=settings.product_service_catalog_path,
        lookup_path=settings.product_service_lookup_path,
        api_key=settings.product_service_api_key,
        api_key_header=settings.product_service_api_key_header,
    )
    recommendation_enricher = ProductRecommendationEnricher(product_service_client)
    product_catalog_sync = ProductCatalogSync(mongo_repo, product_service_client)
    product_event_consumer = ProductEventConsumer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        topic=settings.kafka_product_topic,
        group_id=settings.kafka_product_consumer_group,
        catalog_sync=product_catalog_sync,
    )
    behavior_consumer = BehaviorConsumer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        topic=settings.kafka_behavior_topic,
        group_id=settings.kafka_behavior_consumer_group,
        mongo_repo=mongo_repo,
    )
    training_pipeline = TrainingPipeline(
        mongo_repo=mongo_repo,
        redis_repo=redis_repo,
        settings=TrainingSettings(
            alpha=settings.cf_alpha,
            model_refresh_seconds=settings.recommendation_model_refresh_seconds,
            cache_ttl_seconds=settings.recommendation_cache_ttl_seconds,
            models_dir=settings.models_dir,
        ),
    )
    scheduler = AsyncIOScheduler(timezone=settings.training_timezone)
    product_consumer_task = None
    behavior_consumer_task = None

    app.state.mongo_repo = mongo_repo
    app.state.redis_repo = redis_repo
    app.state.kafka_client = kafka_client
    app.state.product_service_client = product_service_client
    app.state.recommendation_enricher = recommendation_enricher
    app.state.product_catalog_sync = product_catalog_sync
    app.state.product_event_consumer = product_event_consumer
    app.state.behavior_consumer = behavior_consumer
    app.state.training_pipeline = training_pipeline
    app.state.scheduler = scheduler
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

    try:
        await product_catalog_sync.initial_sync_if_empty()
    except Exception as exc:  # pragma: no cover
        logger.warning("Initial product catalog sync failed: %s", exc)

    product_consumer_task = asyncio.create_task(product_event_consumer.start())
    behavior_consumer_task = asyncio.create_task(behavior_consumer.start())

    try:
        await training_pipeline.run(trigger="startup")
    except Exception as exc:  # pragma: no cover
        logger.warning("Initial model training failed: %s", exc)

    scheduler.add_job(
        training_pipeline.run_scheduled_training,
        CronTrigger(
            hour=settings.training_cron_hour,
            minute=settings.training_cron_minute,
            timezone=settings.training_timezone,
        ),
        id="recommendation-nightly-training",
        replace_existing=True,
        coalesce=True,
        max_instances=1,
    )
    scheduler.start()

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
        if product_consumer_task is not None:
            product_consumer_task.cancel()
            with suppress(asyncio.CancelledError):
                await product_consumer_task
        if behavior_consumer_task is not None:
            behavior_consumer_task.cancel()
            with suppress(asyncio.CancelledError):
                await behavior_consumer_task
        await product_event_consumer.stop()
        await behavior_consumer.stop()
        if scheduler.running:
            scheduler.shutdown(wait=False)
        await product_service_client.close()
        await redis_repo.close()
        await mongo_repo.close()
        if settings.eureka_enabled and eureka_client is not None:
            try:
                await eureka_client.stop_async()
            except Exception as exc:  # pragma: no cover
                logger.warning("Eureka deregistration failed: %s", exc)
