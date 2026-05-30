from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


class PromotionServiceApi(BaseServiceApi):
    async def list_promotions(self) -> object:
        return await self.client.request("GET", "/api/v1/promotions")

    async def get_promotion(self, promotion_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/promotions/{promotion_id}")

    async def list_flash_sales(self) -> object:
        return await self.client.request("GET", "/api/v1/flash-sales")

    async def get_flash_sale(self, flash_sale_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/flash-sales/{flash_sale_id}")


PROMOTION_API = PromotionServiceApi(
    ServiceClient(
        ServiceDefinition(
            name="promotion-service",
            base_url=SETTINGS.promotion_service_base_url,
            health_path=SETTINGS.promotion_service_health_path,
            timeout_seconds=SETTINGS.service_request_timeout_seconds,
            retry_attempts=SETTINGS.service_retry_attempts,
            retry_backoff_seconds=SETTINGS.service_retry_backoff_seconds,
            retry_max_backoff_seconds=SETTINGS.service_retry_max_backoff_seconds,
        )
    )
)