# AGENTS.md

## Project Snapshot
- Java 21 / Spring Boot 3.4 multi-module Maven monorepo (`pom.xml`) with ten Spring services, one NestJS service (`promotion-service`), and three FastAPI services (`agent-service`, `mcp-server`, `recommendation-service`); all run behind `api-gateway` (Spring Cloud Gateway on `:8080`). Request and data flow diagram lives in `ARCHITECTURE.md`.
- Core infra: Eureka (`discovery-server :8761`), Spring Cloud Config native repo (`config-server/src/main/resources/configs`), Kafka (KRaft `:9092`), Redis (`:6379`), Jaeger; all wired through `docker-compose.yml`.
- Every business service follows the same package layout (`config`, `controller`, `service`, `repository`, `dto`, `event`, `exception`, `entity/base`). Use existing service folders as templates when adding new services.

---

## Build & Run Workflow
- Containers: `make infra-up` → `make build` (skips tests by default) → `make up`; `make reset` nukes volumes before a clean redeploy (see `Makefile`).
- Local dev: run infra via Docker, then start `discovery-server`, `config-server`, then your target service with `mvn spring-boot:run` in that order (`README.md#local-development`).
- Health gates: wait for config + database health checks declared in `docker-compose.yml` before attaching dependent services; Compose already encodes `depends_on: condition: service_healthy`.

---

## Service Boundaries
- Ports and DBs are one-per-service; connection strings are injected via Compose env vars (see `docker-compose.yml`).
- `identity-service` owns auth, JWT, Redis blacklist; `user-service` holds profiles/addresses; `product-service` is Mongo-based; `order-service` orchestrates Feign calls to `inventory-service` + `product-service`; `payment-service` emits payment events; `notification-service` consumes Kafka and renders Thymeleaf templates under `notification-service/src/main/resources/templates`.

---

## Communication Patterns
- All external traffic hits `api-gateway`. Gateway validates JWTs and forwards `X-User-Id`, `X-User-Role`, `X-User-Email`, and `X-Correlation-Id` headers; downstream controllers read headers instead of parsing tokens.
- Sync calls rely on OpenFeign clients stored under each service's `client` package; they resolve via Eureka, so hostnames match `spring.application.name`.
- Async flows: Kafka topics described in `README.md#event-driven-communication`; every event extends `BaseEvent` and is published through per-service `event` packages.

---

## Configuration & Resilience

### Config Server
- Each service `application.yml` bootstraps name + `spring.config.import`; real configs live in `config-server/.../configs/*.yml`.
- **ALWAYS use `optional:configserver:`** (with the `optional:` prefix). Never use bare `configserver:` — it crashes the service if config-server is temporarily unreachable:
  ```yaml
  spring:
    config:
      import: "optional:file:./.env[.properties],optional:configserver:${CONFIG_SERVER_URI:http://localhost:8888}/"
  ```
- Every `application.yml` **must** include local fallback defaults for every critical property so the service can start with degraded (but working) config when config-server is offline:
  ```yaml
  spring:
    datasource:
      url: ${DATASOURCE_URL:jdbc:postgresql://localhost:5432/nexatech_db}
    security:
      oauth2:
        resourceserver:
          jwt:
            issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:9000/realms/nexatech}
  ```
- Add retry config so Spring Cloud Config client retries instead of giving up immediately:
  ```yaml
  spring:
    cloud:
      config:
        fail-fast: false
        retry:
          initial-interval: 1500
          max-attempts: 4
          max-interval: 5000
          multiplier: 1.5
  ```

### Dependency Resilience
- Use `spring.datasource.hikari.initialization-fail-timeout: -1` so HikariCP does not block startup if the DB is not immediately reachable; the pool retries on first request instead of crashing at boot.
- Every new service must implement a `ConfigServerStartupLogger` (see `user-service/src/main/java/.../config/common/ConfigServerStartupLogger.java`) that fires on `ApplicationReadyEvent` and logs:
  1. Whether a remote config-server property source was loaded.
  2. A snapshot of the five most critical effective config values with their source name (configserver / .env / default).
