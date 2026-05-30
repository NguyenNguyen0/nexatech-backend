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
