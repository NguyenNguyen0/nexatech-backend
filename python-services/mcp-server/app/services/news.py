from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


class NewsServiceApi(BaseServiceApi):
    async def list_public_news(self, page: int = 0, size: int = 10) -> object:
        return await self.client.request(
            "GET",
            "/api/v1/news/public",
            params={"page": page, "size": size},
        )

    async def get_public_news_by_slug(self, slug: str) -> object:
        return await self.client.request("GET", f"/api/v1/news/public/{slug}")

    async def get_news_by_slug(self, slug: str) -> object:
        return await self.client.request("GET", f"/api/v1/news/{slug}")

    async def get_news_images(self, news_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/news/{news_id}/images")


NEWS_API = NewsServiceApi(
    ServiceClient(
        ServiceDefinition(
            name="news-service",
            base_url=SETTINGS.news_service_base_url,
            health_path=SETTINGS.news_service_health_path,
            timeout_seconds=SETTINGS.service_request_timeout_seconds,
            retry_attempts=SETTINGS.service_retry_attempts,
            retry_backoff_seconds=SETTINGS.service_retry_backoff_seconds,
            retry_max_backoff_seconds=SETTINGS.service_retry_max_backoff_seconds,
        )
    )
)