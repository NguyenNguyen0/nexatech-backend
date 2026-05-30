from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.payment import PAYMENT_API


@mcp.tool()
async def create_payment(order_id: str, method: str, idempotency_key: str | None = None) -> dict[str, Any]:
    """Create a new payment for an order. method: VNPAY | COD | WALLET. Optionally provide idempotency_key to safely retry."""
    return await PAYMENT_API.create_payment(order_id, method, idempotency_key)


@mcp.tool()
async def get_payment(payment_id: str) -> dict[str, Any]:
    """Retrieve details of a single payment by its ID, including status, amount, method, and timestamps."""
    return await PAYMENT_API.get_payment(payment_id)


@mcp.tool()
async def get_payment_by_order(order_id: str) -> dict[str, Any]:
    """Retrieve the payment associated with a specific order ID."""
    return await PAYMENT_API.get_payment_by_order(order_id)


@mcp.tool()
async def get_my_payments() -> dict[str, Any]:
    """List all payments belonging to the current authenticated user, including payment history and invoice details."""
    return await PAYMENT_API.my_payments()


@mcp.tool()
async def refund_payment(payment_id: str) -> dict[str, Any]:
    """Request a refund for a completed payment."""
    return await PAYMENT_API.refund(payment_id)


@mcp.tool()
async def get_payment_stats() -> dict[str, Any]:
    """Retrieve payment statistics and summaries such as total revenue, payment method distribution, and success rates."""
    return await PAYMENT_API.stats()