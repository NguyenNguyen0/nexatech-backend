# Skill: Inventory Restock

## Purpose
Import or update stock records while keeping warehouse metadata consistent.

## Activation Conditions
Use when: restock, bulk import, warehouse location update, reorder threshold, or
manual stock correction is requested.
Do not use for: order reservation.

## Steps
### Step 1 - Validate target records
Tool: `get_inventory(product_id)` or `list_inventory(params)`
If product IDs are missing: ask for them before writing.

### Step 2 - Apply stock update
Tool: `update_stock(product_id, quantity, reason, idempotency_key)` or
`bulk_import_inventory(requests, idempotency_key)`
If failure: stop and return structured error.

### Step 3 - Update warehouse metadata
Tool: `update_warehouse_info(product_id, warehouse_location, reorder_level)`
If failure after stock update: report partial success and request human review.

## Rollback
Do not guess previous quantity. If rollback is needed, surface previous known
values and ask for approval before another `update_stock`.

## Output Contract
Return JSON following `shared/output_contract.md`.
