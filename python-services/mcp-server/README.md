# mcp-server

FastAPI service for MCP integration APIs.

## Default port

- 8094

## Health endpoint

- GET /health

## Run

```bash
uv run main.py
```

## Environment variables

- SERVICE_NAME=mcp-server
- PORT=8094
- EUREKA_ENABLED=true
- EUREKA_SERVER_URL=http://localhost:8761/eureka/
- EUREKA_INSTANCE_HOSTNAME=127.0.0.1

## Service Graph

```mermaid
flowchart LR
	A[mcp-server]
	B[discovery-server]
	C[api-gateway]

	A --> B
	C --> A
```

## Dependencies

- Exposed through: [api-gateway](../../api-gateway/README.md)
- Service discovery: [discovery-server](../../discovery-server/README.md)
