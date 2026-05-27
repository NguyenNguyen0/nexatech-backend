import json

from redis.asyncio import Redis


class RedisRepository:
    def __init__(self, redis_url: str) -> None:
        self.client = Redis.from_url(redis_url, encoding="utf-8", decode_responses=True)

    async def ping(self) -> bool:
        return bool(await self.client.ping())

    async def get_json(self, key: str):
        raw_value = await self.client.get(key)
        if raw_value is None:
            return None
        try:
            return json.loads(raw_value)
        except json.JSONDecodeError:
            return None

    async def set_json(self, key: str, value, ttl_seconds: int) -> None:
        await self.client.set(key, json.dumps(value), ex=max(ttl_seconds, 1))

    async def delete_pattern(self, pattern: str) -> None:
        keys = []
        async for key in self.client.scan_iter(match=pattern):
            keys.append(key)
        if keys:
            await self.client.delete(*keys)

    async def close(self) -> None:
        await self.client.aclose()
