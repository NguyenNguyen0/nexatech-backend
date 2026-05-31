# Skill: Inventory Fulfillment

## Purpose
Check availability and reserve stock safely for order placement workflows.

## Activation Conditions
Use when: checkout, order creation, stock hold, reserve inventory, or cart
availability is requested.
Do not use for: browsing inventory or restocking.

## Steps
### Step 1 - Check availability
Tool: `check_inventory(items)`
If failure or unavailable: stop before any reservation.

### Step 2 - Reserve stock
Tool: `reserve_stock(items, idempotency_key)`
If failure: stop and surface `tag`, `message`, and `correlationId`.
Side effect: stock is held and may need release.

## Rollback
If reservation succeeded but later workflow fails, call `release_stock(items)`.

## Output Contract
```json
{
  "status": "success|failed|needs_approval",
  "domain": "inventory",
  "reservedItems": [],
  "rollbackActions": ["release_stock"]
}
```
