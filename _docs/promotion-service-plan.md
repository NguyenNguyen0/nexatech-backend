# Promotion / Flash Sale Service — Final Implementation Plan
**NexaTech Microservices System**  
*Ngày cập nhật: 25/05/2026*

---

## 1. Tổng quan

### 1.1 Mục tiêu service

Trong hệ thống **NexaTech**, tạo một service riêng tên là **Promotion / Flash Sale Service**.

Service này chịu trách nhiệm xử lý:

- Chương trình khuyến mãi
- Mã giảm giá / coupon
- Flash sale
- Giữ slot flash sale tạm thời
- Xác nhận slot khi thanh toán thành công
- Hoàn slot khi thanh toán thất bại hoặc quá hạn
- Đồng bộ dữ liệu flash sale về database

Service này áp dụng **Space-Based Architecture** để xử lý tốt các tình huống có lượng request tăng đột biến, đặc biệt là flash sale.

---

## 2. Vị trí trong hệ thống NexaTech

```text
                         ┌─────────────────────────────────────────┐
                         │           Kong API Gateway :8000         │
                         └──────────────────┬──────────────────────┘
                                            │
              ┌──────────────────┬──────────┴──────────┬───────────────────┐
              │                  │                      │                   │
     ┌────────▼──────┐  ┌────────▼──────┐   ┌─────────▼──────┐  ┌────────▼──────┐
     │ Order Service │  │Payment Service│   │Promotion Service│  │ Other Services │
     │  Java/SB      │  │  Java/SB      │   │ Node.js/NestJS  │  │  Java/SB       │
     │  port: 8084   │  │  port: 8086   │   │  port: 8093     │  │                │
     └───────┬───────┘  └──────┬────────┘   └────────┬───────┘  └────────────────┘
             │                 │                      │
             └─────────────────┴──────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │     Kafka Broker     │
                    │     kafka:19092      │
                    └──────────┬──────────┘
                               │
              ┌────────────────┴───────────────────┐
              │                                    │
   ┌──────────▼──────────┐             ┌───────────▼──────────┐
   │      Redis :6379     │             │  PostgreSQL :5432    │
   │  Space Layer /       │             │  nexatech_promotions │
   │  In-memory Store     │             │  Persistent Store    │
   └──────────────────────┘             └──────────────────────┘
```

---

## 3. Port service

### 3.1 Không dùng port 3000

Port `3000` thường được dùng cho frontend hoặc admin dashboard, nên **không nên dùng cho Promotion Service**.

### 3.2 Port đề xuất

Promotion Service dùng port:

```text
8093
```

Lý do:

- Đồng bộ với các backend service Spring Boot khác.
- Tránh đụng port với frontend.
- Dễ quản lý khi deploy nhiều service.
- Node.js/NestJS có thể chạy ở bất kỳ port nào, không bắt buộc là `3000`.

Service mapping thực tế trong hệ thống:

```text
Identity Service    -> Spring Boot -> 8081
User Service        -> Spring Boot -> 8082
Order Service       -> Spring Boot -> 8084
Inventory Service   -> Spring Boot -> 8085
Payment Service     -> Spring Boot -> 8086
Notification Service-> Spring Boot -> 8087
Storage Service     -> Spring Boot -> 8088  ← đã dùng
Chat Service        -> Spring Boot -> 8089
Product Service     -> Spring Boot -> 8090
Shipping Service    -> Spring Boot -> 8091
News Service        -> Spring Boot -> 8092
Promotion Service   -> Node.js     -> 8093  ← port mới
```

Trong NestJS:

```ts
await app.listen(process.env.PORT || 8093);
```

---

## 4. Lý do dùng Node.js / NestJS

| Tiêu chí | Spring Boot | Node.js / NestJS |
|---|---|---|
| Khởi động | Chậm hơn | Nhanh hơn |
| Memory footprint | Cao hơn | Nhẹ hơn |
| I/O concurrency | Thread-based | Event loop, non-blocking |
| Phù hợp flash sale spike | Tốt | Rất phù hợp |
| Kết nối Redis/Kafka | Tốt | Tốt |
| Scale ngang | Tốt | Tốt |

Promotion Service có nghiệp vụ thiên về:

- I/O nhiều
- Redis operation nhiều
- Kafka event nhiều
- Request spike trong thời gian ngắn

Vì vậy dùng **Node.js / NestJS** là hợp lý.

---

## 5. Kiến trúc Space-Based Architecture

