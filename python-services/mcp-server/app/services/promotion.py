from __future__ import annotations

from typing import Any

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


class PromotionServiceApi(BaseServiceApi):
    async def list_promotions(self, status: str | None = None) -> object:
        return await self.client.request(
            "GET",
            "/api/v1/admin/flash-sales",
            params={"status": status},
        )

    async def get_promotion(self, promotion_id: str) -> object:
        promotions = await self.list_promotions()
        item = _find_item(promotions, promotion_id, keys=("promotionId", "id", "code"))
        if item is None:
            return _not_found("promotion-service", "PRO-4040", f"Promotion not found: {promotion_id}")
        return item

    async def list_flash_sales(self) -> object:
        return await self.client.request("GET", "/api/v1/flash-sales")

    async def get_flash_sale(self, flash_sale_id: str) -> object:
        flash_sales = await self.list_promotions()
        item = _find_item(flash_sales, flash_sale_id, keys=("id", "promotionId", "code"))
        if item is None:
            return _not_found("promotion-service", "PRO-4041", f"Flash sale not found: {flash_sale_id}")
        return item


def _items(payload: object) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        raw_items = payload.get("items")
        if isinstance(raw_items, list):
            return [item for item in raw_items if isinstance(item, dict)]
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    return []


def _find_item(payload: object, lookup_id: str, *, keys: tuple[str, ...]) -> dict[str, Any] | None:
    for item in _items(payload):
        if any(str(item.get(key) or "") == lookup_id for key in keys):
            return item
    return None


def _not_found(service: str, tag: str, message: str) -> dict[str, Any]:
    return {
        "ok": False,
        "service": service,
        "statusCode": 404,
        "tag": tag,
        "message": message,
        "error": {
            "service": service,
            "statusCode": 404,
            "tag": tag,
            "message": message,
        },
    }


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
