from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.inventory import INVENTORY_API


@mcp.tool()
async def list_inventory(params: dict[str, Any] | None = None) -> dict[str, Any]:
    """List all inventory items with optional filtering. Use params to filter by productId, sku, warehouseLocation, etc."""
    return await INVENTORY_API.list_inventory(params=params)


@mcp.tool()
async def get_inventory(product_id: str) -> dict[str, Any]:
    """Retrieve inventory details for a specific product by its ID, including current stock, SKU, and warehouse location."""
    return await INVENTORY_API.get_inventory(product_id)


@mcp.tool()
async def get_inventory_movements(
    product_id: str,
    params: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Retrieve stock movement history for a product, including imports, exports, reservations, and releases."""
    return await INVENTORY_API.get_inventory_movements(product_id, params=params)


@mcp.tool()
async def update_stock(
    product_id: str,
    quantity: int,
    sku: str | None = None,
    reason: str | None = None,
) -> dict[str, Any]:
    """Update the stock quantity of a product. Optionally provide sku and reason for the adjustment."""
    payload: dict[str, Any] = {"productId": product_id, "quantity": quantity}
    if sku:
        payload["sku"] = sku
    if reason:
        payload["reason"] = reason
    return await INVENTORY_API.update_stock(payload)


@mcp.tool()
async def check_inventory(items: list[dict[str, Any]]) -> dict[str, Any]:
    """Check stock availability for a list of items. Each item should have productId and quantity."""
    return await INVENTORY_API.check_inventory({"items": items})


@mcp.tool()
async def get_low_stock_items() -> dict[str, Any]:
    """List all products that are below their reorder level threshold."""
    return await INVENTORY_API.low_stock_items()


@mcp.tool()
async def reserve_stock(items: list[dict[str, Any]]) -> dict[str, Any]:
    """Reserve stock for a list of items, typically during order placement. Each item needs productId and quantity."""
    return await INVENTORY_API.reserve_stock({"items": items})


@mcp.tool()
async def release_stock(items: list[dict[str, Any]]) -> dict[str, Any]:
    """Release previously reserved stock for a list of items, typically after order cancellation."""
    return await INVENTORY_API.release_stock({"items": items})


@mcp.tool()
async def bulk_import_inventory(requests: list[dict[str, Any]]) -> dict[str, Any]:
    """Bulk import inventory records. Each request should contain productId, quantity, sku, and warehouse info."""
    return await INVENTORY_API.bulk_import(requests)


@mcp.tool()
async def update_warehouse_info(
    product_id: str,
    warehouse_location: str,
    reorder_level: int,
) -> dict[str, Any]:
    """Update warehouse location and reorder level for a product."""
    return await INVENTORY_API.update_warehouse_info(
        product_id,
        {
            "warehouseLocation": warehouse_location,
            "reorderLevel": reorder_level,
        },
    )
