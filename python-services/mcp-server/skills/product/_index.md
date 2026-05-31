# Skill: Product Index

## Purpose
Use this index for catalog, category, tag, related-product, and review tasks.

## Activation Conditions
Use when: request mentions product, catalog, category, slug, tag, review, rating,
or related products.
Do not use for: stock quantities, order status, or payment status.

## Steps
### Step 1 - Pick a product skill
Tool: `get_skill("product", skill_name)`
Choose `search_skill` for read-heavy catalog lookup.

## Rollback
Product mutations need HITL and should be reviewed before execution.

## Output Contract
Return JSON following `shared/output_contract.md`.
