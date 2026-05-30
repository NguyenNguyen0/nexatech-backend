from __future__ import annotations

from app.core.http import ServiceClient


class BaseServiceApi:
    def __init__(self, client: ServiceClient) -> None:
        self.client = client

    async def health(self) -> dict[str, object]:
        return await self.client.health()