# Skill: Product Search

## Purpose
Find products by keyword, slug, category, tag, or relationship.

## Activation Conditions
Use when: user asks to find products, list category products, inspect product
detail, show featured products, or browse reviews.
Do not use for: inventory availability; use inventory tools for stock.

## Steps
### Step 1 - Prefer exact identifiers
Tool: `get_product_by_id(product_id)` or `get_product_by_slug(slug)`
If an ID or slug is present, use exact lookup before broad search.

### Step 2 - Search or list
Tool: `search_products(keyword, params)` or `list_products(params)`
If category slug is present: use `list_products_by_category_slug`.

### Step 3 - Add related data only if requested
Tool: `get_related_products`, `list_reviews_by_product`, or slug variants
If not requested: do not call extra tools.

## Rollback
No rollback for read-only search.

## Output Contract
Return JSON following `shared/output_contract.md`.
