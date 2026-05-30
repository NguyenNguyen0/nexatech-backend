# mcp-server

FastAPI + FastMCP skeleton for NexaTech. This iteration focuses on the runtime shell, folder layout, and bootstrap wiring before any business tools are added.

## Default Port

- `8094`

## What Exists Now

- `GET /health`
- `GET /ready`
- `GET /` with a skeleton layout summary
- `FastMCP` mounted at `/mcp`
- correlation ID middleware and basic error envelopes
- optional Eureka registration at startup

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

## Dependencies

The runtime uses the existing Python stack already declared in `pyproject.toml`:

- `fastapi`
- `mcp[cli]` for `FastMCP`
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
