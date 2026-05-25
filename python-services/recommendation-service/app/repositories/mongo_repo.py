from motor.motor_asyncio import AsyncIOMotorClient
from pymongo import ASCENDING, DESCENDING


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

    async def close(self) -> None:
        self.client.close()
