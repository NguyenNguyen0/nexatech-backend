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
        user_role: str | None,
        user_email: str | None,
    ) -> None:
        now = datetime.now(UTC)
        event = {
            "id": str(uuid.uuid4()),
            "timestamp": now,
            "eventType": event_type,
            "correlationId": correlation_id,
            "userId": user_id,
            "userRole": user_role,
            "userEmail": user_email,
            "payload": payload,
        }
        set_payload: dict[str, Any] = {
            "updatedAt": now,
        }
        add_to_set: dict[str, Any] = {}
        if user_id:
            set_payload["lastUserId"] = user_id
            add_to_set["userIds"] = user_id
        if user_role:
            set_payload["lastUserRole"] = user_role
            add_to_set["userRoles"] = user_role
        if user_email:
            set_payload["lastUserEmail"] = user_email

        update: dict[str, Any] = {
            "$setOnInsert": {
                "service": "agent-service",
                "createdAt": now,
            },
            "$set": set_payload,
            "$push": {
                "events": {
                    "$each": [event],
                    "$slice": -self.max_events,
                }
            },
        }
        if add_to_set:
            update["$addToSet"] = add_to_set

        await self._collection.update_one(
            {"_id": thread_id},
            update,
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
            "lastUserId": document.get("lastUserId"),
            "lastUserRole": document.get("lastUserRole"),
            "lastUserEmail": document.get("lastUserEmail"),
            "userIds": document.get("userIds") or [],
            "userRoles": document.get("userRoles") or [],
            "events": [
                {
                    "id": item.get("id"),
                    "timestamp": _to_iso(item.get("timestamp")),
                    "eventType": item.get("eventType"),
                    "correlationId": item.get("correlationId"),
                    "userId": item.get("userId"),
                    "userRole": item.get("userRole"),
                    "userEmail": item.get("userEmail"),
                    "payload": item.get("payload"),
                }
                for item in events
            ],
        }

    async def list_threads_by_user(self, *, user_id: str, limit: int = 20) -> list[dict[str, Any]]:
        cursor = self._collection.find(
            {"userIds": user_id},
            {
                "events": 0,
            },
        ).sort("updatedAt", -1).limit(limit)
        documents = await cursor.to_list(length=limit)

        return [
            {
                "threadId": item.get("_id"),
                "service": item.get("service", "agent-service"),
                "createdAt": _to_iso(item.get("createdAt")),
                "updatedAt": _to_iso(item.get("updatedAt")),
                "title": item.get("title"),
                "metadata": item.get("metadata") or {},
                "lastUserId": item.get("lastUserId"),
                "lastUserRole": item.get("lastUserRole"),
                "lastUserEmail": item.get("lastUserEmail"),
                "ownerUserId": item.get("ownerUserId"),
                "userIds": item.get("userIds") or [],
                "userRoles": item.get("userRoles") or [],
            }
            for item in documents
        ]

    async def create_session(
        self,
        *,
        thread_id: str,
        owner_user_id: str,
        user_role: str | None,
        user_email: str | None,
        title: str | None,
        metadata: dict[str, Any] | None,
    ) -> dict[str, Any]:
        now = datetime.now(UTC)
        add_to_set: dict[str, Any] = {
            "userIds": owner_user_id,
        }
        if user_role:
            add_to_set["userRoles"] = user_role

        await self._collection.update_one(
            {"_id": thread_id},
            {
                "$setOnInsert": {
                    "service": "agent-service",
                    "createdAt": now,
                    "ownerUserId": owner_user_id,
                },
                "$set": {
                    "updatedAt": now,
                    "title": title,
                    "metadata": metadata or {},
                    "lastUserId": owner_user_id,
                    "lastUserRole": user_role,
                    "lastUserEmail": user_email,
                },
                "$addToSet": add_to_set,
            },
            upsert=True,
        )
        session = await self.get_thread(thread_id=thread_id, limit=0)
        return session or {"threadId": thread_id}

    async def update_session(
        self,
        *,
        thread_id: str,
        requester_user_id: str,
        title: str | None,
        metadata: dict[str, Any] | None,
    ) -> dict[str, Any] | None:
        document = await self._collection.find_one({"_id": thread_id})
        if document is None:
            return None

        user_ids = document.get("userIds") or []
        owner_user_id = document.get("ownerUserId")
        if requester_user_id not in user_ids and requester_user_id != owner_user_id:
            return None

        update_fields: dict[str, Any] = {"updatedAt": datetime.now(UTC)}
        if title is not None:
            update_fields["title"] = title
        if metadata is not None:
            update_fields["metadata"] = metadata

        await self._collection.update_one(
            {"_id": thread_id},
            {"$set": update_fields},
        )
        return await self.get_thread(thread_id=thread_id, limit=0)

    async def delete_session(self, *, thread_id: str, requester_user_id: str) -> bool:
        document = await self._collection.find_one({"_id": thread_id})
        if document is None:
            return False

        user_ids = document.get("userIds") or []
        owner_user_id = document.get("ownerUserId")
        if requester_user_id not in user_ids and requester_user_id != owner_user_id:
            return False

        result = await self._collection.delete_one({"_id": thread_id})
        return result.deleted_count == 1

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
        store = MongoConversationStore(
            client,
            mongo_database,
            mongo_collection,
            max_events=max_events,
        )
        await store._collection.create_index("updatedAt")
        await store._collection.create_index("userIds")
        return store
    except Exception as exc:  # pragma: no cover
        logger.warning("Mongo conversation store initialization failed: %s", exc)
        return None