- Reuse global Resilience4j defaults (`sliding-window-size: 10`, `failure-rate-threshold: 50`, `retry max-attempts: 3`, `timeout: 5s`) already defined in `config-server/.../configs/application.yml`. Annotate Feign calls with `@CircuitBreaker(name = "identityService", fallbackMethod = "...")` and `@Retry(name = "identityService")`.
- Kafka consumer config must set `missing-topics-fatal: false` (already in shared config) and every `@KafkaListener` must have a Dead Letter Topic (DLT) configured; log the topic, partition, offset, key, and error when a message lands on DLT:
  ```java
  log.error("[DLT][{}] partition={} offset={} key={} error={}",
      topic, partition, offset, key, ex.getMessage());
  ```

---

## Error Codes & Log Traceability

### Service Error Code Format
Every service defines its error codes with a **service-prefix string tag** in addition to the numeric code, following this convention:

| Service | Prefix | Example |
|---|---|---|
| api-gateway | `GW` | `GW-4010` |
| user-service | `USR` | `USR-2001` |
| identity-service | `IDN` | `IDN-1001` |
| order-service | `ORD` | `ORD-3001` |
| product-service | `PRD` | `PRD-4001` |
| inventory-service | `INV` | `INV-5001` |
| payment-service | `PAY` | `PAY-6001` |
| notification-service | `NTF` | `NTF-7001` |
| promotion-service | `PRO` | `PRO-8001` |
| shipping-service | `SHP` | `SHP-9001` |
| chat-service | `CHT` | `CHT-9501` |
| storage-service | `STR` | `STR-9601` |
| audit-service | `AUD` | `AUD-9701` |

The `ErrorCode` enum must expose a `tag()` method: `"USR-2001"`. The `ApiResponse` body returns `tag` alongside `code` so clients can display and log a human-grep-able string.

### Structured Log Format (every ERROR and WARN)
Every log line at ERROR or WARN level must include the error tag in brackets so `docker logs <service> | grep "USR-2001"` gives zero-noise results:
```
ERROR [user-service,traceId,spanId] [USR-2001] GET /api/v1/users/me correlationId=abc-123 userId=9e209a23 — User not found
```
Use MDC to inject `correlationId` and `userId` at the filter/interceptor layer so they appear automatically in every log line within a request scope.

### GlobalExceptionHandler Requirements
Every `GlobalExceptionHandler` must log request context alongside the error. Do not log only the exception:
```java
// WRONG
log.error("Exception: ", exception);

// CORRECT
log.error("[{}] {} {} correlationId={} userId={} — {}",
    errorCode.getTag(),
    request.getMethod(), request.getRequestURI(),
    MDC.get("correlationId"), MDC.get("userId"),
    exception.getMessage());
```
For 5xx errors, log the full stack trace. For 4xx, log at WARN without stack trace unless it is a validation error that has no message.

---

## Correlation ID — End-to-End Tracing

### Gateway: generate and forward
`JwtHeaderRelayFilter` (or an equivalent filter in the gateway) must generate a `X-Correlation-Id` UUID if the incoming request does not already carry one, and forward it downstream:
```java
String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
    .filter(s -> !s.isBlank())
    .orElseGet(() -> UUID.randomUUID().toString());
extraHeaders.put("X-Correlation-Id", correlationId);
// Also set on response so browser can read it
response.setHeader("X-Correlation-Id", correlationId);
```

### Every downstream service: MDC injection
Add a `CorrelationIdFilter` (a `OncePerRequestFilter` for Spring MVC, a NestJS middleware for promotion-service) that reads `X-Correlation-Id`, puts it in MDC, and sets it on the response:
```java
MDC.put("correlationId", correlationId);
MDC.put("userId", request.getHeader("X-User-Id"));
try {
    filterChain.doFilter(request, response);
} finally {
    MDC.clear(); // Always clear after request
}
```

### Log pattern
The shared logging pattern in `config-server/.../configs/application.yml` must include `correlationId` from MDC:
```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{correlationId:-}]"
```

