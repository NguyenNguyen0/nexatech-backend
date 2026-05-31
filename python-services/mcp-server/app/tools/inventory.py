from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.schemas.tool_contract import tool_result, validation_error
from app.schemas.tool_inputs import InventoryListParams, PagingParams, dump_input
from app.services.inventory import INVENTORY_API
from pydantic import BaseModel, Field


class InventoryItemRequest(BaseModel):
    product_id: str = Field(alias="productId", description="Product ID.")
    quantity: int = Field(description="Requested quantity.", ge=0)
    sku: str | None = Field(default=None, description="Optional SKU.")


class InventoryImportRequest(BaseModel):
    product_id: str = Field(alias="productId", description="Product ID.")
    quantity: int = Field(description="Stock quantity.", ge=0)
    sku: str | None = Field(default=None, description="Optional SKU.")
    warehouse_location: str | None = Field(
        default=None,
        alias="warehouseLocation",
        description="Optional warehouse/bin location.",
    )
    reorder_level: int | None = Field(
        default=None,
        alias="reorderLevel",
        description="Optional low-stock threshold.",
        ge=0,
    )


@mcp.tool()
async def list_inventory(
    params: Annotated[
        InventoryListParams | None,
        Field(
            description=(
                "Optional inventory list filters. Supported keys: sku, status, "
                "keyword, warehouseLocation, page, size, sort. Unknown keys and "
                "empty values are ignored."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """List inventory records with optional search, filters, sorting, and paging.

    Use this for inventory browsing, SKU lookup, warehouse filtering, or finding
    records by keyword/status. The response is returned directly from
    inventory-service after unsupported query parameters are removed.
    """
    return tool_result(await INVENTORY_API.list_inventory(params=dump_input(params)))


@mcp.tool()
async def get_inventory(
    product_id: Annotated[
        str,
        Field(description="Product ID whose inventory record should be retrieved."),
    ],
) -> dict[str, Any]:
    """Retrieve inventory details for a specific product ID.

    Use this when the caller needs current stock, reserved quantity, SKU,
    warehouse location, reorder level, status, or related inventory metadata for
    one product.
    """
    return tool_result(await INVENTORY_API.get_inventory(product_id))


@mcp.tool()
async def get_inventory_movements(
    product_id: Annotated[
        str,
        Field(description="Product ID whose stock movement history should be listed."),
    ],
    params: Annotated[
        PagingParams | None,
        Field(
            description=(
                "Optional movement paging/sorting params. Supported keys: page, "
                "size, sort. Unknown keys and empty values are ignored."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """Retrieve stock movement history for a product.

    Use this to audit inventory changes such as imports, stock adjustments,
    reservations, releases, order deductions, or cancellation returns.
    """
    return tool_result(
        await INVENTORY_API.get_inventory_movements(
            product_id,
            params=dump_input(params),
        )
    )


@mcp.tool()
async def update_stock(
    product_id: Annotated[
        str,
        Field(description="Product ID whose stock quantity should be updated."),
    ],
    quantity: Annotated[
        int,
        Field(description="New stock quantity or service-defined adjustment quantity."),
    ],
    sku: Annotated[
        str | None,
        Field(description="Optional SKU to include in the stock update payload."),
    ] = None,
    reason: Annotated[
        str | None,
        Field(
            description=(
                "Optional human-readable reason for the stock change, useful for "
                "audit trails and movement history."
            ),
        ),
    ] = None,
    idempotency_key: Annotated[
        str | None,
        Field(description="Optional idempotency key for caller-side retry tracking."),
    ] = None,
) -> dict[str, Any]:
    """Update stock for a product.

    Use this for manual stock correction or synchronization. Include a reason when
    possible so downstream movement/audit records explain why the value changed.

    This tool has side effects and requires human approval in the agent layer.
    """
    payload: dict[str, Any] = {"productId": product_id, "quantity": quantity}
    if sku:
        payload["sku"] = sku
    if reason:
        payload["reason"] = reason
    return tool_result(
        await INVENTORY_API.update_stock(payload),
        requires_approval=True,
        idempotency_key=idempotency_key,
    )


@mcp.tool()
async def check_inventory(
    items: Annotated[
        list[InventoryItemRequest],
        Field(
            description=(
                "Items to check. Each item should include productId and quantity; "
                "include sku when the caller has SKU-specific stock requirements."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Check stock availability for one or more cart/order items.

    Use this before checkout or order creation to verify whether requested
    quantities can be fulfilled. This tool does not reserve stock.
    """
    return tool_result(
        await INVENTORY_API.check_inventory(
            {"items": [item.model_dump(by_alias=True, exclude_none=True) for item in items]}
        )
    )


@mcp.tool()
async def get_low_stock_items() -> dict[str, Any]:
    """List inventory records that are below their reorder level threshold.

    Use this for replenishment, purchasing workflows, or operational monitoring of
    products that need restocking.
    """
    return tool_result(await INVENTORY_API.low_stock_items())


@mcp.tool()
async def reserve_stock(
    items: Annotated[
        list[InventoryItemRequest],
        Field(
            description=(
                "Items to reserve. Each item should include productId and quantity; "
                "include sku when the reservation is SKU-specific."
            ),
        ),
    ],
    idempotency_key: Annotated[
        str | None,
        Field(description="Optional idempotency key for caller-side retry tracking."),
    ] = None,
) -> dict[str, Any]:
    """Reserve stock for one or more items during order placement.

    Use this only when stock should be held for a pending order. For read-only
    availability checks, use check_inventory instead.

    This tool has side effects and requires human approval in the agent layer.
    """
    return tool_result(
        await INVENTORY_API.reserve_stock(
            {"items": [item.model_dump(by_alias=True, exclude_none=True) for item in items]}
        ),
        requires_approval=True,
        idempotency_key=idempotency_key,
        rollback_needed=True,
    )


@mcp.tool()
async def release_stock(
    items: Annotated[
        list[InventoryItemRequest],
        Field(
            description=(
                "Reserved items to release. Each item should include productId and "
                "quantity; include sku when the release is SKU-specific."
            ),
        ),
    ],
    idempotency_key: Annotated[
        str | None,
        Field(description="Optional idempotency key for caller-side retry tracking."),
    ] = None,
) -> dict[str, Any]:
    """Release previously reserved stock for one or more items.

    Use this after order cancellation, payment failure, checkout timeout, or any
    workflow that abandons a previously reserved quantity.

    This tool has side effects and requires human approval in the agent layer.
    """
    return tool_result(
        await INVENTORY_API.release_stock(
            {"items": [item.model_dump(by_alias=True, exclude_none=True) for item in items]}
        ),
        requires_approval=True,
        idempotency_key=idempotency_key,
    )


@mcp.tool()
async def bulk_import_inventory(
    requests: Annotated[
        list[InventoryImportRequest],
        Field(
            description=(
                "Bulk inventory records to import. Each record should contain "
                "productId, quantity, sku, warehouseLocation, and reorderLevel when "
                "available."
            ),
        ),
    ],
    idempotency_key: Annotated[
        str | None,
        Field(description="Optional idempotency key for caller-side retry tracking."),
    ] = None,
) -> dict[str, Any]:
    """Bulk import or upsert inventory records.

    Use this for initial inventory loading, batch warehouse synchronization, or
    operational imports where multiple product stock records are submitted at once.

    This tool has side effects and requires human approval in the agent layer.
    """
    if len(requests) > 100:
        return validation_error("bulk_import_inventory accepts at most 100 records")
    return tool_result(
        await INVENTORY_API.bulk_import(
            [item.model_dump(by_alias=True, exclude_none=True) for item in requests]
        ),
        requires_approval=True,
        idempotency_key=idempotency_key,
    )


@mcp.tool()
async def update_warehouse_info(
    product_id: Annotated[
        str,
        Field(description="Product ID whose warehouse metadata should be updated."),
    ],
    warehouse_location: Annotated[
        str,
        Field(description="Warehouse/bin/location value to store for the product."),
    ],
    reorder_level: Annotated[
        int,
        Field(
            description=(
                "Threshold quantity below which the product should be considered "
                "low stock."
            ),
            ge=0,
        ),
    ],
    idempotency_key: Annotated[
        str | None,
        Field(description="Optional idempotency key for caller-side retry tracking."),
    ] = None,
) -> dict[str, Any]:
    """Update warehouse location and reorder threshold for a product.

    Use this when operations moves stock to a new location or changes the
    replenishment threshold used by low-stock monitoring.

    This tool has side effects and requires human approval in the agent layer.
    """
    return tool_result(
        await INVENTORY_API.update_warehouse_info(
            product_id,
            {
                "warehouseLocation": warehouse_location,
                "reorderLevel": reorder_level,
            },
        ),
        requires_approval=True,
        idempotency_key=idempotency_key,
    )
