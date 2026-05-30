from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.payment import PAYMENT_API


@mcp.tool()
async def get_payment(payment_id: str) -> dict[str, Any]:
    return await PAYMENT_API.get_payment(payment_id)


@mcp.tool()
async def get_payment_by_order(order_id: str) -> dict[str, Any]:
    return await PAYMENT_API.get_payment_by_order(order_id)


@mcp.tool()
async def get_my_payments() -> dict[str, Any]:
    return await PAYMENT_API.my_payments()


@mcp.tool()
async def get_payment_stats() -> dict[str, Any]:
    return await PAYMENT_API.stats()