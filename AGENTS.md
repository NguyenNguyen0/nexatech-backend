# AGENTS.md

## Project Snapshot
- Java 21 / Spring Boot 3.4 multi-module Maven monorepo (`pom.xml`) with ten Spring services run behind Kong; request and data flow diagram lives in `README.md`.
- Core infra: Eureka (`discovery-server`), Spring Cloud Config native repo (`config-server/src/main/resources/configs`), Kafka, Redis, Jaeger; all wired through `docker-compose.yml`.
- Every business service follows the same package layout (`config`, `controller`, `service`, `repository`, `dto`, `event`, `exception`, `entity/base`). Use existing service folders as templates.

## Build & Run Workflow
- Containers: `make infra-up` → `make build` (skips tests by default) → `make up`; `make reset` nukes volumes before a clean redeploy (see `Makefile`).
- Local dev: run infra via Docker, then start `discovery-server`, `config-server`, and your target service with `mvn spring-boot:run` in that order (`README.md#3-local-development`).
- Health gates: wait for config + database health checks declared in `docker-compose.yml` before attaching dependent services; Compose already encodes `depends_on: condition: service_healthy`.

## Service Boundaries
- Ports & DBs are one-per-service; connection strings are injected via Compose env vars (see `docker-compose.yml` entries like `identity-service` + `postgres-identity`).
- `identity-service` owns auth, JWT, Redis blacklist; `user-service` holds profiles/addresses; `product-service` is Mongo-based; `order-service` orchestrates Feign calls to `inventory-service` + `product-service`; `payment-service` emits payment events; `notification-service` consumes Kafka and renders Thymeleaf templates under `notification-service/src/main/resources/templates`.

## Communication Patterns
- All external traffic hits Kong (`kong/kong.yml`). Kong validates JWTs and forwards `X-User-Id`, `X-User-Role`, `X-User-Email`; downstream controllers read headers instead of parsing tokens (e.g., `order-service` controllers in `src/main/java/.../order/controller`).
- Sync calls rely on OpenFeign clients stored under each service's `client` package; they resolve via Eureka, so hostnames match `spring.application.name`.
- Async flows: Kafka topics described in `README.md#event-driven-communication`; every event extends `BaseEvent` and is published through per-service `event` packages.

## Configuration & Resilience
- Each service `application.yml` only bootstraps name + `spring.config.import=configserver:`; real configs are in `config-server/.../configs/*.yml` (include DB creds, Kafka, Resilience4j defaults, Actuator exposure).
- Global Resilience4j settings (`sliding-window-size:10`, `failure-rate-threshold:50`, `retry max-attempts:3`, `timeout 5s`) already defined—reuse `@CircuitBreaker(name = "default")` etc. to stay aligned.
- Tracing + metrics: OTLP exporters and `/actuator/prometheus` exposure are prewired in the shared config; don’t override unless necessary.

## Data & Migrations
- Every SQL service uses Flyway under `src/main/resources/db/migration`; keep table naming consistent (`snake_case` columns, UUID PKs via `BaseEntity`).
- Mongo collections follow camelCase fields (`product-service`); reuse existing document schemas noted in `README.md#database-schema`.

## API & DTO Conventions
- Controllers return `ApiResponse<T>` and pagination wrappers from each service’s `dto/response` package; errors funnel through `GlobalExceptionHandler` producing the JSON structure shown in the README.
- Entities extend `BaseEntity` (`identity-service/src/main/java/.../entity/base/BaseEntity.java`) to get UUID ids plus audit timestamps—extend instead of recreating fields.
- MapStruct mappers handle DTO ↔ entity conversions; match mapper naming (`XxxMapper`) and register with `@Mapper(componentModel = "spring")`.

## Operational Notes
- Kong proxy runs on `:8000`, admin on `:8001`; Jaeger UI on `:16686`; Kafka bootstrap for services is `kafka:29092`, but host-based tooling connects via `localhost:9092`.
- Config changes in native mode require rebuilding the affected service container; there is no live refresh without repackage.
- When adding services, extend `docker-compose.yml`, add a config file under `config-server`, and register the module in the root `pom.xml` modules list.

