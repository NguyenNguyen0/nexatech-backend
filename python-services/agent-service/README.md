# agent-service

FastAPI skeleton service for NexaTech agent orchestration. This setup introduces modular folders for LangGraph, HITL logic, MCP client access, and optional CopilotKit runtime objects.

## Default Port

- `8097`

## Runtime Endpoints

- `GET /health`
- `GET /ready`
- `GET /` (returns skeleton layout summary)
- `POST /api/v1/agent/runs`
- `POST /api/v1/agent/runs/stream` (SSE)
- `POST /api/v1/agent/runs/{runId}/cancel`
- `POST /api/v1/agent/runs/interrupts`
- `POST /api/v1/agent/runs/resume`
- `GET /api/v1/agent/runs/{threadId}`
- `GET /api/v1/agent/conversations?userId=...`
- `GET /api/v1/agent/conversations/{threadId}`
- `GET /api/v1/agent/metrics/delegation`
- `POST /api/v1/agent/sessions`
- `PATCH /api/v1/agent/sessions/{threadId}`
- `DELETE /api/v1/agent/sessions/{threadId}`

## Run

```bash
uv run main.py
```

From the repository root:

```bash
make run-agent
```

## LangGraph Postgres Checkpointer

Set `AGENT_DB_URL` to enable durable LangGraph state for interrupts, resume, and run-state lookup:

```bash
export AGENT_DB_URL=postgresql://nexatech:nexatech123@localhost:5432/nexatech_agent
```

The PostgreSQL database and user must already exist. On startup, `AsyncPostgresSaver.setup()` bootstraps the LangGraph checkpoint tables inside that database.

> **Note:** The checkpointer uses `AsyncPostgresSaver` (from `langgraph.checkpoint.postgres.aio`)
> instead of the sync `PostgresSaver`, because the FastAPI runtime calls `ainvoke` / `aget_tuple`.

If `AGENT_DB_URL` is missing or Postgres is unavailable, the service still starts with a stateless graph. `/ready` reports the checkpointer as `disabled` or `error: ...`; low-sensitivity tools can still run, while HITL/resume routes require `checkpointer: ok`.

### Database provisioning

**Option A — Use the infra compose** (database already exists in `_postgres/init-databases.sql`):

```bash
# From repo root
docker compose -f docker-compose.infra.yml up -d
# nexatech_agent database is on port 5432
```

**Option B — Standalone Postgres for agent-service only** (port 5433):

```bash
cd python-services/agent-service
docker compose -f docker-compose.postgres.yml up -d
export AGENT_DB_URL=postgresql://nexatech:nexatech123@localhost:5433/nexatech_agent
```

### Running integration tests

```bash
AGENT_DB_URL=postgresql://nexatech:nexatech123@localhost:5432/nexatech_agent uv run pytest tests/test_graph_checkpoint_integration.py -v
```

## Folder Skeleton

```text
app/
  core/
  graph/
  hitl/
  runtime/
  mcp_client.py
  config.py
tests/
```

## Installed Dependencies

- `fastapi`
- `uvicorn`
- `py-eureka-client`
- `httpx`
- `pydantic-settings`
- `langgraph`
- `langgraph-checkpoint-postgres`
- `langsmith`
- `langchain-groq`
- `copilotkit`
- `pytest`
- `pytest-asyncio`

## Environment Variables

See `.env.example` for a complete local template.

Key variables:

- `SERVICE_NAME`
- `PORT`
- `EUREKA_ENABLED`
- `EUREKA_SERVER_URL`
- `EUREKA_INSTANCE_HOSTNAME`
- `MCP_SERVER_URL`
- `MCP_META_URL`
- `MCP_ENABLE_DOMAIN_ROUTING`
- `MCP_DEFAULT_DOMAIN`
- `MCP_INVENTORY_URL`
- `MCP_PRODUCT_URL`
- `MCP_ORDER_URL`
- `MCP_PAYMENT_URL`
- `MCP_PROMOTION_URL`
- `MCP_NEWS_URL`
- `MCP_AUDIT_URL`
- `MCP_TOOL_CATALOG_CACHE_TTL_SECONDS`
- `REQUEST_TIMEOUT_SECONDS`
- `LANGSMITH_TRACING`
- `LANGSMITH_ENDPOINT`
- `LANGSMITH_PROJECT`
- `LANGSMITH_API_KEY`
- `GROQ_API_KEY`
- `GROQ_MODEL`
- `AGUI_AGENT_NAME`
- `AGUI_AGENT_DESCRIPTION`
- `AGENT_DB_URL`
- `CONVERSATION_STORE_ENABLED`
- `MONGO_URI`
- `MONGO_DATABASE`
- `MONGO_CONVERSATION_COLLECTION`
- `CONVERSATION_HISTORY_MAX_EVENTS`

## Notes

- CopilotKit objects are initialized in runtime state (`app.state`) for compatibility with the current package API.
- Conversation sessions are persisted in MongoDB when `CONVERSATION_STORE_ENABLED=true` and `MONGO_URI` is reachable.
- User context headers (`X-User-Id`, `X-User-Role`, `X-User-Email`) are consumed when available and propagated to MCP calls.
- Service supports both prefixed and stripped routes (`/api/v1/agent/...` and `/runs`, `/conversations`, ...) for gateway compatibility.
- Stream event schema includes: `message.chunk`, `tool.started`, `tool.completed`, `tool.error`, `hitl.interrupt`, `run.completed`, `run.cancelled`, `run.error`.
- `MCP_SERVER_URL` must point to MCP server base URL (example: `http://localhost:8094`), because agent internally calls `${MCP_SERVER_URL}/mcp`.
- Runtime now supports domain-aware MCP routing:
  - Agent asks `route_to_mcp_domain` on `MCP_META_URL`.
  - Agent then fetches tool catalog from the routed domain MCP URL (`MCP_*_URL`) instead of loading a global catalog.
  - If domain MCP is unavailable, agent falls back to `MCP_SERVER_URL`.
  - Tool catalog discovery is cached for `MCP_TOOL_CATALOG_CACHE_TTL_SECONDS` seconds.
- LLM fallback planner now uses the already-filtered `available_tools` from runtime state and does not re-fetch the full MCP tool list unless `available_tools` is missing.

## Phase D Delegation Metrics

Agent-service now tracks in-memory delegation metrics for domain routing and
subagent planning quality.

- Endpoint: `GET /api/v1/agent/metrics/delegation` (also available at `/metrics/delegation`)
- Metric groups include:
  - runtime routing (`runtime_domain_route_total`, `runtime_default_domain_total`)
  - catalog fallback (`runtime_domain_catalog_empty_total`, `runtime_catalog_fallback_total`)
  - subagent planning (`delegation_requests_total`, `llm_fallback_total`, `no_matching_tool_total`)
  - router decisions (`route_domain_total`, `route_meta_intent_total`, `route_general_total`)
