from __future__ import annotations

import json

from aiokafka import AIOKafkaProducer


class BehaviorProducer:
    def __init__(self, bootstrap_servers: str, topic: str) -> None:
        self._topic = topic
        self._producer = AIOKafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda payload: json.dumps(payload).encode("utf-8"),
        )

    async def start(self) -> None:
        await self._producer.start()

    async def stop(self) -> None:
        await self._producer.stop()

    async def send_behavior(self, event: dict) -> None:
        key = str(event.get("userId", "")).encode("utf-8")
        await self._producer.send_and_wait(topic=self._topic, value=event, key=key)
