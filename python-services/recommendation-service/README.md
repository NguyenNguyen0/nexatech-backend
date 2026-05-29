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
- Behavior ingestion: `POST /behaviors` (`X-User-Id` header required)
- Behavior batch ingestion: `POST /behaviors/batch` (`X-User-Id` header required)
- User recommendations: `GET /recommend/user/{userId}` (default `enrich=true` includes `product` details)
- Similar items: `GET /recommend/similar/{productId}` (default `enrich=true`)
- Popular fallback: `GET /recommend/popular` (default `enrich=true`)

To return raw ranking payload only (without product metadata), pass `enrich=false`.

Behavior ingestion payload (example):

```json
{
	"eventId": "evt-123",
	"productId": "665f0e3cb9d9f63d40a7d111",
	"eventType": "VIEW",
	"rating": null,
	"categoryIds": ["phone"],
	"brandId": "apple",
	"price": 27990000,
	"timestamp": "2026-05-25T15:24:11Z"
}
```

Gateway path for frontend: `POST /api/v1/behaviors`.

Batch ingestion payload (example):

```json
{
	"events": [
		{
			"eventId": "evt-123",
			"productId": "665f0e3cb9d9f63d40a7d111",
			"eventType": "VIEW",
			"timestamp": "2026-05-25T15:24:11Z"
		},
		{
			"eventId": "evt-124",
			"productId": "665f0e3cb9d9f63d40a7d222",
			"eventType": "CART_ADD",
			"timestamp": "2026-05-25T15:24:14Z"
		}
	]
}
```

Gateway path for batch: `POST /api/v1/behaviors/batch`.

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
- BEHAVIOR_DEDUP_TTL_SECONDS=86400
- BEHAVIOR_BATCH_MAX_SIZE=100
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

## Service Graph

```mermaid
flowchart LR
	A[recommendation-service]
	B[product-service]
	C[mongodb]
	D[redis]
	E[kafka]
	F[discovery-server]
	G[api-gateway]

	A --> B
	A --> C
	A --> D
	A --> E
	A --> F
	G --> A
```

## Dependencies

- Product enrichment: [product-service](../../product-service/README.md)
- Exposed through: [api-gateway](../../api-gateway/README.md)
- Service discovery: [discovery-server](../../discovery-server/README.md)
- Infra: MongoDB, Redis, Kafka
