from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.schemas.tool_contract import tool_result
from app.services.payment import PAYMENT_API
from pydantic import Field


@mcp.tool()
async def create_payment(
    order_id: Annotated[
        str,
        Field(description="Order ID that the new payment should be created for."),
    ],
    method: Annotated[
        str,
        Field(
            description=(
                "Payment method requested by the user. Common values are VNPAY, "
                "COD, or WALLET, depending on payment-service configuration."
            ),
        ),
    ],
    idempotency_key: Annotated[
        str | None,
        Field(
            description=(
                "Optional Idempotency-Key header. Provide a stable unique key when "
                "retrying payment creation so duplicate requests do not create "
                "duplicate payment records."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """Create a payment for an order.

    This tool has side effects. Use it only when the caller explicitly needs to
    start a payment flow or create a payment record for an order. Include
    idempotency_key for retries, timeouts, or client re-submissions.
    """
    return tool_result(
        await PAYMENT_API.create_payment(order_id, method, idempotency_key),
        requires_approval=True,
        idempotency_key=idempotency_key,
    )


@mcp.tool()
async def get_payment(
    payment_id: Annotated[
        str,
        Field(description="Payment ID to retrieve from payment-service."),
    ],
) -> dict[str, Any]:
    """Retrieve details of a single payment by payment ID.

    Use this when the caller needs payment status, amount, method, external
    gateway reference, timestamps, refund state, or order linkage for one payment.
    """
    return tool_result(await PAYMENT_API.get_payment(payment_id))


@mcp.tool()
async def get_payment_by_order(
    order_id: Annotated[
        str,
        Field(description="Order ID whose associated payment should be retrieved."),
    ],
) -> dict[str, Any]:
    """Retrieve the payment associated with a specific order ID.

    Use this when the caller has an order ID but not a payment ID, such as order
    detail pages, checkout recovery, support lookups, or reconciliation flows.
    """
    return tool_result(await PAYMENT_API.get_payment_by_order(order_id))


@mcp.tool()
async def get_my_payments() -> dict[str, Any]:
    """List payments belonging to the current authenticated user.

    Use this for customer payment history, account dashboards, receipt lookup, or
    support flows where the current request identity determines which payments are
    visible.
    """
    return tool_result(await PAYMENT_API.my_payments())


@mcp.tool()
async def refund_payment(
    payment_id: Annotated[
        str,
        Field(description="Payment ID to refund."),
    ],
    idempotency_key: Annotated[
        str | None,
        Field(description="Optional idempotency key for caller-side retry tracking."),
    ] = None,
) -> dict[str, Any]:
    """Request a refund for a payment.

    This tool has side effects. Use it only when the caller explicitly wants to
    initiate a refund. The payment-service decides whether the payment is eligible
    based on status, method, timing, and gateway rules.
    """
    return tool_result(
        await PAYMENT_API.refund(payment_id),
        requires_approval=True,
        idempotency_key=idempotency_key,
    )


@mcp.tool()
async def get_payment_stats() -> dict[str, Any]:
    """Retrieve administrative payment statistics.

    Use this for back-office dashboards and reporting. The response may include
    totals, revenue, success/failure rates, refund metrics, gateway distribution,
    or payment-method breakdowns returned by payment-service.
    """
    return tool_result(await PAYMENT_API.stats())
