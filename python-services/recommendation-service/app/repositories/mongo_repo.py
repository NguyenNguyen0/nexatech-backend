from datetime import UTC, datetime

from motor.motor_asyncio import AsyncIOMotorClient
from pymongo import ASCENDING, DESCENDING
from pymongo import UpdateOne

from app.schemas.events import UserBehaviorEvent


class MongoRepository:
    def __init__(self, mongo_uri: str, database_name: str) -> None:
        self.client = AsyncIOMotorClient(mongo_uri)
        self.db = self.client[database_name]

    async def ping(self) -> bool:
        result = await self.db.command("ping")
        return result.get("ok") == 1

    async def init_indexes(self) -> None:
        product_catalog = self.db["product_catalog"]
        user_interactions = self.db["user_interactions"]
        behavior_event_dedup = self.db["behavior_event_dedup"]

        await product_catalog.create_index([("active", ASCENDING)], name="idx_product_catalog_active")
        await user_interactions.create_index(
            [("userId", ASCENDING), ("productId", ASCENDING)],
            unique=True,
            name="uq_user_product",
        )
        await user_interactions.create_index([("lastTs", DESCENDING)], name="idx_user_interactions_last_ts")
        await behavior_event_dedup.create_index(
            [("createdAt", ASCENDING)],
            name="idx_behavior_event_dedup_ttl",
            expireAfterSeconds=7 * 24 * 60 * 60,
        )

    async def count_product_catalog(self) -> int:
        return int(await self.db["product_catalog"].count_documents({}))

    async def upsert_product_catalog_items(self, items: list[dict]) -> None:
        if not items:
            return

        operations = []
        for item in items:
            item_id = item.get("id") or item.get("productId")
            if not item_id:
                continue

            operations.append(
                UpdateOne(
                    {"_id": str(item_id)},
                    {
                        "$set": {
                            "name": item.get("name"),
                            "slug": item.get("slug"),
                            "description": item.get("description"),
                            "primaryCategoryId": item.get("primaryCategoryId"),
                            "primaryCategorySlug": item.get("primaryCategorySlug"),
                            "categoryIds": item.get("categoryIds", []),
                            "categorySlugs": item.get("categorySlugs", []),
                            "brandId": item.get("brandId"),
                            "price": float(item.get("price") or 0),
                            "specs": item.get("specs") or {},
                            "active": bool(item.get("active", True)),
                            "updatedAt": item.get("updatedAt"),
                        }
                    },
                    upsert=True,
                )
            )

        if operations:
            await self.db["product_catalog"].bulk_write(operations, ordered=False)

    async def apply_product_event(self, event: dict) -> None:
        product_id = event.get("productId")
        if not product_id:
            return

        event_type = str(event.get("eventType", "")).upper()
        if event_type == "PRODUCT_DELETED":
            await self.db["product_catalog"].update_one(
                {"_id": str(product_id)},
                {"$set": {"active": False, "updatedAt": event.get("timestamp")}},
                upsert=True,
            )
            return

        update_doc = {
            "name": event.get("name"),
            "primaryCategoryId": event.get("primaryCategoryId"),
            "active": bool(event.get("active", True)),
            "updatedAt": event.get("timestamp"),
        }
        await self.db["product_catalog"].update_one(
            {"_id": str(product_id)},
            {"$set": update_doc},
            upsert=True,
        )

    async def apply_behavior_event(self, event: UserBehaviorEvent) -> bool:
        dedup_result = await self.db["behavior_event_dedup"].update_one(
            {"_id": event.event_id},
            {"$setOnInsert": {"createdAt": event.timestamp}},
            upsert=True,
        )
        if dedup_result.upserted_id is None:
            return False

        now = datetime.now(UTC)
        set_fields = {
            "lastTs": event.timestamp,
            "updatedAt": now,
            "lastEventType": event.event_type,
        }
        if event.category_ids:
            set_fields["categoryIds"] = event.category_ids
        if event.brand_id is not None:
            set_fields["brandId"] = event.brand_id
        if event.price is not None:
            set_fields["price"] = event.price

        await self.db["user_interactions"].update_one(
            {"userId": event.user_id, "productId": event.product_id},
            {
                "$inc": {"score": event.weight, f"eventCounts.{event.event_type}": 1},
                "$set": set_fields,
                "$min": {"firstTs": event.timestamp},
                "$setOnInsert": {"createdAt": now},
            },
            upsert=True,
        )
        return True

    async def count_user_interactions(self) -> int:
        return int(await self.db["user_interactions"].count_documents({}))

    async def list_active_products(self) -> list[dict]:
        cursor = self.db["product_catalog"].find(
            {"active": True},
            {
                "_id": 1,
                "name": 1,
                "description": 1,
                "primaryCategoryId": 1,
                "primaryCategorySlug": 1,
                "categoryIds": 1,
                "categorySlugs": 1,
                "brandId": 1,
                "price": 1,
                "specs": 1,
                "active": 1,
            },
        )
        products = await cursor.to_list(length=None)
        for product in products:
            product["_id"] = str(product.get("_id"))
        return products

    async def list_interactions(self) -> list[dict]:
        cursor = self.db["user_interactions"].find(
            {},
            {"_id": 0, "userId": 1, "productId": 1, "score": 1, "lastTs": 1, "firstTs": 1},
        )
        return await cursor.to_list(length=None)

    async def list_user_seen_products(self, user_id: str) -> set[str]:
        cursor = self.db["user_interactions"].find({"userId": user_id}, {"_id": 0, "productId": 1})
        rows = await cursor.to_list(length=None)
        return {str(row.get("productId")) for row in rows if row.get("productId")}

    async def get_top_popular_products(self, top_k: int) -> list[dict]:
        pipeline = [
            {"$group": {"_id": "$productId", "score": {"$sum": "$score"}}},
            {"$sort": {"score": -1}},
            {"$limit": int(top_k)},
        ]
        rows = await self.db["user_interactions"].aggregate(pipeline).to_list(length=int(top_k))
        return [{"productId": str(row.get("_id")), "score": float(row.get("score") or 0)} for row in rows]

    async def close(self) -> None:
        self.client.close()