### 5.1 Ý tưởng chính

Không xử lý flash sale trực tiếp trên database trong từng request.

Thay vào đó:

```text
Request mua flash sale
        |
        v
Promotion Service
        |
        v
Redis Space Layer
        |
        v
Atomic reserve / release
        |
        v
Đồng bộ về PostgreSQL
```

Redis đóng vai trò là **Space Layer**, lưu dữ liệu realtime của flash sale.

PostgreSQL đóng vai trò là **Persistent Store**, lưu dữ liệu bền vững.

Kafka dùng để giao tiếp với Order Service và Payment Service.

---

## 6. Công nghệ sử dụng

| Hạng mục | Công nghệ |
|---|---|
| Runtime | Node.js 20 LTS |
| Framework | NestJS |
| Port | 8093 |
| Database | PostgreSQL |
| Database name | nexatech_promotions |
| ORM | Prisma |
| Space Layer | Redis |
| Redis Client | ioredis |
| Message Queue | Kafka |
| Kafka Client | kafkajs |
| API Gateway | Kong |
| Container | Docker |
| Auth context | Kong inject headers |

Promotion Service **không cần tích hợp Eureka**. Kong sẽ route trực tiếp đến:

```text
http://promotion-service:8093
```

---

## 7. Cấu trúc thư mục

```text
promotion-service/
├── Dockerfile
├── package.json
├── tsconfig.json
├── .env.example
├── prisma/
│   ├── schema.prisma
│   └── migrations/
│
└── src/
    ├── main.ts
    ├── app.module.ts
    │
    ├── config/
    │   ├── app.config.ts
    │   ├── redis.config.ts
    │   └── kafka.config.ts
    │
    ├── promotion/
    │   ├── promotion.module.ts
    │   ├── promotion.controller.ts
    │   ├── promotion.service.ts
    │   ├── promotion.repository.ts
    │   └── dto/
    │       ├── create-promotion.dto.ts
    │       ├── update-promotion.dto.ts
    │       └── validate-promotion.dto.ts
    │
    ├── flash-sale/
    │   ├── flash-sale.module.ts
    │   ├── flash-sale.controller.ts
    │   ├── flash-sale.service.ts
    │   ├── reservation.service.ts
    │   ├── space-loader.service.ts
    │   ├── lua/
    │   │   ├── reserve-stock.lua
    │   │   └── release-stock.lua
    │   └── dto/
    │       ├── reserve-request.dto.ts
    │       └── reservation-response.dto.ts
    │
    ├── messaging/
    │   ├── messaging.module.ts
    │   ├── kafka-producer.service.ts
    │   └── kafka-consumer.service.ts
    │
    ├── scheduler/
    │   ├── scheduler.module.ts
    │   ├── flash-sale-scheduler.service.ts
    │   ├── reservation-expired-scheduler.service.ts
    │   └── sync-scheduler.service.ts
    │
    ├── outbox/
    │   ├── outbox.module.ts
    │   ├── outbox.service.ts
    │   └── outbox-publisher.scheduler.ts
    │
    └── common/
        ├── guards/
        │   └── roles.guard.ts
        ├── filters/
        │   └── http-exception.filter.ts
        ├── interceptors/
        │   └── response.interceptor.ts
        └── decorators/
            └── user.decorator.ts
```

---

## 8. Database Schema

### 8.1 Prisma schema đề xuất

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

model Promotion {
  id              String          @id @default(uuid())
  code            String          @unique
  name            String
  description     String?
  type            PromotionType
  discountPercent Decimal?        @db.Decimal(5, 2)
  discountAmount  Decimal?        @db.Decimal(12, 2)
  minOrderValue   Decimal?        @db.Decimal(12, 2)
  maxDiscount     Decimal?        @db.Decimal(12, 2)
  usageLimit      Int?
  usageCount      Int             @default(0)
  userLimit       Int             @default(1)
  startTime       DateTime
  endTime         DateTime
  status          PromotionStatus @default(DRAFT)
  createdAt       DateTime        @default(now())
  updatedAt       DateTime        @updatedAt

  flashSale       FlashSale?
  redemptions     PromotionRedemption[]

  @@map("promotions")
}

