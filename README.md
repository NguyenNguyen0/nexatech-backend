<div align="center">
  <a href="https://ibb.co/0pjPLMSK">
    <img src="https://i.ibb.co/7xJ7DW5Q/nexatech-bg.jpg" alt="NexaTech Banner" width="100%" />
  </a>

  <h1>NexaTech Backend</h1>

  <p><strong>E-Commerce Microservices Platform</strong> - Java, Node.js and Python services on Spring Cloud, Kafka, Redis, MongoDB, PostgreSQL and Keycloak.</p>

  <p>
    <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21" />
    <img src="https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 4.0.3" />
    <img src="https://img.shields.io/badge/Spring%20Cloud-2025.1.0-6DB33F?style=flat-square&logo=spring&logoColor=white" alt="Spring Cloud 2025.1.0" />
    <img src="https://img.shields.io/badge/NestJS-10-E0234E?style=flat-square&logo=nestjs&logoColor=white" alt="NestJS 10" />
    <img src="https://img.shields.io/badge/FastAPI-Python%203.12-009688?style=flat-square&logo=fastapi&logoColor=white" alt="FastAPI" />
    <img src="https://img.shields.io/badge/Keycloak-26.5.2-4D4DFF?style=flat-square&logo=keycloak&logoColor=white" alt="Keycloak" />
    <img src="https://img.shields.io/badge/Kafka-KRaft-231F20?style=flat-square&logo=apachekafka&logoColor=white" alt="Kafka" />
    <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker Compose" />
  </p>
