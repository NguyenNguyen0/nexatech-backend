# Audit Service

Centralized audit log của hệ thống NexaTech. Ghi nhận mọi hành động nghiệp vụ quan trọng từ các service khác (Order, Payment, User, Product…) thông qua Kafka và cung cấp API tra cứu cho khu vực Admin.

| Hạng mục | Giá trị |
|---|---|
| Port nội bộ | `8096` |
| Database | MongoDB — collection `audit_logs` (DB `nexatech_audit`) |
| Kafka topic | `audit-topic` (cấu hình qua `AUDIT_LOG_TOPIC`) |
| Consumer group | `audit-service-group` |
| Gateway path | `/api/v1/admin/audit/**` (Kong & Spring Cloud Gateway) |
| Quyền truy cập | Keycloak realm role `AUDIT_READ` (deny: `AUDIT_READ_DENY`) |

---

## 1. Cấu trúc thư mục

```text
src/main/java/com/nexatech/audit
├── AuditServiceApplication.java
├── config/
│   ├── common/JacksonConfig.java
│   └── security/                       # SecurityConfig + JWT converter
├── constant/                           # PermissionsAccept / PermissionsDeny
├── controller/AuditController.java     # @RequestMapping("/audit")
├── dto/
│   ├── base/                           # ApiResponse, PageResponse
│   ├── event/AuditLogEvent.java        # Envelope Kafka
│   └── response/AuditLogResponse.java
├── event/AuditLogConsumer.java         # Kafka listener
├── exception/                          # AppException + ErrorCode + handler
├── model/AuditLog.java                 # @Document(collection = "audit_logs")
├── repository/AuditLogRepository.java
├── security/                           # @RequiresPermission, Aspect
└── service/
    ├── AuditLogService.java
    └── impl/AuditLogServiceImpl.java
```

---

## 2. Schema bản ghi audit

Document `audit_logs` (MongoDB):

| Trường | Kiểu | Mô tả |
|---|---|---|
| `_id` | String | UUID tự sinh |
| `action` | String, indexed | Tên hành động: `ORDER_CREATED`, `PAYMENT_REFUNDED`… |
| `entity_type` | String | Loại entity: `Order`, `Payment`, `User`… |
| `entity_id` | String | ID entity bị tác động |
| `old_value` | String | Snapshot trước thay đổi (JSON / text) |
| `new_value` | String | Snapshot sau thay đổi |
| `actor` | String, indexed | `userId` / email / `system` |
| `reason` | String | Lý do/ngữ cảnh |
| `source_service` | String, indexed | Service phát sinh, ví dụ `order-service` |
| `message` | String | Thông điệp human-readable |
| `metadata` | Object | Tự do: `traceId`, IP, userAgent… |
| `timestamp` | Instant, indexed | Thời điểm sự kiện ở producer |
| `created_at` | Instant | Thời điểm audit-service persist |

Compound index:

- `idx_entity` — `(entity_type, entity_id)`
- `idx_action_timestamp` — `(action, timestamp DESC)`

---

## 3. Envelope chuẩn cho producer

Mọi service nghiệp vụ publish lên Kafka topic `audit-topic` theo schema sau (JSON, value-serializer `StringSerializer`):

```json
{
  "action": "ORDER_CREATED",
  "entityType": "Order",
  "entityId": "9d1f...",
  "actor": "user@example.com",
  "sourceService": "order-service",
  "oldValue": null,
  "newValue": "{\"status\":\"NEW\"}",
  "reason": "Khách đặt qua web",
  "message": "Tạo đơn #001",
  "metadata": { "traceId": "abc-123" },
  "timestamp": 1716700000000
}
```

Bắt buộc: `action`, `entityType`, `entityId`. Tất cả các field khác đều tuỳ chọn. Nếu thiếu `timestamp`, audit-service dùng `Instant.now()`.

Consumer xử lý cả trường hợp payload bị bọc nháy kép (double-encoded JSON).

---

## 4. REST API

> Base path nội bộ: `/audit` — qua Kong/Gateway: `/api/v1/admin/audit`.

### `GET /audit` — tìm kiếm có phân trang

Tham số (tất cả tuỳ chọn):

| Param | Mặc định | Ghi chú |
|---|---|---|
| `action` | — | Bằng tuyệt đối |
| `entityType` | — | Bằng tuyệt đối |
| `entityId` | — | Bằng tuyệt đối |
| `actor` | — | Bằng tuyệt đối |
| `sourceService` | — | Bằng tuyệt đối |
| `from` | — | ISO-8601 Instant, lọc `timestamp >= from` |
| `to` | — | ISO-8601 Instant, lọc `timestamp <= to` |
| `page` | `1` | 1-indexed |
| `size` | `20` | Tối đa 20 (set trong `spring.data.web.pageable.max-page-size`) |
| `sortBy` | `timestamp` | Tên field MongoDB |
| `sortDir` | `desc` | `asc` / `desc` |

Ví dụ:

