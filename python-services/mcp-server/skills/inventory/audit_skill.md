# Skill: Inventory Audit

## Purpose
Investigate inventory changes and low-stock operational state.

## Activation Conditions
Use when: user asks why stock changed, movement history, low-stock list, or
warehouse audit.
Do not use for: mutating stock.

## Steps
### Step 1 - Read current inventory
Tool: `get_inventory(product_id)` or `list_inventory(params)`
If no product ID is known: list/filter first.

### Step 2 - Read movements
Tool: `get_inventory_movements(product_id, params)`
If unavailable: return current inventory and structured error.

### Step 3 - Check low stock if relevant
Tool: `get_low_stock_items()`
If the user asked about replenishment, include low-stock results.

## Rollback
No rollback. This skill is read-only.

## Output Contract
Return JSON following `shared/output_contract.md`.
