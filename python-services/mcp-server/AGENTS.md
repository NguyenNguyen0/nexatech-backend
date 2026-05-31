# AGENTS.md - mcp-server

## Service Snapshot
- `mcp-server` is a Python 3.12 FastAPI + FastMCP service mounted at `/mcp` and served on port `8094`.
- It exposes MCP tools for NexaTech domain services through `app/tools/`, while all downstream HTTP calls are wrapped in `app/services/` and `app/core/http.py`.
- Keep tool modules thin: MCP functions should validate/shape agent-facing inputs, document behavior, and delegate business calls to service API wrappers.
- Shared runtime concerns live in `app/core/`: settings, logging, resilience/retry, HTTP client behavior, correlation ID, and user-context propagation.

---

## Build, Run, And Test
- Use `uv` only. Do not use `pip`, ad-hoc virtualenv commands, or direct dependency edits outside `pyproject.toml`/`uv.lock`.
- Run locally from `python-services/mcp-server`:
  ```bash
  uv run main.py
  ```
- From repo root, prefer:
  ```bash
  make run-mcp
  ```
- Run focused checks before finishing changes:
  ```bash
  uv run ruff check app tests
  uv run pytest
  ```
- If sandboxed `uv` cannot write to the default cache, use:
  ```bash
  UV_CACHE_DIR=/tmp/uv-cache uv run ruff check app tests
  UV_CACHE_DIR=/tmp/uv-cache uv run pytest
  ```

---

## Architecture Rules
- Keep `app/tools/<domain>.py` as MCP tool registration only. Do not put endpoint URL construction, retry loops, auth propagation, or low-level HTTP handling in tool functions.
- Put downstream service methods in `app/services/<domain>.py`; use the shared `ServiceClient` so retries, timeout handling, correlation IDs, user headers, and structured errors remain consistent.
- Add new domain integrations in three steps:
  1. Add or extend a service API wrapper in `app/services/`.
  2. Add narrowly scoped MCP tools in `app/tools/`.
  3. Import the new tool module in `app/tools/__init__.py` for side-effect registration.
- Prefer async all the way down. Use `async def`, `await`, `httpx`, and asyncio-compatible libraries.
- Do not block the event loop with synchronous network, database, file, or sleep calls.

---

## MCP Tool Naming
- Use clear verb + noun names that tell the agent exactly what the tool does:
  ```python
  get_inventory()
  update_stock()
  reserve_stock()
  search_audit_logs()
  ```
- Avoid vague names:
  ```python
  process()
  handle_item()
  do_inventory()
  manage()
  ```
- Keep each tool focused on one action. Do not create "god tools" such as `manage_inventory(action, payload)`.
- Keep names semantically distinct. If two tools could be confused, rename or document the difference clearly, for example read-only `check_inventory` versus mutating `reserve_stock`.
- Use domain-consistent naming. Existing public names may be preserved for compatibility, but new tools should prefer unambiguous names such as `get_payment_by_order`, `get_audit_trace`, or `bulk_import_inventory`.

---

## MCP Tool Docstrings
- Every `@mcp.tool()` function must have an agent-facing docstring.
- Write docstrings to help an agent decide **when to use the tool**, not just what the function calls.
- Include whether the tool is read-only or has side effects.
- Include when not to use the tool if confusion is likely.
- Mention important payload shapes, limits, and downstream behavior.
- Good pattern:
  ```python
  @mcp.tool()
  async def get_low_stock_items() -> dict[str, Any]:
      """List inventory records below their reorder level threshold.

      Use this for replenishment, purchasing workflows, or operational monitoring
      of products that need restocking. Do not use this to list the full inventory;
      use list_inventory for broad inventory browsing.
      """
      return await INVENTORY_API.low_stock_items()
  ```

---

## Tool Arguments And Schemas
- Prefer typed, explicit parameters over generic `dict[str, Any]` payloads when the API shape is stable.
- When a generic dict is necessary for flexible filters, document every supported key in `Field(description=...)` and in the docstring.
- Use `typing.Annotated` with `pydantic.Field` for tool argument descriptions:
  ```python
  async def get_payment(
      payment_id: Annotated[
          str,
          Field(description="Payment ID to retrieve from payment-service."),
      ],
  ) -> dict[str, Any]:
  ```
- Add validation constraints when useful and safe, such as `ge=0`, `le=100`, or max batch sizes.
- For list/bulk tools, document and enforce practical batch limits where possible. If no validation exists yet, docstring must state the expected maximum and recommend batching large requests.
- Do not hide required fields inside undocumented dicts. At minimum, document required keys such as `productId`, `quantity`, `sku`, `warehouseLocation`, `reorderLevel`, `orderId`, or `paymentId`.

---

## Read/Write And Idempotency
- Make side effects obvious in both name and docstring.
- Tools that mutate state include, but are not limited to, stock update/reserve/release, bulk import, warehouse update, payment creation, refund, order cancellation, and payment continuation.
- For mutating tools, say "This tool has side effects" in the docstring.
- Prefer idempotency support for write operations when the downstream service supports it. Expose `idempotency_key` and forward it as `Idempotency-Key`.
- Never describe a write tool as safe to retry unless idempotency is implemented.
- Read-only tools should not reserve, mutate, cancel, refund, or create records.

---

## Error Handling Contract
- Downstream service failures should return structured error payloads through `ServiceClient`; do not let raw `httpx` exceptions leak out of MCP tools.
- MCP tools should return dictionaries/objects that agents can inspect gracefully:
  ```json
  {
    "ok": false,
    "service": "inventory-service",
    "statusCode": 404,
    "tag": "INV-5001",
    "message": "Inventory item not found",
    "error": {}
  }
  ```
- Preserve `tag`, `correlationId`, `statusCode`, and `service` fields when available.
- Do not swallow errors silently or replace structured service errors with vague messages.
- For local validation failures inside a tool, return a structured error instead of raising when the caller can correct the input.

---

## Correlation And User Context
- Preserve `X-Correlation-Id` for every downstream request.
- Preserve user context headers when present: `X-User-Id`, `X-User-Role`, and `X-User-Email`.
- Do not parse JWTs inside MCP tools. Trust the gateway/server middleware and shared client context.
- Logs for warnings/errors must include correlation context when available.

---

## Adding Or Updating Tools
- Read the downstream service API wrapper before editing the MCP tool.
- If adding a new endpoint, add the service wrapper first, then expose a thin tool.
- Keep return values as the downstream payload unless a small local wrapper is needed for pagination or a structured not-found response.
- Do not add broad catch-all tools.
- Do not make a tool perform multiple business operations in one call unless the downstream service endpoint itself represents that operation.
- Update `README.md` tool group listings when adding, removing, or renaming public MCP tools.
- Add focused tests when changing behavior, validation, tool registration, error shaping, or service-client contracts.

---

## Python Style
- Keep imports at the top of the file. `app/tools/__init__.py` intentionally imports tool modules for side-effect registration; keep that pattern explicit.
- Use modern type hints: `str | None`, `dict[str, Any]`, `list[dict[str, Any]]`.
- Avoid unnecessary classes in tool modules.
- Keep comments sparse; prefer clear names and docstrings.
- Do not introduce synchronous SDKs when an async client or wrapper can be used.

---

## MCP Inspector
- To inspect tools manually, start the service and connect the MCP Inspector to:
  ```text
  http://localhost:8094/mcp
  ```
- Inspector command:
  ```bash
  npx -y @modelcontextprotocol/inspector
  ```
