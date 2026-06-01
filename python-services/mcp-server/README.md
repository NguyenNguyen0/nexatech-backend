# mcp-server

FastAPI + FastMCP service for NexaTech with a resilient shared client layer, per-service internal APIs, and tool groups for read-only operations plus dependency health checks.

## Default Port

- `8094`

## MCP Profiles

The same codebase can run either the legacy all-tools MCP server or one
independent domain MCP per process. Select the catalog with
`MCP_SERVER_PROFILE`.

| Profile | Service name | Port | Tool modules |
|---|---|---:|---|
| `all` | `mcp-server` | `8094` | all existing domain tools + health tools |
| `meta` | `meta-mcp` | `8094` | MCP domain management/routing tools only |
| `inventory` | `inventory-mcp` | `8101` | inventory tools |
| `product` | `product-mcp` | `8102` | product + review tools |
| `order` | `order-mcp` | `8103` | order tools |
| `payment` | `payment-mcp` | `8104` | payment tools |
| `promotion` | `promotion-mcp` | `8105` | promotion + flash-sale tools |
| `news` | `news-mcp` | `8106` | news tools |
| `audit` | `audit-mcp` | `8107` | audit tools |

The `meta` MCP does not execute business tools. It exposes management tools
such as `list_mcp_domains`, `get_mcp_domain`, `route_to_mcp_domain`,
`check_mcp_domain_health`, `list_skills`, and `get_skill`.

## What Exists Now

- `GET /health`
- `GET /ready`
- `GET /` with a skeleton layout summary
- `FastMCP` mounted at `/mcp`
- correlation ID middleware and basic error envelopes
- user context propagation (`X-User-Id`, `X-User-Role`, `X-User-Email`) for downstream calls
- optional Eureka registration at startup
- retry-aware downstream HTTP client with structured error payloads
- per-service internal APIs under `app/services/`
- health tools for each dependency and an aggregate dependency health tool in
  the legacy `all` profile
- independent domain MCP profiles plus a `meta` MCP profile for discovery,
  routing metadata, and managed-domain health checks

## Run

```bash
uv run main.py
```

From the repository root:

```bash
make run-mcp
```

Run the independent MCPs from the repository root:

```bash
make run-mcp-meta
make run-mcp-inventory
make run-mcp-product
make run-mcp-order
make run-mcp-payment
make run-mcp-promotion
make run-mcp-news
make run-mcp-audit
```

Or run any profile manually:

```bash
SERVICE_NAME=inventory-mcp MCP_SERVER_PROFILE=inventory PORT=8101 uv run main.py
```

## Folder Skeleton

```text
app/
    core/
    mcp/
    clients/
    tools/
    services/
    schemas/
    utils/
tests/
```

## Tool Groups

- News: `list_news`, `get_news_by_slug`, `get_public_news_by_slug`, `get_news_images`
- Product: `list_products`, `search_products`, `get_product_by_id`, `get_product_by_slug`, `list_featured_products`, `list_products_by_tags`, `list_products_by_category`, `list_products_by_category_slug`, `get_related_products`, `get_related_products_by_slug`, `create_product`, `update_product`, `delete_product`, `restore_product`
- Review: `list_reviews_by_product`, `list_reviews_by_product_slug`, `get_my_review_for_product`, `create_review`, `list_reviews`, `update_review`, `delete_review`, `mark_review_helpful`
- Inventory: `list_inventory`, `get_inventory`, `get_inventory_movements`, `check_inventory`, `get_low_stock_items`
- Order: `get_order`, `get_my_orders`, `check_purchased`, `get_order_stats`, `get_order_trace`
- Payment: `get_payment`, `get_payment_by_order`, `get_my_payments`, `get_payment_stats`
- Audit: `search_audit_logs`, `get_audit_entry`, `get_audit_trace`
- Promotion: `list_promotions`, `get_promotion`, `list_flash_sales`, `get_flash_sale`
- Health: `check_dependency_health`, `check_all_dependency_health`, and per-service health tools
- Meta: `list_mcp_domains`, `get_mcp_domain`, `route_to_mcp_domain`, `check_mcp_domain_health`, `list_skills`, `get_skill`

All downstream calls go through a shared retrying HTTP client and return structured errors instead of raising to the top-level MCP server.

## Dependencies

The runtime uses the existing Python stack already declared in `pyproject.toml`:

- `fastapi`
- `fastmcp`
- `httpx`
- `pydantic-settings`
- `uvicorn`
- `py-eureka-client`

## MCP Inspector

```bash
npx -y @modelcontextprotocol/inspector
```

Connect to:

```text
http://localhost:8094/mcp
```
