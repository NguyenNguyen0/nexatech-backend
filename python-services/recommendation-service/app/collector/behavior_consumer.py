import json
import logging

from aiokafka import AIOKafkaConsumer

from app.repositories.mongo_repo import MongoRepository
from app.schemas.events import UserBehaviorEvent


logger = logging.getLogger("recommendation-service.behavior-consumer")


class BehaviorConsumer:
    def __init__(self, bootstrap_servers: str, topic: str, group_id: str, mongo_repo: MongoRepository) -> None:
        self._topic = topic
        self._mongo_repo = mongo_repo
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
        logger.info("Behavior consumer started for topic=%s", self._topic)
        try:
            async for message in self._consumer:
                try:
                    event = UserBehaviorEvent.from_payload(message.value)
                except ValueError as exc:
                    logger.warning("Skip invalid behavior event: %s", exc)
                    continue

                try:
                    applied = await self._mongo_repo.apply_behavior_event(event)
                    if not applied:
                        logger.debug("Duplicate behavior event skipped: eventId=%s", event.event_id)
                except Exception as exc:  # pragma: no cover
                    logger.exception("Failed to persist behavior event: %s", exc)
        finally:
            await self._consumer.stop()
            logger.info("Behavior consumer stopped")

    async def stop(self) -> None:
        await self._consumer.stop()
