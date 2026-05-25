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

## Docker (Phase 9)

Build image:

```bash
docker build -t nexatech/recommendation-service:local .
```

Run container (standalone):

```bash
docker run --rm -p 8095:8095 \
	-e EUREKA_ENABLED=false \
	-e MONGO_URI=mongodb://nexatech:nexatech123@host.docker.internal:27017/recommendation?authSource=admin \
	-e REDIS_URL=redis://host.docker.internal:6379/0 \
	-e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
	-e MODELS_DIR=/models \
	-v rec-models:/models \
	nexatech/recommendation-service:local
```

Compose integration example is available at `docker-compose.recommendation.yml`.

## Tests (Phase 10)

Run all tests:

```bash
uv run pytest
```

Run only unit tests:

```bash
uv run pytest tests/unit
```

Run only integration-style API tests:

```bash
uv run pytest tests/integration
```

## Operational checks

- Health: `GET /health`
- Readiness: `GET /ready`
- User recommendations: `GET /recommend/user/{userId}`
- Similar items: `GET /recommend/similar/{productId}`
- Popular fallback: `GET /recommend/popular`

Recommended runtime metrics to monitor:

- API latency p95 for recommendation endpoints
- Redis cache hit ratio (`rec:user:*`, `rec:similar:*`, `rec:popular:*`)
- Kafka consumer lag for `rec-behavior-collector` and `rec-product-catalog-sync`
- Training duration and training failure count

## Environment variables

- SERVICE_NAME=recommendation-service
- PORT=8095
- EUREKA_ENABLED=true
- EUREKA_SERVER_URL=http://localhost:8761/eureka/
- EUREKA_INSTANCE_HOSTNAME=127.0.0.1
