from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


class PaymentServiceApi(BaseServiceApi):
    async def create_payment(self, order_id: str, method: str, idempotency_key: str | None = None) -> object:
        headers = {"Idempotency-Key": idempotency_key} if idempotency_key else {}
        return await self.client.request(
            "POST", "/api/v1/payments",
            json={"orderId": order_id, "method": method},
            headers=headers,
        )

    async def get_payment(self, payment_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/payments/{payment_id}")

    async def get_payment_by_order(self, order_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/payments/order/{order_id}")

    async def my_payments(self) -> object:
        return await self.client.request("GET", "/api/v1/payments/my")

    async def refund(self, payment_id: str) -> object:
        return await self.client.request("POST", f"/api/v1/payments/{payment_id}/refund")

    async def stats(self) -> object:
        return await self.client.request("GET", "/api/v1/admin/payments/stats")


PAYMENT_API = PaymentServiceApi(
    ServiceClient(
        ServiceDefinition(
            name="payment-service",
            base_url=SETTINGS.payment_service_base_url,
            health_path=SETTINGS.payment_service_health_path,
            timeout_seconds=SETTINGS.service_request_timeout_seconds,
            retry_attempts=SETTINGS.service_retry_attempts,
            retry_backoff_seconds=SETTINGS.service_retry_backoff_seconds,
            retry_max_backoff_seconds=SETTINGS.service_retry_max_backoff_seconds,
        )
    )
)