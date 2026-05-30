from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.audit import AUDIT_API
from app.services.inventory import INVENTORY_API
from app.services.news import NEWS_API
from app.services.order import ORDER_API
from app.services.payment import PAYMENT_API
from app.services.promotion import PROMOTION_API


SERVICE_APIS = {
    "news-service": NEWS_API,
    "inventory-service": INVENTORY_API,
    "order-service": ORDER_API,
    "payment-service": PAYMENT_API,
    "audit-service": AUDIT_API,
    "promotion-service": PROMOTION_API,
}


@mcp.tool()
async def check_dependency_health(service_name: str | None = None) -> dict[str, Any]:
    if service_name:
        api = SERVICE_APIS.get(service_name)
        if api is None:
            return {
                "ok": False,
                "service": service_name,
                "message": f"Unknown dependency '{service_name}'",
            }
        return {"service": service_name, "health": await api.health()}

    return await check_all_dependency_health()


@mcp.tool()
async def check_all_dependency_health() -> dict[str, Any]:
    result: dict[str, Any] = {"ok": True, "dependencies": {}}
    for service_name, api in SERVICE_APIS.items():
        result["dependencies"][service_name] = await api.health()
    return result


@mcp.tool()
async def check_news_service_health() -> dict[str, Any]:
    return await NEWS_API.health()


@mcp.tool()
async def check_inventory_service_health() -> dict[str, Any]:
    return await INVENTORY_API.health()


@mcp.tool()
async def check_order_service_health() -> dict[str, Any]:
    return await ORDER_API.health()


@mcp.tool()
async def check_payment_service_health() -> dict[str, Any]:
    return await PAYMENT_API.health()


@mcp.tool()
async def check_audit_service_health() -> dict[str, Any]:
    return await AUDIT_API.health()


@mcp.tool()
async def check_promotion_service_health() -> dict[str, Any]:
    return await PROMOTION_API.health()