model FlashSale {
  id              String       @id @default(uuid())
  promotionId     String       @unique
  promotion       Promotion    @relation(fields: [promotionId], references: [id])
  productId       String
  originalPrice   Decimal      @db.Decimal(12, 2)
  salePrice       Decimal      @db.Decimal(12, 2)
  totalStock      Int
  remainingStock  Int
  soldCount       Int          @default(0)
  loadedToSpace   Boolean      @default(false)
  createdAt       DateTime     @default(now())
  updatedAt       DateTime     @updatedAt

  reservations    FlashSaleReservation[]

  @@map("flash_sales")
}

model FlashSaleReservation {
  id            String             @id @default(uuid())
  flashSaleId   String
  flashSale     FlashSale          @relation(fields: [flashSaleId], references: [id])
  userId        String
  orderId       String?
  quantity      Int                @default(1)
  status        ReservationStatus  @default(PENDING)
  expiresAt     DateTime
  confirmedAt   DateTime?
  releasedAt    DateTime?
  createdAt     DateTime           @default(now())
  updatedAt     DateTime           @updatedAt

  @@index([flashSaleId, userId])
  @@index([status, expiresAt])
  @@map("flash_sale_reservations")
}

model PromotionRedemption {
  id          String    @id @default(uuid())
  promotionId String
  promotion   Promotion @relation(fields: [promotionId], references: [id])
  userId      String
  orderId     String
  discount    Decimal   @db.Decimal(12, 2)
  usedAt      DateTime  @default(now())

  @@unique([promotionId, userId, orderId])
  @@map("promotion_redemptions")
}

model OutboxEvent {
  id            String       @id @default(uuid())
  aggregateType String
  aggregateId   String
  eventType     String
  payload       Json
  status        OutboxStatus @default(PENDING)
  retryCount    Int          @default(0)
  createdAt     DateTime     @default(now())
  publishedAt   DateTime?

  @@index([status, createdAt])
  @@map("outbox_events")
}

enum PromotionType {
  FLASH_SALE
  COUPON
  PERCENTAGE
  FIXED_AMOUNT
}

enum PromotionStatus {
  DRAFT
  ACTIVE
  PAUSED
  ENDED
  CANCELLED
}

enum ReservationStatus {
  PENDING
  CONFIRMED
  RELEASED
  EXPIRED
}

enum OutboxStatus {
  PENDING
  PUBLISHED
  FAILED
}
```

---

## 9. Redis Data Model

### 9.1 Flash sale metadata

```text
flashsale:{flashSaleId}:meta
```

Dạng Redis Hash:

```text
status
startTime
endTime
productId
discountPercent
userLimit
totalStock
```

### 9.2 Stock counter

```text
flashsale:{flashSaleId}:stock
```

Ví dụ:

```text
flashsale:FS001:stock = 1000
```

### 9.3 User đang giữ slot

```text
flashsale:{flashSaleId}:reserved_users
```

Dùng để chặn user reserve nhiều lần khi chưa thanh toán.

### 9.4 User đã mua thành công

```text
flashsale:{flashSaleId}:buyers
```

Dùng để lưu user đã thanh toán thành công.

### 9.5 Reservation tạm thời

```text
reservation:{reservationId}
```

Dạng JSON string, có TTL.

Ví dụ:

```json
{
  "reservationId": "res-uuid",
  "flashSaleId": "fs-uuid",
  "userId": "user-uuid",
  "quantity": 1,
  "status": "PENDING"
}
```

### 9.6 Active flash sale index

```text
flashsales:active
```

---

## 10. Nguyên tắc xử lý bắt buộc

### 10.1 Không lưu stock bằng biến local Node.js

Không được làm:

```ts
let remainingStock = 1000;
```

Vì khi chạy nhiều instance, mỗi instance sẽ có một biến riêng, dẫn đến sai số lượng.

### 10.2 Stock chỉ xử lý trong Redis

Stock realtime phải nằm trong Redis:

```text
flashsale:{flashSaleId}:stock
```

### 10.3 Phải dùng Lua script

Không dùng nhiều Redis command rời rạc cho luồng reserve.

Phải dùng Lua script để đảm bảo atomic:

```text
Check status
Check time
Check user
Check stock
DECRBY stock
SET reservation TTL
```

### 10.4 Reservation phải lưu DB ngay sau khi reserve

Không được lưu DB kiểu fire-and-forget.

Sai:

```ts
reservationRepo.create(...).catch(...)
```

Đúng:

```text
1. Redis reserve success
2. Save reservation PENDING to DB
3. Insert outbox event
4. Return response
```

Nếu DB insert fail, phải gọi release script để rollback Redis.

### 10.5 Confirm/release phải idempotent

Payment event có thể bị gửi lặp. Vì vậy confirm/release phải chỉ xử lý khi reservation còn `PENDING`.

---

## 11. Lua Script

### 11.1 reserve-stock.lua

```lua
-- KEYS[1] = flashsale:{id}:meta
-- KEYS[2] = flashsale:{id}:stock
-- KEYS[3] = flashsale:{id}:reserved_users
-- KEYS[4] = flashsale:{id}:buyers
-- KEYS[5] = reservation:{reservationId}

