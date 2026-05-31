# Skill: News Index

## Purpose
Use this index for public/admin news article lookup and news images.

## Activation Conditions
Use when: request mentions news, article, slug, draft, image, thumbnail, or
published content.
Do not use for: product catalog pages.

## Steps
### Step 1 - Pick public versus admin listing
Tool: `list_news(page, size)` for public articles or `list_all_news(page, size)`
for admin/all articles.

### Step 2 - Resolve detail
Tool: `get_public_news_by_slug(slug)` or `get_news_by_slug(slug)`
If images are requested: call `get_news_images(news_id)`.

## Rollback
No rollback for current read-only news tools.

## Output Contract
Return JSON following `shared/output_contract.md`.
