<div align="center">
  <a href="https://ibb.co/0pjPLMSK">
    <img src="https://i.ibb.co/7xJ7DW5Q/nexatech-bg.jpg" alt="NexaTech Banner" width="100%" />
  </a>

  <h1>NexaTech Backend</h1>

  <p><strong>E-Commerce Microservices Platform</strong> — Built on Java 21, Spring Boot 3.4.3 &amp; Spring Cloud 2024.0.0</p>

  <p>
    <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21" />
    <img src="https://img.shields.io/badge/Spring%20Boot-3.4.3-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot" />
    <img src="https://img.shields.io/badge/Spring%20Cloud-2024.0.0-6DB33F?style=flat-square&logo=spring&logoColor=white" alt="Spring Cloud" />
    <img src="https://img.shields.io/badge/Kong-3.6-003459?style=flat-square&logo=kong&logoColor=white" alt="Kong" />
    <img src="https://img.shields.io/badge/Kafka-7.6.0-231F20?style=flat-square&logo=apachekafka&logoColor=white" alt="Kafka" />
    <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker" />
  </p>
</div>

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Services](#services)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [1. Clone & Configure Environment](#1-clone--configure-environment)
  - [2. Run with Docker Compose](#2-run-with-docker-compose)
  - [3. Local Development](#3-local-development)
- [Ports & Endpoints](#ports--endpoints)
- [API Overview](#api-overview)
- [Database Schema](#database-schema)
- [Event-Driven Communication](#event-driven-communication)
- [Configuration (Config Server)](#configuration-config-server)
- [Observability](#observability)
- [Project Structure](#project-structure)
- [Patterns & Conventions](#patterns--conventions)
- [Makefile Commands](#makefile-commands)
- [Changelog & Improvements](#changelog--improvements)

---

## Architecture Overview

```
                        ┌──────────────────────────────────┐
                        │           Frontend (Next.js)       │
                        └────────────────┬─────────────────┘
                                         │ HTTP :8000
                        ┌────────────────▼─────────────────┐
                        │        Kong API Gateway            │
                        │   (DB-less / Declarative Config)   │
                        │       CORS, Rate Limiting          │
                        └──┬──────┬──────┬──────┬──────┬───┘
                           │      │      │      │      │
               ┌───────────┘  ┌───┘  ┌───┘  ┌───┘  ┌──┘
               ▼              ▼      ▼      ▼      ▼
         ┌──────────┐  ┌──────────┐  ...  ┌──────────┐  ┌──────────────┐
         │ identity │  │  user    │       │  order   │  │notification  │
         │ :8081    │  │  :8082   │       │  :8084   │  │  :8087       │
         └────┬─────┘  └────┬─────┘       └────┬─────┘  └──────┬───────┘
              │              │                  │               │
         ┌────▼──────────────▼──────────────────▼───────────────▼────┐
         │              Spring Cloud Infrastructure                    │
         │  ┌────────────────┐     ┌──────────────────────────────┐  │
         │  │ Eureka Discovery│     │   Spring Cloud Config Server  │  │
         │  │    :8761        │     │         :8888                 │  │
         │  └────────────────┘     └──────────────────────────────┘  │
         └─────────────────────────────────────────────────────────────┘
              │              │                  │               │
         ┌────▼────┐  ┌──────▼──┐  ┌───────────▼──┐  ┌────────▼────┐
         │PostgreSQL│  │ MongoDB │  │    Kafka      │  │   Redis     │
         │(per svc) │  │Products │  │  (Events)     │  │  (Cache /   │
         └──────────┘  └─────────┘  └───────────────┘  │  Token BL)  │
                                                        └─────────────┘
                                                               │
                                                    ┌──────────▼────────┐
                                                    │   Jaeger Tracing   │
                                                    │     :16686         │
                                                    └───────────────────┘
```

### Request Flow

1. **Client** → `Kong :8000` (proxy, CORS handling)
2. **Kong** → Downstream service (forwards `X-User-Id`, `X-User-Role`, `X-User-Email` headers)
3. **Services** communicate synchronously via **OpenFeign** (service discovery through Eureka)
4. **Services** communicate asynchronously via **Apache Kafka** (events)
5. **Configuration** is centralized at the **Config Server** (native classpath)
6. **Traces** are exported via OTLP → **Jaeger**

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.3 |
| Cloud | Spring Cloud | 2024.0.0 |
| API Gateway | Kong | 3.6 |
| Service Discovery | Spring Cloud Netflix Eureka | - |
| Config Management | Spring Cloud Config (native) | - |
| Messaging | Apache Kafka + Zookeeper | 7.6.0 (Confluent) |
| SQL Database | PostgreSQL | 16-alpine |
| NoSQL Database | MongoDB | 7 |
| Cache / Token BL | Redis | 7-alpine |
| ORM | Spring Data JPA (Hibernate) | - |
| DB Migration | Flyway | - |
| Object Mapping | MapStruct | 1.6.3 |
| Security | Spring Security (Stateless JWT) | - |
| JWT | JJWT | 0.12.6 |
| Service-to-Service | OpenFeign | - |
| Resilience | Resilience4j (CB / Retry / Timeout) | - |
| Tracing | Micrometer OTLP → Jaeger | - |
| Metrics | Micrometer → Prometheus | - |
| Containerization | Docker + Docker Compose | - |
| Build | Apache Maven | 3.x |
| Code Gen | Lombok | - |
| Email | Spring Mail (Thymeleaf templates) | - |

---

## Services

| Service | Port | Database | Description |
|---|---|---|---|
| `discovery-server` | 8761 | — | Eureka Server — service registration & discovery |
| `config-server` | 8888 | — | Spring Cloud Config Server — centralized configuration |
| `identity-service` | 8081 | PostgreSQL + Redis | Authentication, JWT issuance, credentials & refresh token management |
| `user-service` | 8082 | PostgreSQL | User profiles and shipping addresses |
| `product-service` | 8083 | MongoDB + Redis | Product catalog, categories, and brands |
| `order-service` | 8084 | PostgreSQL | Order management and order items |
| `inventory-service` | 8085 | PostgreSQL + Redis | Stock tracking, movements, and reservation |
| `payment-service` | 8086 | PostgreSQL | Payment processing |
| `notification-service` | 8087 | PostgreSQL | Email/notification consumer from Kafka events |
| `kong` | 8000 (proxy) / 8001 (admin) | — | API Gateway |

---

## Prerequisites

| Tool | Minimum Version | Required For |
|---|---|---|
| Docker Desktop | 24.x+ | Running the full stack |
| Docker Compose | v2.x+ | Running the full stack |
| Java JDK | 21 | Local development only |
| Apache Maven | 3.9+ | Local development only |

> **Note:** To run the entire stack with Docker, only Docker Desktop is required. JDK and Maven are only needed when developing or debugging locally.

---

## Getting Started

### 1. Clone & Configure Environment

```bash
git clone <repo-url>
cd nexatech-backend

# Create .env from template
cp .env.example .env
```

Edit `.env` with your values:

```env
# === Database ===
POSTGRES_USER=nexatech
POSTGRES_PASSWORD=your_strong_password

MONGO_USER=nexatech
MONGO_PASSWORD=your_strong_password

# === JWT ===
JWT_SECRET=your-very-long-random-secret-key-at-least-256-bits
JWT_ACCESS_EXPIRATION=900000       # 15 minutes (ms)
JWT_REFRESH_EXPIRATION=604800000   # 7 days (ms)

# === Mail (Gmail App Password) ===
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password
```

> ⚠️ **Security:** Never commit the `.env` file to the repository. It is already excluded in `.gitignore`.

### 2. Run with Docker Compose

**Step 1 — Start infrastructure (databases, Kafka, Redis, Jaeger):**

```bash
make infra-up
# Or:
docker compose up -d postgres-identity postgres-user postgres-order postgres-inventory \
  postgres-payment postgres-notification mongodb redis zookeeper kafka jaeger
```

**Step 2 — Build all services:**

```bash
make build
# Or:
mvn clean package -DskipTests
```

**Step 3 — Start the full stack:**

```bash
make up
# Or:
docker compose up -d --build
```

**Check container status:**

```bash
docker compose ps
```

**Tail logs:**

```bash
make logs SERVICE=identity-service
# Or:
docker compose logs -f identity-service
```

### 3. Local Development

Run infrastructure via Docker, but start a specific service directly with Java:

```bash
# 1. Start infrastructure
make infra-up

# 2. Start discovery-server first (required by all services)
cd discovery-server
mvn spring-boot:run

# 3. Start config-server (required before starting any business service)
cd config-server
mvn spring-boot:run

# 4. Start the service you are working on
cd identity-service
mvn spring-boot:run
```

> **Tip:** Use your IDE (IntelliJ IDEA / VS Code) to run and debug individual services.

---

## Ports & Endpoints

### Services

| Service | URL | Health Check |
|---|---|---|
| Kong Proxy | `http://localhost:8000` | — |
| Kong Admin | `http://localhost:8001` | `GET /` |
| Eureka Dashboard | `http://localhost:8761` | `GET /actuator/health` |
| Config Server | `http://localhost:8888` | `GET /actuator/health` |
| Identity Service | `http://localhost:8081` | `GET /actuator/health` |
| User Service | `http://localhost:8082` | `GET /actuator/health` |
| Product Service | `http://localhost:8083` | `GET /actuator/health` |
| Order Service | `http://localhost:8084` | `GET /actuator/health` |
| Inventory Service | `http://localhost:8085` | `GET /actuator/health` |
| Payment Service | `http://localhost:8086` | `GET /actuator/health` |
| Notification Service | `http://localhost:8087` | `GET /actuator/health` |

### Infrastructure

| Service | URL |
|---|---|
| Jaeger UI | `http://localhost:16686` |
| Kafka | `localhost:9092` |
| Redis | `localhost:6379` |
| MongoDB | `localhost:27017` |
| PostgreSQL (identity) | `localhost:5433` |
| PostgreSQL (user) | `localhost:5434` |
| PostgreSQL (order) | `localhost:5435` |
| PostgreSQL (inventory) | `localhost:5436` |
| PostgreSQL (payment) | `localhost:5437` |
| PostgreSQL (notification) | `localhost:5438` |

---

## API Overview

All API requests go through the Kong Gateway at `http://localhost:8000`.

### Authentication — `/api/v1/auth`

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new account → returns access + refresh token | Public |
| `POST` | `/api/v1/auth/login` | Sign in → returns access + refresh token | Public |
| `POST` | `/api/v1/auth/refresh` | Renew access token using a refresh token | Public |
| `POST` | `/api/v1/auth/change-password` | Change password (`X-User-Id` header required) | Authenticated |
| `POST` | `/api/v1/auth/logout` | Sign out — blacklists the current token (`X-User-Id` header) | Authenticated |

**Request body — Register:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "fullName": "Nguyen Van A"
}
```

**Response — AuthResponse:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716...",
    "expiresIn": 900000
  }
}
```

---

### Users — `/api/v1/users`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/users/{id}` | Get user profile |
| `PUT` | `/api/v1/users/{id}` | Update user profile |
| `GET` | `/api/v1/users/{id}/addresses` | List all addresses for a user |
| `POST` | `/api/v1/users/{id}/addresses` | Add a new shipping address |
| `PUT` | `/api/v1/users/{id}/addresses/{addrId}` | Update an address |
| `DELETE` | `/api/v1/users/{id}/addresses/{addrId}` | Delete an address |

---

### Products — `/api/v1/products` & `/api/v1/categories`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/products` | List all products (paginated) |
| `GET` | `/api/v1/products/{id}` | Get product details |
| `GET` | `/api/v1/products/search?keyword=` | Search products by keyword |
| `GET` | `/api/v1/products/category/{categoryId}` | Get products by category |
| `POST` | `/api/v1/products` | Create a new product (Admin / Manager) |
| `PUT` | `/api/v1/products/{id}` | Update a product |
| `DELETE` | `/api/v1/products/{id}` | Delete a product |
| `GET` | `/api/v1/categories` | List all categories |

---

### Orders — `/api/v1/orders`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/orders` | Create a new order (`X-User-Id` header required) |
| `GET` | `/api/v1/orders/{id}` | Get order details |
| `GET` | `/api/v1/orders/my` | Get current user's orders |
| `GET` | `/api/v1/orders` | Get all orders (Admin) |
| `PATCH` | `/api/v1/orders/{id}/status?status=` | Update order status |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancel an order |

**Order Status Flow:**
```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED → COMPLETED
                                                        ↘ RETURNED → REFUNDED
        ↘ CANCELLED (from PENDING or CONFIRMED)
```

---

### Inventory — `/api/v1/inventory`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/inventory/{productId}` | Get stock status for a product |
| `POST` | `/api/v1/inventory/check` | Check stock availability (called by order-service via Feign) |
| `POST` | `/api/v1/inventory/reserve` | Reserve stock when an order is placed |
| `PUT` | `/api/v1/inventory/{productId}` | Update stock quantity |

---

### Payments — `/api/v1/payments`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/payments` | Initiate a payment for an order |
| `GET` | `/api/v1/payments/{id}` | Get transaction details |
| `GET` | `/api/v1/payments/order/{orderId}` | Get payment history for an order |

---

### Notifications — `/api/v1/notifications`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/notifications` | List notifications for the current user |
| `PATCH` | `/api/v1/notifications/{id}/read` | Mark a notification as read |

---

## Database Schema

### identity-service (PostgreSQL)

```sql
credentials (id, email, password, role, enabled, email_verified, created_at, updated_at)
refresh_tokens (id, token, user_id, expiry_date, revoked, created_at, updated_at)
```

### user-service (PostgreSQL)

```sql
user_profiles (id, full_name, email, phone, avatar_url, role, active, created_at, updated_at)
addresses     (id, user_id, recipient_name, phone, province, district, ward, street_address,
               default_address, created_at, updated_at)
```

### product-service (MongoDB)

```
products   { id, name, slug, description, price, compareAtPrice, sku, categoryId, brandId,
             images[], attributes{}, active, createdAt, updatedAt }
categories { id, name, slug, parentId, imageUrl, active, ... }
brands     { id, name, slug, logoUrl, active, ... }
```

### order-service (PostgreSQL)

```sql
orders      (id, order_number, customer_id, status, total_amount, shipping_fee, discount,
             shipping_address, note, created_at, updated_at)
order_items (id, order_id → orders.id, product_id, product_name, quantity, unit_price, subtotal, ...)
```

### inventory-service (PostgreSQL)

```sql
inventory_items  (id, product_id UNIQUE, quantity, reserved_quantity, reorder_level,
                  warehouse_location, created_at, updated_at)
stock_movements  (id, product_id, type, quantity, reference, reason, performed_by, created_at, updated_at)
```

### payment-service (PostgreSQL)

```sql
payments (id, order_id, customer_id, amount, method, status, transaction_id, failure_reason,
          created_at, updated_at)
```

### notification-service (PostgreSQL)

```sql
notifications (id, user_id, type, channel, subject, content, status, failure_reason,
               created_at, updated_at)
```

---

## Event-Driven Communication

All Kafka events extend `BaseEvent` with the fields: `eventId`, `eventType`, `timestamp`.

### Kafka Topics

| Topic | Producer | Consumer(s) | Event |
|---|---|---|---|
| `user.events` | `identity-service` | `user-service`, `notification-service` | `UserRegisteredEvent` |
| `order.events` | `order-service` | `inventory-service`, `notification-service` | `OrderEvent` |
| `payment.events` | `payment-service` | `order-service`, `notification-service` | *(planned)* |
| `inventory.events` | `inventory-service` | `notification-service` | *(planned)* |

### Main Event Flows

```
[User Registers]
  identity-service → user.events → user-service (create UserProfile)
                                 → notification-service (send welcome email)

[Order Created]
  order-service ──(Feign)──→ inventory-service (check & reserve stock)
  order-service ──(Feign)──→ product-service (fetch product details)
  order-service → order.events → notification-service (send confirmation email)

[Payment Completed]
  payment-service → payment.events → order-service (update order status)
                                   → notification-service (send receipt email)
```

### Feign Clients (Synchronous)

| Client | From Service | To Service | Endpoints |
|---|---|---|---|
| `InventoryClient` | `order-service` | `inventory-service` | `POST /api/v1/inventory/check`, `/reserve` |
| `ProductClient` | `order-service` | `product-service` | `GET /api/v1/products/{id}` |

---

## Configuration (Config Server)

The Config Server runs in **native** mode (classpath), with all configuration files stored at:

```
config-server/src/main/resources/configs/
├── application.yml          # Shared config (Eureka, Actuator, Resilience4j, logging)
├── identity-service.yml
├── user-service.yml
├── product-service.yml
├── order-service.yml
├── inventory-service.yml
├── payment-service.yml
└── notification-service.yml
```

Each service needs only a minimal bootstrap `application.yml`:
```yaml
spring:
  application:
    name: identity-service
  config:
    import: "configserver:"
```

The Config Server will automatically serve `identity-service.yml` when the service starts.

> **Note:** With the native profile, configuration changes require a service rebuild. For production, consider switching to a git-backed profile to support hot-reload.

### Resilience4j (Global Defaults)

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50%
        wait-duration-in-open-state: 10s
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 1s
  timelimiter:
    configs:
      default:
        timeout-duration: 5s
```

---

## Observability

### Distributed Tracing — Jaeger

Access the Jaeger UI at **`http://localhost:16686`** to trace requests across services.

Configuration in `application.yml` (global):
```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # 100% sampling (dev). Reduce to 0.1 for production
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### Metrics — Prometheus

All services expose metrics at `GET /actuator/prometheus`.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  endpoint:
    health:
      show-details: always
```

### Eureka Dashboard

View the registration status of all services at **`http://localhost:8761`**.

---

## Project Structure

```
nexatech-backend/
├── pom.xml                          # Root POM (parent, dependency management)
├── docker-compose.yml               # Full stack: infra + services + gateway
├── Dockerfile                       # Multi-service build (ARG SERVICE_NAME)
├── Makefile                         # Shortcut commands
├── .env.example                     # Environment variable template
├── kong/
│   └── kong.yml                     # Kong declarative config (routes, plugins)
├── config-server/
│   └── src/main/resources/configs/  # Centralized config for all services
├── discovery-server/                # Eureka Server
├── identity-service/                # Auth: JWT, credentials, refresh tokens
│   └── src/main/java/.../identity/
│       ├── config/           # SecurityConfig, KafkaConfig, JacksonConfig
│       ├── controller/       # AuthController
│       ├── service/          # AuthService, TokenService
│       ├── model/            # Credential, RefreshToken (JPA entities)
│       ├── dto/request/      # LoginRequest, RegisterRequest, ...
│       ├── dto/response/     # AuthResponse, ApiResponse, ErrorResponse
│       ├── event/            # UserRegisteredEvent, AuthEventProducer
│       ├── exception/        # GlobalExceptionHandler, custom exceptions
│       ├── enums/            # UserRole
│       └── entity/base/      # BaseEntity (UUID PK, audit fields)
├── user-service/
├── product-service/
├── order-service/
│   └── src/main/java/.../order/
│       ├── client/           # InventoryClient (Feign), ProductClient (Feign)
│       ├── event/            # OrderEvent, OrderEventProducer
│       └── ...
├── inventory-service/
├── payment-service/
└── notification-service/
    └── src/main/java/.../notification/
        ├── event/            # NotificationEventConsumer (Kafka listeners)
        └── service/          # EmailService (Thymeleaf + Spring Mail)
```

### Standard Package Structure per Service

```
com.nexatech.<service>/
├── config/           # Spring config beans (Security, Kafka, Feign, ...)
├── controller/       # REST controllers
├── service/          # Business logic
├── repository/       # Spring Data repositories
├── model/            # JPA entities / MongoDB documents
├── dto/
│   ├── request/      # Input DTOs (validated)
│   └── response/     # Output DTOs
├── mapper/           # MapStruct mappers
├── event/            # Kafka events & producers/consumers
├── exception/        # Custom exceptions + GlobalExceptionHandler
├── enums/            # Enumerations
└── entity/base/      # BaseEntity
```

---

## Patterns & Conventions

### Response Wrapper

```java
// All APIs return ApiResponse<T>
ApiResponse.ok(data)         // 200 success
ApiResponse.ok(null)         // 204-style void

// Pagination
PageResponse.of(page)        // { content[], page, size, total, totalPages }
```

### Base Entity

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
```

### Base Event

```java
public abstract class BaseEvent {
    private UUID eventId;
    private String eventType;
    private Instant timestamp;
}
```

### Error Response

```json
{
  "status": 400,
  "message": "Validation failed",
  "errors": [
    { "field": "email", "message": "must be a valid email" }
  ],
  "path": "/api/v1/auth/register",
  "timestamp": "2026-03-17T08:00:00Z"
}
```

### Kong Header Forwarding

Kong forwards user information to downstream services via the following headers (after JWT validation):

| Header | Description |
|---|---|
| `X-User-Id` | User UUID (credential id) |
| `X-User-Role` | Role: `ADMIN`, `CUSTOMER`, ... |
| `X-User-Email` | User's email address |

Services read these headers instead of validating JWT themselves, for example:
```java
@GetMapping("/my")
public ApiResponse<PageResponse<OrderResponse>> getMyOrders(
        @RequestHeader("X-User-Id") UUID customerId,
        Pageable pageable) { ... }
```

### UserRole

```java
public enum UserRole {
    ADMIN, MANAGER, STAFF, WAREHOUSE, ACCOUNTANT, CUSTOMER
}
```

### OrderStatus

```java
public enum OrderStatus {
    PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED,
    COMPLETED, CANCELLED, RETURNED, REFUNDED
}
```

---

## Makefile Commands

```bash
# Build the entire Maven project
make build

# Clean build output
make clean

# Compile only (without packaging)
make compile

# Start infrastructure only (DB, Kafka, Redis, Jaeger)
make infra-up

# Stop infrastructure
make infra-down

# Build & start the full stack (infra + services + kong)
make up

# Stop all containers
make down

# Rebuild & restart a specific service
make rebuild SERVICE=identity-service

# Stream real-time logs for a service
make logs SERVICE=order-service

# Full reset (remove containers + volumes and restart from scratch)
make reset
```
---


## Additional Information

- **Spring Boot Actuator** — All services expose: `GET /actuator/health`, `GET /actuator/info`
- **Config Server API** — `GET http://localhost:8888/{service-name}/default`
- **Eureka API** — `GET http://localhost:8761/eureka/apps`
- **Kong Admin** — `GET http://localhost:8001/services`, `GET http://localhost:8001/routes`
