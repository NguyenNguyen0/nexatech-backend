from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.order import ORDER_API


@mcp.tool()
async def get_order(order_id: str) -> dict[str, Any]:
    """Retrieve details of a single order by its ID, including items, status, totals, and shipping info."""
    return await ORDER_API.get_order(order_id)


@mcp.tool()
async def get_my_orders() -> dict[str, Any]:
    """List all orders belonging to the current authenticated user, including order history and statuses."""
    return await ORDER_API.my_orders()


@mcp.tool()
async def check_purchased(user_id: str, product_id: str) -> dict[str, Any]:
    """Check whether the given user has purchased a specific product."""
    return await ORDER_API.check_purchased(user_id, product_id)


@mcp.tool()
async def get_order_stats() -> dict[str, Any]:
    """Retrieve order statistics and summaries such as total orders, revenue, cancellation rate, and trends."""
    return await ORDER_API.stats()


@mcp.tool()
async def cancel_order(order_id: str) -> dict[str, Any]:
    """Cancel an order by its ID."""
    return await ORDER_API.cancel_order(order_id)


@mcp.tool()
async def continue_order_payment(order_id: str) -> dict[str, Any]:
    """Resume VNPay payment for a pending order."""
    return await ORDER_API.continue_payment(order_id)


@mcp.tool()
async def get_order_trace(correlation_id: str) -> dict[str, Any]:
    """Retrieve cross-service audit trace for a given correlation ID from order-service."""
    return await ORDER_API.trace_by_correlation(correlation_id)