# Skill: Promotion Index

## Purpose
Use this index for promotion and flash-sale lookup.

## Activation Conditions
Use when: request mentions promotion, campaign, voucher, discount, flash sale, or
deal window.
Do not use for: product search unless the task asks about sale products.

## Steps
### Step 1 - List campaigns
Tool: `list_promotions(status)` or `list_flash_sales()`
If user gives a promotion code or ID: use exact get tool.

### Step 2 - Inspect one campaign
Tool: `get_promotion(promotion_id)` or `get_flash_sale(flash_sale_id)`
If not found: return structured not-found error.

## Rollback
No rollback for current read-only promotion tools.

## Output Contract
Return JSON following `shared/output_contract.md`.
