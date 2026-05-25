from motor.motor_asyncio import AsyncIOMotorClient
from pymongo import ASCENDING, DESCENDING
from pymongo import UpdateOne


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

        await product_catalog.create_index([("active", ASCENDING)], name="idx_product_catalog_active")
        await user_interactions.create_index(
            [("userId", ASCENDING), ("productId", ASCENDING)],
            unique=True,
            name="uq_user_product",
        )
        await user_interactions.create_index([("lastTs", DESCENDING)], name="idx_user_interactions_last_ts")

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

    async def close(self) -> None:
        self.client.close()
