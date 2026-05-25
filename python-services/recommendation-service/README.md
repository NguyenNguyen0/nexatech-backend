# recommendation-service

FastAPI service for recommendation APIs.

## Default port

- 8095

## Health endpoint

- GET /health

## Run

```bash
uv run main.py
```

## Environment variables

- SERVICE_NAME=recommendation-service
- PORT=8095
- EUREKA_ENABLED=true
- EUREKA_SERVER_URL=http://localhost:8761/eureka/
- EUREKA_INSTANCE_HOSTNAME=127.0.0.1