### Error responses
Every error response body must include `correlationId` so the frontend can display it to the user and support can grep Docker logs for it:
```json
{
  "code": 2001,
  "tag": "USR-2001",
  "message": "User not found",
  "correlationId": "abc-123-xyz",
  "timestamp": "2026-05-29T09:55:25Z"
}
```

---

## Startup Health & Dependency Logging

Every service must implement a startup check that logs the reachability of each dependency at boot:
```
INFO  [user-service] [STARTUP] Checking dependencies...
INFO  [user-service] [STARTUP] ✓ Database: reachable (jdbc:postgresql://localhost:5432/nexatech_users)
WARN  [user-service] [STARTUP] ✗ Redis: unreachable at localhost:6379 — cache disabled, sessions may not persist
INFO  [user-service] [STARTUP] ✓ Kafka: reachable at localhost:9092
WARN  [user-service] [STARTUP] ✗ Config server: not loaded — running with local defaults
INFO  [user-service] [STARTUP] ✓ Keycloak JWKS: reachable at http://localhost:9000/realms/nexatech
```
Unreachable dependencies must log at WARN (not ERROR) at startup — the service should still start. Throw at startup only if the dependency is truly required for any request to be servable (e.g., missing DB schema).

---

## Health Endpoints

Configure Spring Actuator with separate liveness and readiness probes:
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```
- `/actuator/health/liveness` — returns UP if the JVM is alive (always up unless process is dead).
- `/actuator/health/readiness` — returns UP only when DB, Redis, and Kafka are reachable.
- Docker Compose and Kubernetes must use `readiness` for `service_healthy` gates and traffic routing, and `liveness` for restart decisions.

---

## API & DTO Conventions
- Controllers return `ApiResponse<T>` and pagination wrappers from each service's `dto/response` package.
- `ApiResponse` must include `code` (int), `tag` (String, e.g. `"USR-2001"`), `message`, `correlationId`, and `result`.
- Errors funnel through `GlobalExceptionHandler` producing the JSON structure shown above.
- Entities extend `BaseEntity` (`identity-service/src/main/java/.../entity/base/BaseEntity.java`) to get UUID ids plus audit timestamps — extend instead of recreating fields.
- MapStruct mappers handle DTO ↔ entity conversions; match mapper naming (`XxxMapper`) and register with `@Mapper(componentModel = "spring")`.

---

## Data & Migrations
- Every SQL service uses Flyway under `src/main/resources/db/migration`; keep table naming consistent (`snake_case` columns, UUID PKs via `BaseEntity`).
- Mongo collections follow camelCase fields (`product-service`); reuse existing document schemas noted in `README.md#database-schema`.

---

## Frontend Error Handling Convention

### API error response contract
The frontend reads `tag`, `message`, and `correlationId` from every error response body. The `tag` field is the canonical identifier for both user-facing display and log tracing.

### User-facing display
Display errors as: `[tag] message` — for example:
```
[USR-2001] Không tìm thấy người dùng
[GW-4010] Phiên đăng nhập hết hạn, vui lòng đăng nhập lại
[PRO-8001] Sản phẩm này không còn trong chương trình flash sale
```
For 5xx errors, show the `correlationId` in the toast/alert so users can report it:
```
Đã xảy ra lỗi. Mã tra cứu: abc-123-xyz
```

### Frontend logger requirements
Every API call error must be logged to `console.error` with the full context so DevTools/Sentry captures it. Never swallow errors silently:
```typescript
// WRONG
catch (e) { /* nothing */ }

// CORRECT
catch (error) {
  const tag = error?.response?.data?.tag ?? 'UNKNOWN';
  const correlationId = error?.response?.data?.correlationId
    ?? error?.response?.headers?.['x-correlation-id']
    ?? 'none';
  const status = error?.response?.status;
  console.error(`[${tag}] HTTP ${status} ${endpoint} correlationId=${correlationId}`, error);
  // Then show user-facing message
  toast.error(`[${tag}] ${userMessage} (${correlationId})`);
}
```

