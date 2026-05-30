from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.order import ORDER_API


@mcp.tool()
async def get_order(order_id: str) -> dict[str, Any]:
    return await ORDER_API.get_order(order_id)


@mcp.tool()
async def get_my_orders() -> dict[str, Any]:
    return await ORDER_API.my_orders()


@mcp.tool()
async def check_purchased(product_id: str) -> dict[str, Any]:
    return await ORDER_API.check_purchased(product_id)


@mcp.tool()
async def get_order_stats() -> dict[str, Any]:
    return await ORDER_API.stats()


@mcp.tool()
async def get_order_trace(correlation_id: str) -> dict[str, Any]:
    return await ORDER_API.trace_by_correlation(correlation_id)