-- ARGV[1] = userId
-- ARGV[2] = reservationId
-- ARGV[3] = reservationJson
-- ARGV[4] = ttlSeconds
-- ARGV[5] = nowEpochSeconds
-- ARGV[6] = quantity

local userId = ARGV[1]
local ttl = tonumber(ARGV[4])
local now = tonumber(ARGV[5])
local quantity = tonumber(ARGV[6])

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'ACTIVE' then
  return redis.error_reply('FLASH_SALE_NOT_ACTIVE')
end

local startTime = tonumber(redis.call('HGET', KEYS[1], 'startTime'))
local endTime = tonumber(redis.call('HGET', KEYS[1], 'endTime'))

if now < startTime or now > endTime then
  return redis.error_reply('FLASH_SALE_NOT_IN_TIME')
end

local alreadyReserved = redis.call('SISMEMBER', KEYS[3], userId)
if alreadyReserved == 1 then
  return redis.error_reply('USER_ALREADY_RESERVED')
end

local alreadyBought = redis.call('SISMEMBER', KEYS[4], userId)
if alreadyBought == 1 then
  return redis.error_reply('USER_ALREADY_PURCHASED')
end

local stock = tonumber(redis.call('GET', KEYS[2]))
if stock == nil or stock < quantity then
  return redis.error_reply('OUT_OF_STOCK')
end

redis.call('DECRBY', KEYS[2], quantity)
redis.call('SADD', KEYS[3], userId)
redis.call('SET', KEYS[5], ARGV[3], 'EX', ttl)

return 'OK'
```

### 11.2 release-stock.lua

```lua
-- KEYS[1] = reservation:{reservationId}
-- KEYS[2] = flashsale:{id}:stock
-- KEYS[3] = flashsale:{id}:reserved_users

-- ARGV[1] = userId
-- ARGV[2] = quantity

local data = redis.call('GET', KEYS[1])
if not data then
  return 'RESERVATION_NOT_FOUND'
end

local quantity = tonumber(ARGV[2])

redis.call('DEL', KEYS[1])
redis.call('INCRBY', KEYS[2], quantity)
redis.call('SREM', KEYS[3], ARGV[1])

return 'OK'
```

---

## 12. Luồng checkout flash sale

### 12.1 Nguyên tắc

**Order Service là service điều phối checkout.**

Promotion Service chỉ xử lý logic promotion/flash sale.

Order Service không tự trừ stock flash sale.

Order Service không tự validate sâu logic flash sale.

### 12.2 Flow chính

```text
1. User checkout sản phẩm flash sale
2. Order Service gọi Promotion Service /reserve
3. Promotion Service kiểm tra flash sale trong Redis
4. Redis atomic trừ stock và tạo reservation
5. Promotion Service lưu reservation PENDING vào DB
6. Promotion Service trả reservationId cho Order Service
7. Order Service tạo order PENDING_PAYMENT
8. Order Service lưu reservationId vào order
9. Payment Service xử lý thanh toán
10. Payment success -> publish payment.payment-completed
11. Promotion Service consume event và confirm reservation
12. Payment failed/timeout -> Promotion Service release reservation
```

---

## 13. API Design

### 13.1 Public API

```http
GET    /api/v1/promotions
GET    /api/v1/promotions/active
GET    /api/v1/promotions/{id}
GET    /api/v1/promotions/validate
POST   /api/v1/promotions/{id}/reserve
```

### 13.2 Admin API

```http
POST   /api/v1/admin/promotions
PUT    /api/v1/admin/promotions/{id}
PATCH  /api/v1/admin/promotions/{id}/activate
PATCH  /api/v1/admin/promotions/{id}/pause
PATCH  /api/v1/admin/promotions/{id}/end
POST   /api/v1/admin/promotions/{id}/load-to-space
GET    /api/v1/admin/promotions
GET    /api/v1/admin/promotions/{id}/stats
```

### 13.3 Internal API

Không public confirm/release cho user.

```http
POST /internal/promotions/reservations/{reservationId}/confirm
POST /internal/promotions/reservations/{reservationId}/release
```

Ưu tiên confirm/release qua Kafka event từ Payment Service.

---

## 14. Reserve API

### 14.1 Request

```http
POST /api/v1/promotions/{id}/reserve
```

```json
{
  "quantity": 1
}
```

User context lấy từ Kong headers:

```text
X-User-Id
X-User-Role
```

### 14.2 Response success

```json
{
  "success": true,
  "data": {
    "reservationId": "res-uuid",
    "flashSaleId": "fs-uuid",
    "productId": "product-uuid",
    "quantity": 1,
    "salePrice": 17500000,
    "discountPercent": 30,
    "expiresAt": "2026-05-26T10:05:00Z",
    "status": "PENDING"
  }
}
```

### 14.3 Response out of stock

```json
{
  "success": false,
  "error": "OUT_OF_STOCK",
  "message": "Flash sale đã hết hàng"
}
```

---

## 15. Confirm / Release

### 15.1 Confirm khi payment success

Promotion Service consume:

```text
payment.payment-completed
```

Sau đó:

```text
1. Check reservation status = PENDING
2. Update DB status = CONFIRMED
3. Delete reservation key trong Redis
4. Remove user khỏi reserved_users
5. Add user vào buyers
6. Publish promotion.reservation.confirmed
```

SQL idempotency:

```sql
UPDATE flash_sale_reservations
SET status = 'CONFIRMED',
    order_id = $1,
    confirmed_at = now()