### HTTP interceptor
Add a global Axios/Fetch interceptor that:
1. Attaches `X-Correlation-Id` to every outgoing request (generate UUID if not set by server).
2. On error response, reads `tag` + `correlationId` from body (fallback to response header).
3. Logs structured error with status, tag, endpoint, correlationId.
4. Maps HTTP status to user-facing category: 401 → redirect to login, 403 → show permission error, 503 → show "service temporarily unavailable", 5xx → show correlationId for support.

---

## Feign Client Logging

When a Feign call fails, log the outgoing request context alongside the error so it is traceable. Add a `RequestInterceptor` that attaches `X-Correlation-Id` from MDC to every outgoing Feign request:
```java
@Bean
public RequestInterceptor correlationIdFeignInterceptor() {
    return template -> {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            template.header("X-Correlation-Id", correlationId);
        }
    };
}
```
Set Feign log level to `BASIC` in non-prod and `HEADERS` in prod (never `FULL` — it logs request bodies which may contain PII). Configure per-client in the service's config yml.

---

## Docker & Log Hygiene

### Log driver
Every service in `docker-compose.yml` must declare a log driver with rotation to prevent disk fill:
```yaml
logging:
  driver: json-file
  options:
    max-size: "50m"
    max-file: "5"
    tag: "{{.Name}}"
```

### Grep recipes (document these in README)
```bash
# All errors from user-service in last 10 minutes
docker logs user-service --since 10m 2>&1 | grep "ERROR"

# Trace a specific error code across all services
docker logs user-service 2>&1 | grep "USR-2001"

# Follow a single request by correlation ID across all services
for svc in user-service order-service inventory-service; do
  echo "=== $svc ==="; docker logs $svc 2>&1 | grep "abc-123-xyz"
done

# All DLT (dead letter) Kafka failures
docker logs notification-service 2>&1 | grep "\[DLT\]"
```

---

## Adding a New Service — Checklist

When adding a new service, every item below is required before the PR is mergeable:

- [ ] Module registered in root `pom.xml` modules list.
- [ ] Config file added at `config-server/src/main/resources/configs/<service-name>.yml`.
- [ ] Routes registered in `config-server/.../configs/api-gateway.yml`.
- [ ] `docker-compose.yml` entry added with `depends_on: condition: service_healthy`, `logging` driver block, and DB/env wiring.
- [ ] `application.yml` uses `optional:configserver:` (never bare `configserver:`), includes local fallback defaults for all critical env vars.
- [ ] `spring.datasource.hikari.initialization-fail-timeout: -1` set.
- [ ] `spring.cloud.config.retry` block added.
- [ ] `ErrorCode` enum follows `[PREFIX-XXXX]` tag convention for the new service.
- [ ] `ApiResponse` includes `tag` and `correlationId` fields.
- [ ] `GlobalExceptionHandler` logs method + path + correlationId + userId + tag on every error.
- [ ] `CorrelationIdFilter` (MDC injection) added and registered.
- [ ] `ConfigServerStartupLogger` added to log effective config and config-server load status.
- [ ] Startup dependency log prints ✓/✗ for each dependency (DB, Redis, Kafka, external services).
- [ ] Actuator liveness and readiness probes configured and wired to Docker/K8s health checks.
- [ ] Feign `RequestInterceptor` forwards `X-Correlation-Id` from MDC.
- [ ] Kafka listeners have DLT configured with structured DLT log.
- [ ] Docker Compose `logging` block with `json-file`, `max-size: 50m`, `max-file: 5`.
- [ ] Frontend error handler reads `tag` + `correlationId` and logs to console with full context.

---

## Operational Notes
- Kong proxy runs on `:8000`, admin on `:8001`; Jaeger UI on `:16686`; Kafka bootstrap for services is `kafka:29092`, but host-based tooling connects via `localhost:9092`.
- Config changes in native mode require rebuilding the affected service container; there is no live refresh without repackage.
- `X-Correlation-Id` is the single thread to follow a request across all services and through the frontend. Always include it in bug reports.