</div>

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Architecture Diagram](ARCHITECTURE.md)
- [Tech Stack](#tech-stack)
- [Services](#services)
- [Infrastructure](#infrastructure)
- [Getting Started](#getting-started)
- [Ports & Endpoints](#ports--endpoints)
- [API Overview](#api-overview)
- [Data Stores](#data-stores)
- [Event-Driven Communication](#event-driven-communication)
- [Configuration](#configuration)
- [Observability](#observability)
- [Project Structure](#project-structure)
- [Makefile Commands](#makefile-commands)

---

## Architecture Overview

```text
                         Frontend / API Client
                                  |
                                  | HTTP :8080
                                  v
                         Spring Cloud Gateway
                              api-gateway
                                  |
              +-------------------+-------------------+
              |                   |                   |
              v                   v                   v
       Java Spring services  Node.js service     Python FastAPI services
       identity, user,       promotion-service   agent-service,
       product, order,       (NestJS + Prisma)   mcp-server,
       inventory, payment,                       recommendation-service
       shipping, notification,
       chat, storage, news,
       audit
              |                   |                   |
              +-------------------+-------------------+
                                  |
        +-------------------------+-------------------------+
        |                         |                         |
        v                         v                         v
  Eureka Discovery          Spring Cloud Config        Keycloak IAM
      :8761                       :8888                 :9000

        +-------------------------+-------------------------+
        |                         |                         |
        v                         v                         v
 PostgreSQL :5432           MongoDB :27017            Redis :6379
 one database per           product/chat/reco         cache, sessions,
 business service                                  flash-sale stock

        +-------------------------+-------------------------+
                                  |
                                  v
                           Kafka KRaft :9092
                         async events/outbox
```

### Request Flow

1. Client calls `api-gateway` at `http://localhost:8080`.
2. Gateway validates OAuth2/JWT tokens from Keycloak and routes by path via Eureka load-balanced URIs.
3. Java services use Spring Cloud Config for centralized configuration and Eureka for service discovery.
4. Synchronous service calls use OpenFeign or HTTP clients; asynchronous workflows use Kafka.
5. Shared infrastructure for local development is started from `docker-compose.infra.yml`.

---

## Tech Stack

| Area | Technology |
|---|---|
| Java runtime | Java 21 |
| Java framework | Spring Boot 4.0.3 |
| Cloud stack | Spring Cloud 2025.1.0, Eureka, Spring Cloud Config, Spring Cloud Gateway |
| Identity provider | Keycloak 26.5.2 |
| Node.js service | NestJS 10, Fastify, Prisma 5, TypeScript 5 |
| Python services | FastAPI, uv, py-eureka-client |
| SQL database | PostgreSQL 16 Alpine |
| NoSQL database | MongoDB 7 |
| Cache / transient state | Redis 7 Alpine |
| Messaging | Apache Kafka in KRaft mode |
| Object storage | MinIO S3-compatible storage |
| Java persistence | Spring Data JPA, Flyway |
| Mongo persistence | Spring Data MongoDB / async Python Mongo repositories |
| Object mapping | MapStruct 1.6.3 |
| Resilience | Resilience4j |
| Metrics | Spring Boot Actuator, Prometheus endpoint |
| Build tools | Maven, npm, uv, Docker Compose |

---

## Services

Mỗi service có README riêng theo dạng graph dependency. Bảng dưới đây tổng hợp service hiện tại và link trực tiếp tới README của từng service.

| Service | Runtime | Port | Main Store | Depends On (Service Links) | README |
|---|---|---:|---|---|---|
| `api-gateway` | Spring Cloud Gateway | 8080 | - | [identity-service](identity-service/README.md), [user-service](user-service/README.md), [product-service](product-service/README.md), [order-service](order-service/README.md), [inventory-service](inventory-service/README.md), [payment-service](payment-service/README.md), [shipping-service](shipping-service/README.md), [notification-service](notification-service/README.md), [chat-service](chat-service/README.md), [storage-service](storage-service/README.md), [news-service](news-service/README.md), [promotion-service](promotion-service/README.md), [audit-service](audit-service/README.md), [agent-service](python-services/agent-service/README.md), [mcp-server](python-services/mcp-server/README.md), [recommendation-service](python-services/recommendation-service/README.md), [discovery-server](discovery-server/README.md) | [api-gateway/README.md](api-gateway/README.md) |
| `discovery-server` | Spring Boot (Eureka) | 8761 | - | Platform registry for all services | [discovery-server/README.md](discovery-server/README.md) |
| `config-server` | Spring Boot (Config Server) | 8888 | Native config repo | [discovery-server](discovery-server/README.md) | [config-server/README.md](config-server/README.md) |
| `identity-service` | Spring Boot | 8082 | Redis | [user-service](user-service/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [identity-service/README.md](identity-service/README.md) |
| `user-service` | Spring Boot | 8083 | PostgreSQL | [identity-service](identity-service/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [user-service/README.md](user-service/README.md) |
| `product-service` | Spring Boot | 8090 | MongoDB | [storage-service](storage-service/README.md), [order-service](order-service/README.md), [inventory-service](inventory-service/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [product-service/README.md](product-service/README.md) |
| `order-service` | Spring Boot | 8084 | PostgreSQL + Redis | [product-service](product-service/README.md), [inventory-service](inventory-service/README.md), [payment-service](payment-service/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [order-service/README.md](order-service/README.md) |
| `inventory-service` | Spring Boot | 8085 | PostgreSQL + Redis | [product-service](product-service/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [inventory-service/README.md](inventory-service/README.md) |
| `payment-service` | Spring Boot | 8086 | PostgreSQL | [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [payment-service/README.md](payment-service/README.md) |
| `shipping-service` | Spring Boot | 8091 | PostgreSQL | [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [shipping-service/README.md](shipping-service/README.md) |
| `notification-service` | Spring Boot | 8087 | PostgreSQL | [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [notification-service/README.md](notification-service/README.md) |
| `chat-service` | Spring Boot | 8089 | PostgreSQL + MongoDB + Redis | [user-service](user-service/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [chat-service/README.md](chat-service/README.md) |
| `storage-service` | Spring Boot | 8088 | MinIO | [product-service](product-service/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [storage-service/README.md](storage-service/README.md) |
| `news-service` | Spring Boot | 8092 | PostgreSQL + Redis + S3 | [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [news-service/README.md](news-service/README.md) |
| `audit-service` | Spring Boot | 8096 | MongoDB | [api-gateway](api-gateway/README.md), [config-server](config-server/README.md), [discovery-server](discovery-server/README.md) | [audit-service/README.md](audit-service/README.md) |
| `promotion-service` | NestJS | 8093 | PostgreSQL + Redis + Kafka | [api-gateway](api-gateway/README.md), [discovery-server](discovery-server/README.md) | [promotion-service/README.md](promotion-service/README.md) |
| `agent-service` | FastAPI | 8097 | - | [api-gateway](api-gateway/README.md), [discovery-server](discovery-server/README.md) | [python-services/agent-service/README.md](python-services/agent-service/README.md) |
| `mcp-server` | FastAPI | 8094 | - | [api-gateway](api-gateway/README.md), [discovery-server](discovery-server/README.md) | [python-services/mcp-server/README.md](python-services/mcp-server/README.md) |
| `recommendation-service` | FastAPI | 8095 | MongoDB + Redis + Kafka | [product-service](product-service/README.md), [api-gateway](api-gateway/README.md), [discovery-server](discovery-server/README.md) | [python-services/recommendation-service/README.md](python-services/recommendation-service/README.md) |

---

## Infrastructure

Local development infrastructure is defined in [`docker-compose.infra.yml`](docker-compose.infra.yml).

| Container | Host Port | Notes |
|---|---:|---|
| `postgres` | 5432 | Creates `keycloak` plus service databases from [`_postgres/init-databases.sql`](_postgres/init-databases.sql) |
| `mongodb` | 27017 | Used by product, chat and recommendation flows |
| `redis` | 6379 | Cache, token/session state, flash-sale reservation state |
| `kafka` | 9092 | Single-node Kafka KRaft broker; no Zookeeper |
| `keycloak` | 9000 | Imports realm from [`_realms/realm-export.json`](_realms/realm-export.json) |
| `minio` | 9002 / 9003 | S3 API on `9002`, console on `9003` |
| `minio-init` | - | Creates public `nexatech-products` bucket |

Default local credentials in the infra compose:

```env
POSTGRES_USER=nexatech
POSTGRES_PASSWORD=nexatech123
MONGO_INITDB_ROOT_USERNAME=nexatech
MONGO_INITDB_ROOT_PASSWORD=nexatech123
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin
MINIO_ROOT_USER=nexatech-minio
MINIO_ROOT_PASSWORD=nexatech-minio-secret
```

---

## Getting Started

### Prerequisites

| Tool | Required For |
|---|---|
| Docker Desktop / Docker Engine + Compose v2 | Local infrastructure |
| Java JDK 21 | Java services |
| Maven 3.9+ | Java builds |
| Node.js 20+ and npm | `promotion-service` |
| Python 3.12 and uv | Python FastAPI services |

### 1. Start Local Infrastructure

```bash
make dev-up
```

Equivalent command:

```bash
docker compose -f docker-compose.infra.yml up -d
```

Reset all local infra volumes when database/bootstrap state needs to be recreated:

```bash
make infra-reset
```

### 2. Build Java Modules

```bash
make build
```

This runs:

```bash
mvn clean package -DskipTests
```

### 3. Run Core Spring Infrastructure

Start these first:

```bash
make run-discovery
make run-config
make run-gateway
```

Then run the services you need, for example:

```bash
make run-identity
make run-user
make run-product
make run-inventory
make run-order
make run-payment
make run-chat
make run-storage
```

For Java modules that do not yet have a local Makefile shortcut, run Maven directly:

```bash
mvn -pl shipping-service spring-boot:run
mvn -pl news-service spring-boot:run
```

### 4. Run Promotion Service

Create `promotion-service/.env` with at least:

```env
NODE_ENV=development
PORT=8093
DATABASE_URL=postgresql://nexatech:nexatech123@localhost:5432/nexatech_promotions
REDIS_HOST=localhost
REDIS_PORT=6379
KAFKA_BROKERS=localhost:9092
KAFKA_GROUP_ID=promotion-service-group
RESERVATION_TTL_SECONDS=300
```

`promotion-service` defaults to port `8093`. `agent-service` now defaults to `8097`, so the two can run together without overriding either port.

Then:

```bash
cd promotion-service
npm install
npm run generate
npm run migrate:dev
npm run start:dev
```

From the repository root, Windows/PowerShell users can also use:

```bash
make run-promotion
```

### 5. Run Python Services

```bash
cd python-services/agent-service
uv run main.py

cd ../mcp-server
uv run main.py

cd ../recommendation-service
uv run main.py
```

Useful Python environment variables:

```env
SERVICE_NAME=recommendation-service
PORT=8095
EUREKA_ENABLED=true
EUREKA_SERVER_URL=http://localhost:8761/eureka/
EUREKA_INSTANCE_HOSTNAME=127.0.0.1
```

---

## Ports & Endpoints

| Service | URL | Health |
|---|---|---|
| API Gateway | `http://localhost:8080` | `GET /actuator/health` |
| Eureka Dashboard | `http://localhost:8761` | `GET /actuator/health` |
| Config Server | `http://localhost:8888` | `GET /actuator/health` |
| Keycloak | `http://localhost:9000` | - |
| Identity Service | `http://localhost:8082` | `GET /actuator/health` |
| User Service | `http://localhost:8083` | `GET /actuator/health` |
| Order Service | `http://localhost:8084` | `GET /actuator/health` |
| Inventory Service | `http://localhost:8085` | `GET /actuator/health` |
| Payment Service | `http://localhost:8086` | `GET /actuator/health` |
| Notification Service | `http://localhost:8087` | `GET /actuator/health` |
| Storage Service | `http://localhost:8088` | `GET /actuator/health` |
| Chat Service | `http://localhost:8089` | `GET /actuator/health` |
| Product Service | `http://localhost:8090` | `GET /actuator/health` |
| Shipping Service | `http://localhost:8091` | `GET /actuator/health` |
| News Service | `http://localhost:8092` | `GET /actuator/health` |
| Audit Service | `http://localhost:8096` | `GET /actuator/health` |
| Agent Service | `http://localhost:8097` | `GET /health` |
| MCP Server | `http://localhost:8094` | `GET /health` |
| Recommendation Service | `http://localhost:8095` | `GET /health`, `GET /ready` |
| Promotion Service | `http://localhost:8093` by default, or custom `PORT` | `GET /health` |

Infrastructure endpoints:

| Tool | URL |
|---|---|
| PostgreSQL | `localhost:5432` |
| MongoDB | `localhost:27017` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092` |
| MinIO S3 API | `http://localhost:9002` |
| MinIO Console | `http://localhost:9003` |

---

## API Overview

Most client traffic should go through `api-gateway` at `http://localhost:8080`. Services not yet present in the Spring Cloud Gateway route config, such as `promotion-service`, can be called directly during local development or added to [`api-gateway.yml`](config-server/src/main/resources/configs/api-gateway.yml). A Kong declarative config also exists at [`_kong/kong.yml`](_kong/kong.yml), but there is no current root Compose file wiring Kong into the local stack.

| Domain | Main Paths | Service |
|---|---|---|
| Authentication and sessions | `/api/v1/auth/**`, session/admin auth paths | `identity-service` |
| Users, addresses, roles, permissions, groups | `/api/v1/users/**`, `/api/v1/addresses/**`, role/group paths | `user-service` |
| Products, brands, categories | `/api/v1/products/**`, `/api/v1/brands/**`, `/api/v1/categories/**` | `product-service` |
| Product reviews and wishlist | review/wishlist paths under product domain | `product-service` |
| Cart, checkout, orders, returns | `/api/v1/cart/**`, `/api/v1/checkout/**`, `/api/v1/orders/**`, returns/admin order paths | `order-service` |
| Inventory | `/api/v1/inventory/**` | `inventory-service` |
| Payments and VNPay | `/api/v1/payments/**`, VNPay return/IPN paths | `payment-service` |
| Shipping | `/api/v1/shipping/**` | `shipping-service` |
| Notifications | `/api/v1/notifications/**`, admin notification paths | `notification-service` |
| Storage | `/api/v1/storage/**` | `storage-service` |
| Chat and typing | chat REST paths and WebSocket routes | `chat-service` |
| News | news command/query paths | `news-service` |
| Promotions and flash sales | `/api/v1/flash-sales/**` | `promotion-service` |
| Audit search | `/api/v1/admin/audit/**` | `audit-service` |
| Recommendations | `/api/v1/recommend/**`, `/api/v1/behaviors/**` through gateway; direct paths include `/recommend/user/{userId}`, `/recommend/similar/{productId}`, `/recommend/popular` | `recommendation-service` |
| Agent / MCP | service-specific FastAPI routes | `agent-service`, `mcp-server` |

Gateway routes are configured in [`config-server/src/main/resources/configs/api-gateway.yml`](config-server/src/main/resources/configs/api-gateway.yml).

---

## Data Stores

PostgreSQL databases created by [`_postgres/init-databases.sql`](_postgres/init-databases.sql):

| Database | Owner Service |
|---|---|
| `keycloak` | Keycloak |
| `nexatech_identity` | Identity-related local tables if enabled |
| `nexatech_users` | User service |
| `nexatech_orders` | Order service |
| `nexatech_inventory` | Inventory service |
| `nexatech_payments` | Payment service |
| `nexatech_shipping` | Shipping service |
| `nexatech_notifications` | Notification service |
| `nexatech_chats` | Chat service |
| `nexatech_news` | News service |
| `nexatech_promotions` | Promotion service |

MongoDB databases/collections are used primarily for product catalog data, chat document data, audit logs and recommendation features. MinIO stores uploaded media; the infra compose auto-creates `nexatech-products`.

Promotion service tables are managed by Prisma:

```text
promotions
flash_sales
flash_sale_reservations
promotion_redemptions
outbox_events
```

---

## Event-Driven Communication

Kafka is the backbone for asynchronous workflows. Shared Java Kafka defaults live in [`config-server/src/main/resources/configs/application.yml`](config-server/src/main/resources/configs/application.yml); service-specific topics and groups are configured in each service config.

Known event areas:

| Event Area | Producer | Consumers / Use |
|---|---|---|
| User events | `identity-service` | Profile/bootstrap and notification flows |
| Product/catalog events | `product-service` | Recommendation sync and downstream catalog consumers |
| Order events | `order-service` | Inventory, payment, notification and shipping workflows |
| Payment events | `payment-service` | Order status and notification workflows |
| Inventory events | `inventory-service` | Order/product visibility and notification workflows |
| Promotion/flash-sale events | `promotion-service` | Outbox-based publishing for flash sale reservation/sell events |
| Recommendation behavior events | frontend/services | `recommendation-service` collectors |

Recommendation service exposes readiness checks for MongoDB, Redis and Kafka via:

```bash
curl http://localhost:8095/ready
```

---

## Configuration

Spring services import configuration from the Config Server:

```yaml
spring:
  application:
    name: product-service
  config:
    import: "optional:file:./.env[.properties],optional:configserver:${CONFIG_SERVER_URI:http://localhost:8888}/"
```

Native config files:

```text
config-server/src/main/resources/configs/
├── application.yml
├── api-gateway.yml
├── identity-service.yml
├── user-service.yml
├── product-service.yml
├── order-service.yml
├── inventory-service.yml
├── payment-service.yml
├── shipping-service.yml
├── notification-service.yml
├── chat-service.yml
├── storage-service.yml
├── news-service.yml
└── audit-service.yml
```

Common local environment variables:

```env
CONFIG_SERVER_URI=http://localhost:8888
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/
KEYCLOAK_URL=http://localhost:9000
KEYCLOAK_ISSUER_URI=http://localhost:9000/realms/nexatech
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
DB_HOST=localhost
DB_USERNAME=nexatech
DB_PASSWORD=nexatech123
REDIS_HOST=localhost
REDIS_PORT=6379
```

Config changes require restarting the affected service.

---

## Observability

Spring Boot Actuator is enabled for Java services. The usual endpoints are:

```text
GET /actuator/health
GET /actuator/info
GET /actuator/prometheus
```

Local monitoring is defined in [`docker-compose.infra.yml`](docker-compose.infra.yml) and [`monitoring/prometheus.yml`](monitoring/prometheus.yml).

```bash
docker compose -f docker-compose.infra.yml --profile monitoring up -d
```

Open Prometheus at `http://localhost:9090` and Grafana at `http://localhost:3005` (`admin` / `admin`). Grafana auto-loads the Prometheus datasource and the `NexaTech Microservices Overview` dashboard.

For a Docker-only demo of the monitored services, use the optional profile:

```bash
docker compose -f docker-compose.infra.yml --profile monitoring --profile monitored-services up -d --build
```

Prometheus scrapes service container names on the `nexatech-infra-net` Docker network, for example `order-service:8084` and `promotion-service:8093`.

Eureka registration can be inspected at:

```text
http://localhost:8761
```

Python services register to Eureka through `py-eureka-client` when `EUREKA_ENABLED=true`.

---

## Project Structure

```text
nexatech-backend/
├── pom.xml                              # Maven parent and Java module list
├── Dockerfile                           # Generic Java service image build
├── Makefile                             # Local build/run shortcuts
├── docker-compose.infra.yml             # Local infrastructure only
├── _postgres/init-databases.sql         # PostgreSQL database bootstrap
├── _realms/realm-export.json            # Keycloak realm import
├── config-server/
│   └── src/main/resources/configs/      # Centralized Spring configs
├── api-gateway/                         # Spring Cloud Gateway
├── discovery-server/                    # Eureka
├── identity-service/                    # Auth/Keycloak facade
├── user-service/                        # Profiles/RBAC/address domain
├── product-service/                     # Product catalog and reviews
├── order-service/                       # Cart/checkout/order domain
├── inventory-service/                   # Stock/reservation domain
├── payment-service/                     # Payment and VNPay domain
├── shipping-service/                    # Shipping provider domain
├── notification-service/                # Notification domain
├── chat-service/                        # Chat/WebSocket domain
├── storage-service/                     # MinIO-backed file storage
├── news-service/                        # News/blog domain
├── audit-service/                       # Mongo-backed audit log domain
├── promotion-service/                   # NestJS promotions and flash sales
└── python-services/
    ├── agent-service/
    ├── mcp-server/
    └── recommendation-service/
```

Java business services keep the familiar package layout:

```text
com.nexatech.<service>/
├── config/
├── controller/
├── service/
├── repository/
├── dto/
├── mapper/
├── event/
├── exception/
└── entity or model/
```

---

## Makefile Commands

```bash
make help              # Show available commands
make build             # Maven clean package -DskipTests
make clean             # Clean Maven targets
make compile           # Compile Java modules

make dev-up            # Start local infrastructure
make dev-down          # Stop local infrastructure
make infra-reset       # Recreate local infrastructure volumes
make logs-infra        # Follow infrastructure logs

make run-discovery
make run-config
make run-gateway
make run-identity
make run-user
make run-product
make run-inventory
make run-order
make run-payment
make run-notification
make run-chat
make run-storage
make run-promotion
```

Run modules without local shortcuts directly:

```bash
mvn -pl shipping-service spring-boot:run
mvn -pl news-service spring-boot:run
```

Some Makefile targets reference a legacy `docker-compose.yml` full-stack file that is not present in the current repository snapshot. Use `make dev-up` plus per-service run targets or `mvn -pl <module> spring-boot:run` for local development.

---

## Useful Links

- Config Server API: `GET http://localhost:8888/{service-name}/default`
- Eureka API: `GET http://localhost:8761/eureka/apps`
- Keycloak Admin Console: `http://localhost:9000`
- MinIO Console: `http://localhost:9003`