WHERE id = $2
AND status = 'PENDING';
```

Nếu affected rows = 0 thì bỏ qua.

### 15.2 Release khi payment failed/timeout

Promotion Service consume:

```text
payment.payment-failed
payment.payment-timeout
```

Sau đó:

```text
1. Check reservation status = PENDING
2. Release Redis stock bằng Lua
3. Update DB status = RELEASED
4. Publish promotion.reservation.released
```

SQL idempotency:

```sql
UPDATE flash_sale_reservations
SET status = 'RELEASED',
    released_at = now()
WHERE id = $1
AND status = 'PENDING';
```

Nếu affected rows = 0 thì bỏ qua.

---

## 16. Reservation hết hạn

### 16.1 Không phụ thuộc chính vào Redis expired key

Redis key hết hạn thì dữ liệu JSON trong key đã mất. Vì vậy không nên dùng Redis expired event làm nguồn chính để hoàn stock.

### 16.2 Cách xử lý chính

Dùng scheduler quét DB:

```sql
SELECT *
FROM flash_sale_reservations
WHERE status = 'PENDING'
AND expires_at < now();
```

Sau đó với từng reservation:

```text
1. Release stock trong Redis
2. Remove user khỏi reserved_users
3. Update DB status = EXPIRED
4. Publish promotion.reservation.released
```

### 16.3 Redis Keyspace Notifications

Có thể bật thêm để tối ưu realtime, nhưng chỉ nên xem là phụ.

```text
DB Scheduler = main safety mechanism
Redis Keyspace Notifications = optional optimization
```

---

## 17. Kafka Events

### 17.1 Promotion Service publish

| Topic | Khi nào | Consumer |
|---|---|---|
| promotion.reservation.created | Sau khi reserve thành công | Order Service hoặc audit |
| promotion.reservation.confirmed | Sau khi payment thành công | Order, Inventory |
| promotion.reservation.released | Sau khi release/expired | Order |
| promotion.flash-sale.started | Flash sale bắt đầu | Notification |
| promotion.flash-sale.ended | Flash sale kết thúc | Notification |
| promotion.stock.low | Stock còn thấp | Notification/Admin |

### 17.2 Promotion Service consume

| Topic | Từ service | Hành động |
|---|---|---|
| payment.payment-completed | Payment Service | Confirm reservation |
| payment.payment-failed | Payment Service | Release reservation |
| payment.payment-timeout | Payment Service | Release reservation |

### 17.3 Payment success payload

```json
{
  "eventType": "PAYMENT_COMPLETED",
  "data": {
    "orderId": "order-uuid",
    "reservationId": "reservation-uuid",
    "userId": "user-uuid"
  }
}
```

### 17.4 Payment failed payload

```json
{
  "eventType": "PAYMENT_FAILED",
  "data": {
    "orderId": "order-uuid",
    "reservationId": "reservation-uuid",
    "userId": "user-uuid"
  }
}
```

---

## 18. Outbox Pattern

### 18.1 Lý do cần Outbox

Nếu reserve thành công và DB đã lưu reservation nhưng publish Kafka thất bại, hệ thống có thể bị mất event.

Vì vậy nên dùng Outbox Pattern:

```text
1. Reserve Redis success
2. DB transaction:
   - Insert reservation
   - Insert outbox event
