import json
import logging

from aiokafka import AIOKafkaConsumer

from app.sync.product_catalog_sync import ProductCatalogSync


logger = logging.getLogger("recommendation-service.product-event-consumer")


class ProductEventConsumer:
    def __init__(self, bootstrap_servers: str, topic: str, group_id: str, catalog_sync: ProductCatalogSync) -> None:
        self._topic = topic
        self._catalog_sync = catalog_sync
        self._consumer = AIOKafkaConsumer(
            topic,
            bootstrap_servers=bootstrap_servers,
            group_id=group_id,
            enable_auto_commit=True,
            value_deserializer=lambda v: json.loads(v.decode("utf-8")),
            auto_offset_reset="latest",
        )

    async def start(self) -> None:
        await self._consumer.start()
        logger.info("Product event consumer started for topic=%s", self._topic)
        try:
            async for message in self._consumer:
                await self._catalog_sync.apply_event(message.value)
        finally:
            await self._consumer.stop()
            logger.info("Product event consumer stopped")

    async def stop(self) -> None:
        await self._consumer.stop()
