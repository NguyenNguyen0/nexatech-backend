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
- User recommendations: `GET /recommend/user/{userId}` (default `enrich=true` includes `product` details)
- Similar items: `GET /recommend/similar/{productId}` (default `enrich=true`)
- Popular fallback: `GET /recommend/popular` (default `enrich=true`)

To return raw ranking payload only (without product metadata), pass `enrich=false`.

Recommended runtime metrics to monitor:

- API latency p95 for recommendation endpoints
- Redis cache hit ratio (`rec:user:*`, `rec:similar:*`, `rec:popular:*`)
- Kafka consumer lag for `rec-behavior-collector` and `rec-product-catalog-sync`
- Training duration and training failure count

## Environment variables

- SERVICE_NAME=recommendation-service
- SERVICE_VERSION=0.1.0
- PORT=8095
- LOG_LEVEL=INFO
- REQUEST_ID_HEADER=X-Request-Id
- EUREKA_ENABLED=true
- EUREKA_SERVER_URL=http://localhost:8761/eureka/
- EUREKA_INSTANCE_HOSTNAME=127.0.0.1
- MONGO_URI=mongodb://nexatech:nexatech123@localhost:27017/recommendation?authSource=admin
- MONGO_DATABASE=recommendation
- REDIS_URL=redis://localhost:6379/0
- KAFKA_BOOTSTRAP_SERVERS=localhost:9092
- KAFKA_BEHAVIOR_TOPIC=user-behavior-events
- KAFKA_BEHAVIOR_CONSUMER_GROUP=rec-behavior-collector
- KAFKA_PRODUCT_TOPIC=product.events
- KAFKA_PRODUCT_CONSUMER_GROUP=rec-product-catalog-sync
- PRODUCT_SERVICE_BASE_URL=http://localhost:8090
- PRODUCT_SERVICE_CATALOG_PATH=/api/v1/internal/products/all
- PRODUCT_SERVICE_LOOKUP_PATH=/api/v1/internal/products/lookup
- PRODUCT_SERVICE_API_KEY_HEADER=X-Internal-Api-Key
- PRODUCT_SERVICE_API_KEY=change-me
- CF_ALPHA=0.6
- RECOMMENDATION_MODEL_REFRESH_SECONDS=120
- RECOMMENDATION_CACHE_TTL_SECONDS=60
- MODELS_DIR=models
- TRAINING_TIMEZONE=Asia/Ho_Chi_Minh
- TRAINING_CRON_HOUR=2
- TRAINING_CRON_MINUTE=0