3. Outbox scheduler publish Kafka
4. Mark event = PUBLISHED
```

### 18.2 Outbox event ví dụ

```json
{
  "aggregateType": "FLASH_SALE_RESERVATION",
  "aggregateId": "reservation-uuid",
  "eventType": "PROMOTION_RESERVATION_CREATED",
  "payload": {
    "reservationId": "reservation-uuid",
    "flashSaleId": "flashsale-uuid",
    "userId": "user-uuid",
    "quantity": 1
  }
}
```

---

## 19. Kong Gateway

### 19.1 Route promotion service

```yaml
- name: promotion-service
  url: http://promotion-service:8093
  connect_timeout: 10000
  read_timeout: 30000
  write_timeout: 10000
  retries: 2
  routes:
    - name: promotion-user-route
      paths:
        - /api/v1/promotions
      strip_path: false
      methods:
        - GET
        - POST

    - name: promotion-admin-route
      paths:
        - /api/v1/admin/promotions
      strip_path: false
      methods:
        - GET
        - POST
        - PUT
        - PATCH
        - DELETE

    - name: promotion-internal-route
      paths:
        - /internal/promotions
      strip_path: false
      methods:
        - POST
```

### 19.2 Header security

Kong phải:

```text
1. Verify JWT
2. Remove X-User-Id, X-User-Role từ request gốc
3. Inject lại X-User-Id, X-User-Role sau khi verify token
```

Nếu không làm bước này, user có thể tự fake `X-User-Id`.

---

## 20. Dockerfile

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npx prisma generate
RUN npm run build

FROM node:20-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/prisma ./prisma
COPY package*.json ./

USER appuser

EXPOSE 8093

CMD ["node", "dist/main.js"]
```

---

## 21. Docker Compose

```yaml
promotion-service:
  image: nexatech/promotion-service:latest
  container_name: nexatech-promotion-service
  build:
    context: ./promotion-service
    dockerfile: Dockerfile
  environment:
    NODE_ENV: production
    PORT: 8093
    DATABASE_URL: postgresql://nexatech:nexatech123@postgres:5432/nexatech_promotions
    REDIS_HOST: redis
    REDIS_PORT: 6379
    KAFKA_BROKERS: kafka:19092
    KAFKA_GROUP_ID: promotion-service-group
    RESERVATION_TTL_SECONDS: 300
  expose:
    - "8093"
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    kafka:
      condition: service_healthy
  networks:
    - nexatech-infra-net
  healthcheck:
    test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8093/health"]
    interval: 10s
    timeout: 5s
    retries: 5
```

Nếu muốn test trực tiếp không qua Kong:

```yaml
ports:
  - "8093:8093"
```

Production nên ưu tiên gọi qua Kong, không cần public port trực tiếp ra ngoài.

---

## 22. Environment Example

### 22.1 Local

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

### 22.2 Docker

```env
NODE_ENV=production
PORT=8093

DATABASE_URL=postgresql://nexatech:nexatech123@postgres:5432/nexatech_promotions

REDIS_HOST=redis
REDIS_PORT=6379

KAFKA_BROKERS=kafka:19092
KAFKA_GROUP_ID=promotion-service-group

RESERVATION_TTL_SECONDS=300
```

---

## 23. Tích hợp với Order Service

Order Service cần bổ sung flow:

```text
1. Khi user checkout flash sale
2. Order Service gọi Promotion Service /reserve
3. Nhận reservationId
4. Tạo order PENDING_PAYMENT
5. Lưu reservationId vào order
6. Gọi Payment Service
```

Order cần lưu thêm:

```text
promotionId
flashSaleId
reservationId
discountAmount
finalPrice
```

Order Service không xử lý logic flash sale.

---

## 24. Tích hợp với Payment Service

Payment Service publish event sau khi xử lý thanh toán.

### Payment success

```text
Topic: payment.payment-completed
```

```json
{
  "eventType": "PAYMENT_COMPLETED",
  "data": {
    "orderId": "order-uuid",
    "reservationId": "reservation-uuid",
    "userId": "user-uuid"
  }
}
```

### Payment failed

