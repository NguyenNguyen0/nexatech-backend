from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.inventory import INVENTORY_API


@mcp.tool()
async def list_inventory() -> dict[str, Any]:
    return await INVENTORY_API.list_inventory()


@mcp.tool()
async def get_inventory(product_id: str) -> dict[str, Any]:
    return await INVENTORY_API.get_inventory(product_id)


@mcp.tool()
async def get_inventory_movements(product_id: str) -> dict[str, Any]:
    return await INVENTORY_API.get_inventory_movements(product_id)


@mcp.tool()
async def check_inventory(items: list[dict[str, Any]]) -> dict[str, Any]:
    return await INVENTORY_API.check_inventory({"items": items})


@mcp.tool()
async def get_low_stock_items() -> dict[str, Any]:
    return await INVENTORY_API.low_stock_items()