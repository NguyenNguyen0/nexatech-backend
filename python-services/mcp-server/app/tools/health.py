from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.services.audit import AUDIT_API
from app.services.inventory import INVENTORY_API
from app.services.news import NEWS_API
from app.services.order import ORDER_API
from app.services.payment import PAYMENT_API
from app.services.product import PRODUCT_API
from app.services.promotion import PROMOTION_API
from pydantic import Field


SERVICE_APIS = {
    "news-service": NEWS_API,
    "product-service": PRODUCT_API,
    "inventory-service": INVENTORY_API,
    "order-service": ORDER_API,
    "payment-service": PAYMENT_API,
    "audit-service": AUDIT_API,
    "promotion-service": PROMOTION_API,
}


@mcp.tool()
async def check_dependency_health(
    service_name: Annotated[
        str | None,
        Field(
            description=(
                "Optional service name to check. Supported values: news-service, "
                "product-service, inventory-service, order-service, payment-service, "
                "audit-service, promotion-service. Omit to check every dependency."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """Check MCP downstream dependency health for one service or all services.

    Use this general-purpose health tool before calling domain tools, during
    troubleshooting, or when the user asks whether a dependent service is reachable.
    Unknown service names return an ok=false response listing the requested name.
    """
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
    """Check health for every downstream service known by this MCP server.

    The result contains a dependencies object keyed by service name. Each value is
    the service client's health response, including reachability/error information
    returned by the shared BaseServiceApi health implementation.
    """
    result: dict[str, Any] = {"ok": True, "dependencies": {}}
    for service_name, api in SERVICE_APIS.items():
        result["dependencies"][service_name] = await api.health()
    return result


@mcp.tool()
async def check_news_service_health() -> dict[str, Any]:
    """Check whether news-service is reachable from the MCP server."""
    return await NEWS_API.health()


@mcp.tool()
async def check_inventory_service_health() -> dict[str, Any]:
    """Check whether inventory-service is reachable from the MCP server."""
    return await INVENTORY_API.health()


@mcp.tool()
async def check_product_service_health() -> dict[str, Any]:
    """Check whether product-service is reachable from the MCP server."""
    return await PRODUCT_API.health()


@mcp.tool()
async def check_order_service_health() -> dict[str, Any]:
    """Check whether order-service is reachable from the MCP server."""
    return await ORDER_API.health()


@mcp.tool()
async def check_payment_service_health() -> dict[str, Any]:
    """Check whether payment-service is reachable from the MCP server."""
    return await PAYMENT_API.health()


@mcp.tool()
async def check_audit_service_health() -> dict[str, Any]:
    """Check whether audit-service is reachable from the MCP server."""
    return await AUDIT_API.health()


@mcp.tool()
async def check_promotion_service_health() -> dict[str, Any]:
    """Check whether promotion-service is reachable from the MCP server."""
    return await PROMOTION_API.health()