```text
Topic: payment.payment-failed
```

```json
{
  "eventType": "PAYMENT_FAILED",
  "data": {
    "orderId": "order-uuid",
    "reservationId": "reservation-uuid",
    "userId": "user-uuid"
  }
}
```

### Payment timeout

```text
Topic: payment.payment-timeout
```

```json
{
  "eventType": "PAYMENT_TIMEOUT",
  "data": {
    "orderId": "order-uuid",
    "reservationId": "reservation-uuid",
    "userId": "user-uuid"
  }
}
```

---

## 25. Scheduler

### 25.1 FlashSaleScheduler

Tự động:

```text
DRAFT -> ACTIVE khi đến startTime
ACTIVE -> ENDED khi quá endTime
```

Đồng thời publish event:

```text
promotion.flash-sale.started
promotion.flash-sale.ended
```

### 25.2 ReservationExpiredScheduler

Chạy mỗi 30 giây hoặc 60 giây:

```text
1. Query DB reservation PENDING đã hết hạn
2. Release Redis stock
3. Update status = EXPIRED
4. Publish promotion.reservation.released
```

### 25.3 SyncScheduler

Chạy mỗi 5 phút:

```text
1. Lấy stock hiện tại từ Redis
2. Sync remainingStock về PostgreSQL
3. Update soldCount nếu cần
```

---

## 26. Load dữ liệu vào Redis

### 26.1 Khi nào load?

Có thể load khi:

```text
Admin bấm load-to-space
Hoặc scheduler tự load trước khi flash sale bắt đầu
```

### 26.2 Logic load

```text
1. Lấy FlashSale + Promotion từ DB
2. HSET metadata
3. SETNX stock để tránh reset stock đang chạy
4. SADD flashsales:active
5. Update loadedToSpace = true
```

### 26.3 Lưu ý

Không dùng `SET` stock nếu flash sale đang chạy vì có thể reset stock về số ban đầu.

Nên dùng:

```text
SETNX flashsale:{id}:stock remainingStock
```

---

## 27. Kiểm thử

### 27.1 Test bình thường

```text
1. Admin tạo flash sale stock = 100
2. Load flash sale vào Redis
3. Activate flash sale
4. Gửi 100 request reserve đồng thời
5. Kiểm tra Redis stock = 0
6. Request thứ 101 phải trả OUT_OF_STOCK
```

### 27.2 Test overselling

```text
Stock = 10
Gửi 100 request đồng thời
Kết quả:
- Chỉ 10 request thành công
- 90 request OUT_OF_STOCK
- Redis stock không được âm
```

### 27.3 Test payment success

```text
1. Reserve thành công
2. Payment Service publish payment.payment-completed
3. Reservation chuyển CONFIRMED
4. User chuyển từ reserved_users sang buyers
5. Stock không hoàn lại
```

### 27.4 Test payment failed

```text
1. Reserve thành công
2. Payment Service publish payment.payment-failed
3. Reservation chuyển RELEASED
4. Stock được INCRBY lại
5. User bị remove khỏi reserved_users
```

### 27.5 Test reservation expired

```text
1. Reserve thành công
2. Không thanh toán trong 5 phút
3. Scheduler phát hiện expiresAt < now
4. Stock được hoàn lại
5. Reservation chuyển EXPIRED
```

### 27.6 Test duplicate event

```text
Gửi payment.payment-completed 2 lần
Kết quả:
- Chỉ confirm 1 lần
- Không phát sinh sai stock
```

---

## 28. Rủi ro và giải pháp

| Rủi ro | Giải pháp |
|---|---|
| Redis down khi flash sale chạy | Trả lỗi tạm thời, không fallback sang DB để tránh overselling |
| Overselling | Lua script atomic với DECRBY |
| DB insert fail sau Redis reserve | Gọi release script để rollback Redis |
| Kafka publish fail | Dùng Outbox Pattern |
| Payment event bị gửi lặp | Confirm/release idempotent |
| Reservation expired nhưng Redis key đã mất data | Scheduler dựa trên DB là chính |
| User fake X-User-Id | Kong strip header gốc và inject lại sau JWT verify |
| Port 3000 trùng frontend | Dùng port 8093 |
| Node.js restart | Redis và PostgreSQL giữ trạng thái, service phục hồi được |

---

## 29. Phase triển khai

### Phase 1 — Init service

