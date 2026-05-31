from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.schemas.tool_contract import tool_result
from app.services.promotion import PROMOTION_API
from pydantic import Field


@mcp.tool()
async def list_promotions(
    status: Annotated[
        str | None,
        Field(
            description=(
                "Optional promotion status filter accepted by promotion-service, "
                "for example ACTIVE, UPCOMING, EXPIRED, or INACTIVE."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """List admin flash-sale promotions, optionally filtered by lifecycle status.

    Use this tool when an operator needs to inspect all promotion campaigns from the
    promotion-service admin API. The returned payload is the service response as-is,
    usually a list or paged object containing promotion IDs, codes, discount values,
    status, start/end dates, and included products.
    """
    return tool_result(await PROMOTION_API.list_promotions(status=status))


@mcp.tool()
async def get_promotion(
    promotion_id: Annotated[
        str,
        Field(
            description=(
                "Promotion identifier to look up. The resolver matches common keys "
                "from the service response: promotionId, id, or code."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Get one admin promotion by ID or code from the promotion listing.

    Use this when the user knows a promotion ID/code and needs the full campaign
    details. If no matching item is found, the tool returns a structured PRO-4040
    not-found response instead of raising.
    """
    return tool_result(await PROMOTION_API.get_promotion(promotion_id))


@mcp.tool()
async def list_flash_sales() -> dict[str, Any]:
    """List public flash sales currently exposed by promotion-service.

    Use this for shopper-facing flash-sale discovery, product deal browsing, or
    checking which campaigns are visible publicly. It calls the public flash-sales
    endpoint and returns the raw promotion-service payload.
    """
    return tool_result(await PROMOTION_API.list_flash_sales())


@mcp.tool()
async def get_flash_sale(
    flash_sale_id: Annotated[
        str,
        Field(
            description=(
                "Flash sale identifier to look up. The resolver matches id, "
                "promotionId, or code from the flash-sale payload."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Get one flash-sale campaign by ID or code.

    Use this after listing flash sales when a user asks about a specific campaign,
    discount window, or included sale products. If not found, the tool returns a
    structured PRO-4041 response.
    """
    return tool_result(await PROMOTION_API.get_flash_sale(flash_sale_id))
