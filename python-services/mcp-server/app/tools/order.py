from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.services.order import ORDER_API
from pydantic import Field


@mcp.tool()
async def get_order(
    order_id: Annotated[
        str,
        Field(description="Order ID to retrieve from order-service."),
    ],
) -> dict[str, Any]:
    """Retrieve details of a single order by order ID.

    Use this when the caller needs the full order view, including line items,
    customer/user context, status, totals, shipping information, payment state, and
    timestamps returned by order-service.
    """
    return await ORDER_API.get_order(order_id)


@mcp.tool()
async def get_my_orders() -> dict[str, Any]:
    """List orders belonging to the current authenticated user.

    Use this for customer order history, account dashboards, or support flows where
    the current request identity should determine which user's orders are returned.
    Authentication context is expected to be handled by the MCP server/service
    client environment.
    """
    return await ORDER_API.my_orders()


@mcp.tool()
async def check_purchased(
    user_id: Annotated[
        str,
        Field(description="User ID to check purchase history for."),
    ],
    product_id: Annotated[
        str,
        Field(description="Product ID to verify whether the user purchased."),
    ],
) -> dict[str, Any]:
    """Check whether a user has purchased a specific product.

    Use this for review eligibility, warranty/support validation, recommendation
    logic, or any workflow that needs a boolean/summary answer about whether a
    user bought a product.
    """
    return await ORDER_API.check_purchased(user_id, product_id)


@mcp.tool()
async def get_order_stats() -> dict[str, Any]:
    """Retrieve administrative order statistics.

    Use this for back-office reporting and dashboards. The response is delegated
    from order-service and may include totals, order counts by status, revenue,
    cancellation metrics, conversion trends, or other admin aggregate fields.
    """
    return await ORDER_API.stats()


@mcp.tool()
async def cancel_order(
    order_id: Annotated[
        str,
        Field(description="Order ID to cancel."),
    ],
) -> dict[str, Any]:
    """Cancel an order by order ID.

    This tool has side effects. Use it only when the caller explicitly wants to
    cancel an order. Depending on order-service state rules, cancellation may also
    release reserved inventory or affect payment/shipping workflows.
    """
    return await ORDER_API.cancel_order(order_id)


@mcp.tool()
async def continue_order_payment(
    order_id: Annotated[
        str,
        Field(description="Pending order ID whose payment flow should be resumed."),
    ],
) -> dict[str, Any]:
    """Resume or recreate the payment flow for a pending order.

    Use this when checkout was interrupted, a VNPay redirect was lost, or the user
    needs a fresh payment continuation response for an order that is still payable.
    """
    return await ORDER_API.continue_payment(order_id)


@mcp.tool()
async def get_order_trace(
    correlation_id: Annotated[
        str,
        Field(
            description=(
                "X-Correlation-Id for the request or business flow to trace through "
                "order-service's admin audit endpoint."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Retrieve a cross-service audit trace by correlation ID from order-service.

    Use this for incident investigation when a frontend error response, gateway
    log, or service log includes an X-Correlation-Id and the caller wants to follow
    the related order/payment/inventory events.
    """
    return await ORDER_API.trace_by_correlation(correlation_id)