- [ ] Tạo thư mục `promotion-service/`
- [ ] Init NestJS project
- [ ] Đổi port mặc định sang `8093`
- [ ] Cài Prisma, ioredis, kafkajs
- [ ] Tạo `/health`
- [ ] Viết Dockerfile
- [ ] Thêm service vào docker-compose

### Phase 2 — Database

- [ ] Tạo database `nexatech_promotions`
- [ ] Viết Prisma schema
- [ ] Tạo migration
- [ ] Implement repositories:
  - [ ] PromotionRepository
  - [ ] FlashSaleRepository
  - [ ] ReservationRepository
  - [ ] OutboxRepository

### Phase 3 — Redis Space Layer

- [ ] Cấu hình ioredis
- [ ] Viết Redis key constants
- [ ] Implement SpaceLoaderService
- [ ] Load metadata vào Redis
- [ ] Load stock bằng SETNX
- [ ] Load active flash sale index

### Phase 4 — Core Flash Sale Logic

- [ ] Viết `reserve-stock.lua`
- [ ] Viết `release-stock.lua`
- [ ] Load Lua script bằng EVALSHA
- [ ] Implement `reserveSlot()`
- [ ] Implement `confirmReservation()`
- [ ] Implement `releaseReservation()`
- [ ] Thêm rollback Redis nếu DB save fail

### Phase 5 — REST API

- [ ] Promotion CRUD cho admin
- [ ] Activate/pause/end
- [ ] Load-to-space
- [ ] Reserve API cho user
- [ ] Validate API
- [ ] Stats API cho admin
- [ ] Global exception filter
- [ ] Response wrapper

### Phase 6 — Kafka

- [ ] KafkaProducerService
- [ ] KafkaConsumerService
- [ ] Consume payment completed/failed/timeout
- [ ] Publish reservation confirmed/released
- [ ] Implement Outbox Publisher

### Phase 7 — Scheduler

- [ ] FlashSaleScheduler
- [ ] ReservationExpiredScheduler
- [ ] SyncScheduler
- [ ] Test expired reservation

### Phase 8 — Kong Integration

- [ ] Thêm route promotion-service:8093
- [ ] Verify JWT
- [ ] Inject user headers
- [ ] Chặn internal API khỏi public
- [ ] Test qua Kong port 8000

### Phase 9 — Integration với Order/Payment

- [ ] Order Service gọi Promotion `/reserve`
- [ ] Order lưu `reservationId`
- [ ] Payment publish success/failed/timeout event
- [ ] Promotion consume và xử lý confirm/release

### Phase 10 — Hardening

- [ ] Rate limit `/reserve`
- [ ] Structured logging
- [ ] Retry Kafka
- [ ] Dead-letter topic nếu cần
- [ ] Load test bằng k6 hoặc Artillery
- [ ] Review security

---

## 30. Checklist tổng kết

- [x] Promotion Service dùng Node.js/NestJS
- [x] Port dùng `8093`, không dùng `3000`
- [x] Redis làm Space Layer
- [x] PostgreSQL lưu dữ liệu bền vững
- [x] Kafka xử lý event payment
- [x] Lua script xử lý atomic reserve/release
- [x] Dùng `DECRBY` và `INCRBY` theo quantity
- [x] Không lưu stock trong biến local Node.js
- [x] Reservation lưu DB ngay sau reserve
- [x] Nếu DB save fail thì rollback Redis
- [x] Scheduler xử lý reservation expired dựa trên DB
- [x] Confirm/release idempotent
- [x] Kong route trực tiếp đến `promotion-service:8093`
- [x] Order Service điều phối checkout
- [x] Payment Service gửi event để confirm/release
- [x] Confirm/release không public cho user
- [x] Có Outbox Pattern để tránh mất Kafka event

---

## 31. Kết luận

Promotion / Flash Sale Service trong NexaTech nên được triển khai theo hướng:

```text
Node.js / NestJS
+ Port 8093
+ Redis Space Layer
+ PostgreSQL
+ Kafka
+ Kong routing
```

Đây là service phù hợp nhất để áp dụng **Space-Based Architecture** vì flash sale có lượng request tăng đột biến, yêu cầu xử lý nhanh, tránh overselling và giảm tải trực tiếp cho database.

Các service còn lại như Order, Payment, Inventory vẫn tiếp tục dùng Spring Boot. Promotion Service giao tiếp với các service đó qua REST API và Kafka event, đảm bảo hệ thống vẫn nhất quán, dễ scale và dễ triển khai trong kiến trúc microservices của NexaTech.
