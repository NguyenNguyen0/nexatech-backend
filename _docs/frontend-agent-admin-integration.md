# Frontend Integration Guide - Admin Agent

This guide describes how to integrate the admin frontend with `agent-service` in local development through `api-gateway`, including user header propagation and conversation session history.

## 1) Local Architecture (recommended)

Frontend should call only `api-gateway` (`http://localhost:8080`) and not call Python services directly.

Flow:

1. Frontend -> `api-gateway` (`/api/v1/agent/...`)
2. Gateway validates JWT and injects headers when available:
   - `X-User-Id`
   - `X-User-Role`
   - `X-User-Email`
   - `X-Correlation-Id`
3. Gateway forwards to `agent-service` through Eureka (`lb://agent-service`)
4. `agent-service` calls `mcp-server` and forwards user/correlation headers
5. `mcp-server` forwards the same context to downstream business services

## 2) Required Local Services

Start infrastructure and core services first:

1. discovery-server (`:8761`)
2. config-server (`:8888`)
3. api-gateway (`:8080`)
4. mcp-server (`:8094`)
5. agent-service (`:8097`)

Suggested commands from repository root:

```bash
make infra-up
make run-discovery
make run-config
make run-gateway
make run-mcp
make run-agent
```

## 3) Agent API Contracts for Frontend

Use gateway base URL:

- Base URL: `http://localhost:8080`
- Agent prefix: `/api/v1/agent`

### Start a run

`POST /api/v1/agent/runs`

Request body:

```json
{
  "message": "Cho toi xem khuyen mai hien tai",
  "threadId": "optional-thread-id",
  "caller": {
    "roles": ["operator"]
  }
}
```

Notes:

- If gateway already sends `X-User-Role`, agent merges header roles into `caller.roles`.
- If `X-User-Id` or `X-User-Email` exists, agent binds them into caller context automatically.

### Resume a blocked run (HITL)

`POST /api/v1/agent/runs/resume`

```json
{
  "threadId": "thread-123",
  "decision": "approved"
}
```

### Get run state

`GET /api/v1/agent/runs/{threadId}`

### Streaming run (AG-UI/CopilotKit)

`POST /api/v1/agent/runs/stream`

- Response: `text/event-stream` (SSE)
- Stable events:
  - `run.started`
  - `message.chunk`
  - `tool.started`
  - `tool.completed`
  - `tool.error`
  - `hitl.interrupt`
  - `run.completed`
  - `run.cancelled`
  - `run.error`

Sample request:

```json
{
  "runId": "optional-run-id",
  "threadId": "optional-thread-id",
  "message": "Cho toi xem khuyen mai hom nay"
}
```

Sample SSE frame:

```text
event: message.chunk
data: {"eventType":"message.chunk","runId":"...","threadId":"...","timestamp":"...","data":{"content":"..."}}
```

### Cancel an active stream

`POST /api/v1/agent/runs/{runId}/cancel`

- Returns cancellation requested state.
- Active SSE stream emits `run.cancelled` when cancellation takes effect.

### Get current interrupts for a thread

`POST /api/v1/agent/runs/interrupts`

```json
{
  "threadId": "thread-123"
}
```

### Resume interrupt decision

`POST /api/v1/agent/runs/resume`

```json
{
  "threadId": "thread-123",
  "decision": "approved"
}
```

### Get conversation thread detail

`GET /api/v1/agent/conversations/{threadId}?limit=100`

### List conversation threads by user

`GET /api/v1/agent/conversations?userId={userId}&limit=20`

Behavior:

- Backend filters by `X-User-Id` header first.
- If `X-User-Id` is missing, frontend can pass `userId` query for local fallback.
- Returns metadata list (no full event payload) sorted by latest update.

### Session CRUD

- `POST /api/v1/agent/sessions`
- `PATCH /api/v1/agent/sessions/{threadId}`
- `DELETE /api/v1/agent/sessions/{threadId}`

Session endpoints require `X-User-Id` and only allow session owners/participants to update/delete.

## 4) Conversation Session Data Model (Mongo)

Agent persists long-term session documents with user metadata:

- `lastUserId`, `lastUserRole`, `lastUserEmail`
- `userIds[]`, `userRoles[]`
- `events[]` where each event includes:
  - `eventType`
  - `userId`, `userRole`, `userEmail`
  - `correlationId`
  - `payload`

This enables querying conversation history per user in admin UI.

## 5) Frontend HTTP Client Example (Axios)

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  // Optional fallback when testing without full auth pipeline.
  const userId = localStorage.getItem('debug_user_id');
  const userRole = localStorage.getItem('debug_user_role');
  if (userId && !config.headers['X-User-Id']) {
    config.headers['X-User-Id'] = userId;
  }
  if (userRole && !config.headers['X-User-Role']) {
    config.headers['X-User-Role'] = userRole;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const tag = error?.response?.data?.tag ?? 'UNKNOWN';
    const correlationId =
      error?.response?.data?.correlationId ??
      error?.response?.headers?.['x-correlation-id'] ??
      'none';

    console.error(
      `[${tag}] HTTP ${error?.response?.status} ${error?.config?.url} correlationId=${correlationId}`,
      error,
    );

    return Promise.reject(error);
  },
);

export default api;
```

## 6) Admin Page Integration Pattern

Recommended screens:

1. Agent chat panel:
  - send message -> `POST /api/v1/agent/runs/stream`
  - render incremental output from `message.chunk`
  - render tool cards from `tool.started|completed|error`
  - if interrupted -> show approval UI and call `runs/resume`
  - cancel button -> `POST /api/v1/agent/runs/{runId}/cancel`
2. Session history list by current admin user:
  - `GET /api/v1/agent/conversations?limit=20` with `X-User-Id`
3. Session detail drawer:
   - `GET /api/v1/agent/conversations/{threadId}`
4. Session management actions:
  - create empty thread: `POST /api/v1/agent/sessions`
  - rename/edit metadata: `PATCH /api/v1/agent/sessions/{threadId}`
  - delete thread: `DELETE /api/v1/agent/sessions/{threadId}`

## 7) Local Readiness Checklist

Before frontend QA:

1. `GET http://localhost:8080/api/v1/agent/health` -> 200
2. `GET http://localhost:8080/api/v1/mcp/health` -> 200
3. `GET http://localhost:8097/ready` -> `checks.eureka` is `ok` or `disabled`
4. `GET http://localhost:8094/ready` -> `checks.eureka` is `ok` or `disabled`
5. Agent run from frontend returns `threadId`
6. Conversation list endpoint returns threads for current user

## 8) Important Notes

- Through gateway, `agent-service` accepts routes under `/api/v1/agent/...`.
- Service also supports stripped routes (`/runs`, `/conversations`, ...) for compatibility with current gateway StripPrefix behavior.
- If Mongo is unavailable, conversation endpoints return `503` but run/resume still work.
- If checkpointer is disabled, resume/state endpoints requiring checkpoint return `503`.
- For stream UX, frontend should implement reconnect/retry and preserve unsent draft in local storage on page reload.
