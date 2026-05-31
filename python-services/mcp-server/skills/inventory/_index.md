# Skill: Inventory Index

## Purpose
Use this index for stock, warehouse, reservation, low-stock, and movement-history
tasks.

## Activation Conditions
Use when: request mentions inventory, stock, warehouse, reserve, release, low
stock, movement history, or restock.
Do not use for: product descriptions, prices, order status, or payment status.

## Steps
### Step 1 - Pick an inventory skill
Tool: `get_skill("inventory", skill_name)`
Choose `fulfillment_skill`, `restock_skill`, or `audit_skill`.

## Rollback
Follow `shared/error_handling.md`.

## Output Contract
Return JSON following `shared/output_contract.md`.
