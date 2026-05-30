from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.promotion import PROMOTION_API


@mcp.tool()
async def list_promotions() -> dict[str, Any]:
    return await PROMOTION_API.list_promotions()


@mcp.tool()
async def get_promotion(promotion_id: str) -> dict[str, Any]:
    return await PROMOTION_API.get_promotion(promotion_id)


@mcp.tool()
async def list_flash_sales() -> dict[str, Any]:
    return await PROMOTION_API.list_flash_sales()


@mcp.tool()
async def get_flash_sale(flash_sale_id: str) -> dict[str, Any]:
    return await PROMOTION_API.get_flash_sale(flash_sale_id)