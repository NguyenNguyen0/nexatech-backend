# mcp-server

FastAPI + FastMCP service for NexaTech with a resilient shared client layer, per-service internal APIs, and tool groups for read-only operations plus dependency health checks.

## Default Port

- `8094`

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
- health tools for each dependency and an aggregate dependency health tool

## Run

```bash
uv run main.py
```

From the repository root:

```bash
make run-mcp
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
- Inventory: `list_inventory`, `get_inventory`, `get_inventory_movements`, `check_inventory`, `get_low_stock_items`
- Order: `get_order`, `get_my_orders`, `check_purchased`, `get_order_stats`, `get_order_trace`
- Payment: `get_payment`, `get_payment_by_order`, `get_my_payments`, `get_payment_stats`
- Audit: `search_audit_logs`, `get_audit_entry`, `get_audit_trace`
- Promotion: `list_promotions`, `get_promotion`, `list_flash_sales`, `get_flash_sale`
- Health: `check_dependency_health`, `check_all_dependency_health`, and per-service health tools

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
