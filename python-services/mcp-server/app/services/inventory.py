from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


class InventoryServiceApi(BaseServiceApi):
    async def list_inventory(self, params: dict[str, object] | None = None) -> object:
        return await self.client.request(
            "GET",
            "/api/v1/inventory/list",
            params=_clean_list_params(params),
        )

    async def get_inventory(self, product_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/inventory/{product_id}")

    async def get_inventory_movements(
        self,
        product_id: str,
        params: dict[str, object] | None = None,
    ) -> object:
        return await self.client.request(
            "GET",
            f"/api/v1/inventory/{product_id}/movements",
            params=_clean_page_params(params),
        )

    async def update_stock(self, payload: dict[str, object]) -> object:
        return await self.client.request("PUT", "/api/v1/inventory", json=payload)

    async def check_inventory(self, payload: dict[str, object]) -> object:
        return await self.client.request("POST", "/api/v1/inventory/check", json=payload)

    async def reserve_stock(self, payload: dict[str, object]) -> object:
        return await self.client.request("POST", "/api/v1/inventory/reserve", json=payload)

    async def release_stock(self, payload: dict[str, object]) -> object:
        return await self.client.request("POST", "/api/v1/inventory/release", json=payload)

    async def low_stock_items(self) -> object:
        return await self.client.request("GET", "/api/v1/inventory/low-stock")

    async def bulk_import(self, requests: list[dict[str, object]]) -> object:
        return await self.client.request("POST", "/api/v1/inventory/bulk-import", json=requests)

    async def update_warehouse_info(
        self,
        product_id: str,
        payload: dict[str, object],
    ) -> object:
        return await self.client.request(
            "PATCH",
            f"/api/v1/inventory/{product_id}/warehouse",
            json=payload,
        )


def _clean_list_params(params: dict[str, object] | None) -> dict[str, object]:
    allowed = {
        "sku",
        "status",
        "keyword",
        "warehouseLocation",
        "page",
        "size",
        "sort",
    }
    return {
        key: value
        for key, value in (params or {}).items()
        if key in allowed and value not in (None, "")
    }


def _clean_page_params(params: dict[str, object] | None) -> dict[str, object]:
    allowed = {"page", "size", "sort"}
    return {
        key: value
        for key, value in (params or {}).items()
        if key in allowed and value not in (None, "")
    }


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
