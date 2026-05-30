from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


class InventoryServiceApi(BaseServiceApi):
    async def list_inventory(self) -> object:
        return await self.client.request("GET", "/api/v1/inventory/list")

    async def get_inventory(self, product_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/inventory/{product_id}")

    async def get_inventory_movements(self, product_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/inventory/{product_id}/movements")

    async def check_inventory(self, payload: dict[str, object]) -> object:
        return await self.client.request("POST", "/api/v1/inventory/check", json=payload)

    async def low_stock_items(self) -> object:
        return await self.client.request("GET", "/api/v1/inventory/low-stock")


INVENTORY_API = InventoryServiceApi(
    ServiceClient(
        ServiceDefinition(
            name="inventory-service",
            base_url=SETTINGS.inventory_service_base_url,
            health_path=SETTINGS.inventory_service_health_path,
            timeout_seconds=SETTINGS.service_request_timeout_seconds,
            retry_attempts=SETTINGS.service_retry_attempts,
            retry_backoff_seconds=SETTINGS.service_retry_backoff_seconds,
            retry_max_backoff_seconds=SETTINGS.service_retry_max_backoff_seconds,
        )
    )
)