from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.inventory import INVENTORY_API


@mcp.tool()
async def list_inventory(params: dict[str, Any] | None = None) -> dict[str, Any]:
    return await INVENTORY_API.list_inventory(params=params)


@mcp.tool()
async def get_inventory(product_id: str) -> dict[str, Any]:
    return await INVENTORY_API.get_inventory(product_id)


@mcp.tool()
async def get_inventory_movements(
    product_id: str,
    params: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return await INVENTORY_API.get_inventory_movements(product_id, params=params)


@mcp.tool()
async def update_stock(
    product_id: str,
    quantity: int,
    sku: str | None = None,
    reason: str | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {"productId": product_id, "quantity": quantity}
    if sku:
        payload["sku"] = sku
    if reason:
        payload["reason"] = reason
    return await INVENTORY_API.update_stock(payload)


@mcp.tool()
async def check_inventory(items: list[dict[str, Any]]) -> dict[str, Any]:
    return await INVENTORY_API.check_inventory({"items": items})


@mcp.tool()
async def get_low_stock_items() -> dict[str, Any]:
    return await INVENTORY_API.low_stock_items()


@mcp.tool()
async def reserve_stock(items: list[dict[str, Any]]) -> dict[str, Any]:
    return await INVENTORY_API.reserve_stock({"items": items})


@mcp.tool()
async def release_stock(items: list[dict[str, Any]]) -> dict[str, Any]:
    return await INVENTORY_API.release_stock({"items": items})


@mcp.tool()
async def bulk_import_inventory(requests: list[dict[str, Any]]) -> dict[str, Any]:
    return await INVENTORY_API.bulk_import(requests)


@mcp.tool()
async def update_warehouse_info(
    product_id: str,
    warehouse_location: str,
    reorder_level: int,
) -> dict[str, Any]:
    return await INVENTORY_API.update_warehouse_info(
        product_id,
        {
            "warehouseLocation": warehouse_location,
            "reorderLevel": reorder_level,
        },
    )
