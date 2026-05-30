from __future__ import annotations

import logging
import uuid
from datetime import UTC, datetime
from typing import Any


logger = logging.getLogger(__name__)


class MongoConversationStore:
    def __init__(self, client: Any, database: str, collection: str, *, max_events: int) -> None:
        self.client = client
        self.database = database
        self.collection = collection
        self.max_events = max_events
        self._collection = client[database][collection]

    async def append_event(
        self,
        *,
        thread_id: str,
        event_type: str,
        payload: dict[str, Any],
        correlation_id: str | None,
        user_id: str | None,
    ) -> None:
        now = datetime.now(UTC)
        event = {
            "id": str(uuid.uuid4()),
            "timestamp": now,
            "eventType": event_type,
            "correlationId": correlation_id,
            "userId": user_id,
            "payload": payload,
        }
        await self._collection.update_one(
            {"_id": thread_id},
            {
                "$setOnInsert": {
                    "service": "agent-service",
                    "createdAt": now,
                },
                "$set": {
                    "updatedAt": now,
                },
                "$push": {
                    "events": {
                        "$each": [event],
                        "$slice": -self.max_events,
                    }
                },
            },
            upsert=True,
        )

    async def get_thread(self, *, thread_id: str, limit: int = 100) -> dict[str, Any] | None:
        document = await self._collection.find_one({"_id": thread_id})
        if document is None:
            return None

        events = document.get("events") or []
        if limit > 0:
            events = events[-limit:]

        return {
            "threadId": document.get("_id"),
            "service": document.get("service", "agent-service"),
            "createdAt": _to_iso(document.get("createdAt")),
            "updatedAt": _to_iso(document.get("updatedAt")),
            "events": [
                {
                    "id": item.get("id"),
                    "timestamp": _to_iso(item.get("timestamp")),
                    "eventType": item.get("eventType"),
                    "correlationId": item.get("correlationId"),
                    "userId": item.get("userId"),
                    "payload": item.get("payload"),
                }
                for item in events
            ],
        }

    async def close(self) -> None:
        self.client.close()


def _to_iso(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.astimezone(UTC).isoformat().replace("+00:00", "Z")
    return str(value)


async def initialize_conversation_store(
    *,
    enabled: bool,
    mongo_uri: str | None,
    mongo_database: str,
    mongo_collection: str,
    max_events: int,
) -> MongoConversationStore | None:
    if not enabled:
        return None
    if not mongo_uri:
        return None

    try:
        from motor.motor_asyncio import AsyncIOMotorClient
    except Exception as exc:  # pragma: no cover
        logger.warning("Mongo conversation store is not available: %s", exc)
        return None

    try:
        client = AsyncIOMotorClient(mongo_uri, serverSelectionTimeoutMS=1500)
        await client.admin.command("ping")
        return MongoConversationStore(
            client,
            mongo_database,
            mongo_collection,
            max_events=max_events,
        )
    except Exception as exc:  # pragma: no cover
        logger.warning("Mongo conversation store initialization failed: %s", exc)
        return None