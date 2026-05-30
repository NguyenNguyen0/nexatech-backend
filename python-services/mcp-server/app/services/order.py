from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


class OrderServiceApi(BaseServiceApi):
    async def get_order(self, order_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/orders/{order_id}")

    async def my_orders(self) -> object:
        return await self.client.request("GET", "/api/v1/orders/my")

    async def check_purchased(self, product_id: str) -> object:
        return await self.client.request("GET", "/api/v1/orders/check-purchased", params={"productId": product_id})

    async def stats(self) -> object:
        return await self.client.request("GET", "/api/v1/admin/orders/stats")

    async def trace_by_correlation(self, correlation_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/admin/audit/trace/{correlation_id}")


ORDER_API = OrderServiceApi(
    ServiceClient(
        ServiceDefinition(
            name="order-service",
            base_url=SETTINGS.order_service_base_url,
            health_path=SETTINGS.order_service_health_path,
            timeout_seconds=SETTINGS.service_request_timeout_seconds,
            retry_attempts=SETTINGS.service_retry_attempts,
            retry_backoff_seconds=SETTINGS.service_retry_backoff_seconds,
            retry_max_backoff_seconds=SETTINGS.service_retry_max_backoff_seconds,
        )
    )
)