```bash
curl -H "Authorization: Bearer <jwt>" \
  "http://localhost:8000/api/v1/admin/audit?entityType=Order&action=ORDER_CREATED&page=1&size=10"
```

Response:

```json
{
  "code": 1000,
  "message": "Success",
  "result": {
    "content": [
      {
        "id": "65f...",
        "action": "ORDER_CREATED",
        "entityType": "Order",
        "entityId": "9d1f...",
        "actor": "user@example.com",
        "sourceService": "order-service",
        "timestamp": "2026-05-26T05:00:00Z",
        "createdAt": "2026-05-26T05:00:00.123Z"
      }
    ],
    "page": 1,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasMore": false
  }
}
```

### `GET /audit/{id}` — chi tiết theo id

Lỗi `404` nếu không tồn tại (ErrorCode `3001 — AUDIT_LOG_NOT_FOUND`).

---

## 5. Bảo mật

- `SecurityConfig` cấu hình OAuth2 resource server JWT (issuer Keycloak realm `nexatech`).
- Mọi endpoint `/audit/**` yêu cầu JWT hợp lệ.
- Annotation `@RequiresPermission(PermissionsAccept.Audit.READ)` enforce realm role `AUDIT_READ` thông qua `PermissionAuthorizationAspect`.
- Quy ước realm role kế thừa `EffectiveRealmAuthoritiesResolver`: nếu user có cả `AUDIT_READ` và `AUDIT_READ_DENY`, deny thắng.

Public endpoints: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/internal/**`.

---

## 6. Cấu hình môi trường

File `.env` (local) — copy từ `.env.example`:

```text
CONFIG_SERVER_URI=http://localhost:8888
EUREKA_INSTANCE_HOSTNAME=localhost
EUREKA_ENABLED=true
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka

MONGO_AUDIT_URL=mongodb://nexatech:nexatech123@localhost:27017/nexatech_audit?authSource=admin&authMechanism=SCRAM-SHA-256

KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=audit-service-group
AUDIT_LOG_TOPIC=audit-topic

KEYCLOAK_ISSUER_URI=http://localhost:9000/realms/nexatech
```

Cấu hình runtime đặt tại `config-server/src/main/resources/configs/audit-service.yml` (Mongo, Kafka, Security, Pagination, Springdoc, Logging).

---

## 7. Chạy local

```bash
make dev-up          # mongo + kafka + keycloak (docker-compose.infra.yml)
make run-discovery   # eureka :8761
make run-config      # config-server :8888
make run-gateway     # api-gateway :8080 (nếu test qua Spring Gateway)

# audit-service:
mvn -pl audit-service spring-boot:run
# hoặc dùng VS Code launch profile "audit-service"
```

Truy cập:

- Trực tiếp: `http://localhost:8096/audit`
- Qua Kong: `http://localhost:8000/api/v1/admin/audit`
- Qua Spring Gateway: `http://localhost:8080/api/v1/admin/audit`

---

## 8. Tích hợp từ service khác (producer)

Mỗi service nghiệp vụ cần:

1. Khai báo topic & producer cấu hình trong `application.yml` (`spring.kafka.producer.value-serializer: StringSerializer`).
2. Inject `KafkaTemplate<String, String>` và publish JSON envelope.

Snippet gợi ý:

```java
@Service
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic.audit-log:audit-topic}")
    private String auditTopic;

    public void publish(String action, String entityType, String entityId, String actor) {
        var event = Map.of(
                "action", action,
                "entityType", entityType,
                "entityId", entityId,
                "actor", actor,
                "sourceService", "order-service",
                "timestamp", System.currentTimeMillis()
        );
        try {
            kafkaTemplate.send(auditTopic, objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            // audit không được phá business — chỉ log warn
        }
    }
}
```

---

## 9. Lỗi & mã trạng thái

`ErrorCode` (xem `exception/ErrorCode.java`):

| Code | HTTP | Khi nào |
|---|---|---|
| 1001 | 401 | JWT không hợp lệ / thiếu |
| 1002 | 403 | Thiếu `AUDIT_READ` (hoặc bị deny) |
| 1003 | 400 | Validation lỗi không khớp ErrorCode nào |
| 1004 | 400 | JSON body sai cú pháp |
| 1005 / 1006 | 400 | Tham số page/sort không hợp lệ |
| 3001 | 404 | `GET /audit/{id}` không tồn tại |
| 3002 / 3003 / 3004 | 400 | Payload Kafka thiếu field bắt buộc |
| 9999 | 500 | Lỗi không phân loại |

Response chuẩn:

```json
{ "code": 3001, "message": "Audit log not found" }
```

---

## 10. Mở rộng

- Thêm endpoint export CSV/Excel: dùng quyền `AUDIT_EXPORT`.
- Thêm TTL index trên `timestamp` để tự động xoá log cũ (tuỳ chính sách retention).
- Thêm Outbox Pattern phía producer để tránh mất event khi Kafka down.
- Bật tracing (Spring Cloud Sleuth + Jaeger) — `application.yml` chung đã set `management.tracing.sampling.probability: 1.0`.
