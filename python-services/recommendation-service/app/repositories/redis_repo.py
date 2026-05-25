from redis.asyncio import Redis


class RedisRepository:
    def __init__(self, redis_url: str) -> None:
        self.client = Redis.from_url(redis_url, encoding="utf-8", decode_responses=True)

    async def ping(self) -> bool:
        return bool(await self.client.ping())

    async def close(self) -> None:
        await self.client.aclose()
