# Nexatech API Documentation

## 1. API Convention Review

### Public APIs
Tất cả các public API đều được truy cập thông qua API Gateway với prefix `/api/v1`.

- **Thống nhất:** Đã thống nhất prefix `/api/v1` cho toàn bộ hệ thống ở tầng Gateway.
- **Tính nhất quán trong Codebase:**
    - **Inconsistent:** Có sự không đồng nhất trong cách triển khai giữa các service.
    - **Nhóm 1 (Identity, User):** Định nghĩa route trực tiếp (ví dụ: `/users`, `/auth`) và dựa vào Gateway `StripPrefix=2` để khớp với `/api/v1`.
    - **Nhóm 2 (Product, Order, Payment, Inventory, Notification, Storage):** Định nghĩa route bao gồm cả prefix `/api/v1` ngay trong `@RequestMapping` của Controller.
- **Khuyến nghị:** Nên thống nhất một cách tiếp cận (ưu tiên đưa `/api/v1` vào cấu hình Gateway và giữ code sạch hoặc ngược lại) để tránh nhầm lẫn khi phát triển.

### Internal APIs
- Các API nội bộ sử dụng prefix `/internal` và không được expose qua Gateway (ví dụ: `storage-service`, `user-service`, `identity-service`).
- `storage-service` tuân thủ đúng ngoại lệ: `/internal/files/upload` và `/internal/files`.

---

## 2. Service Routes

### Identity Service
- `POST /api/v1/auth/register`: Đăng ký người dùng mới.
- `POST /api/v1/auth/login`: Đăng nhập.
- `POST /api/v1/auth/refresh`: Refresh token.
- `POST /api/v1/auth/logout`: Đăng xuất.
- `GET /api/v1/auth/openid-configuration`: Lấy cấu hình OpenID.
- `GET /api/v1/auth/userinfo`: Lấy thông tin người dùng từ token.
- `POST /api/v1/auth/introspect`: Kiểm tra token.
- `GET/POST/PUT/DELETE /api/v1/users/**`: Quản lý người dùng (Keycloak).
- `GET/POST/DELETE /api/v1/groups/**`: Quản lý nhóm.
- `GET/POST/DELETE /api/v1/roles/**`: Quản lý role.

### User Service
- `GET/POST/PUT/DELETE /api/v1/users/**`: Thông tin chi tiết người dùng và địa chỉ.
- `GET/POST/DELETE /api/v1/permissions/**`: Quản lý phân quyền.
- `GET/POST/PUT/DELETE /api/v1/groups/**`: Quản lý group người dùng.
- `GET/POST/PUT/DELETE /api/v1/roles/**`: Quản lý role nội bộ.
- `GET/POST/PUT/DELETE /api/v1/users/me/addresses/**`: Quản lý địa chỉ cá nhân.

### Product Service
- `GET /api/v1/products`: Danh sách sản phẩm (phân trang).
- `GET /api/v1/products/{id}`: Chi tiết sản phẩm (bao gồm `averageRating`).
- `GET /api/v1/products/search?keyword=`: Tìm kiếm sản phẩm (full-text + fallback regex).
- `GET /api/v1/products/category/{categoryId}`: Lọc sản phẩm theo danh mục.
- `POST /api/v1/products`: Tạo sản phẩm.
- `PUT /api/v1/products/{id}`: Cập nhật sản phẩm.
- `DELETE /api/v1/products/{id}`: Xóa mềm sản phẩm.
- `GET/POST/PUT/DELETE /api/v1/categories/**`: Quản lý danh mục.
- `GET/POST/PUT/DELETE /api/v1/brands/**`: Quản lý thương hiệu.
- `POST /api/v1/products/{id}/images`: Upload hình ảnh sản phẩm (multipart, tối đa 10 ảnh).
- `DELETE /api/v1/products/{id}/images`: Xóa hình ảnh sản phẩm.
- `PUT /api/v1/products/{id}/images/reorder`: Sắp xếp lại thứ tự ảnh.
- `POST /api/v1/products/{productId}/reviews`: Tạo đánh giá sản phẩm (cần `X-User-Id`, 1 review/user/product).
- `GET /api/v1/products/{productId}/reviews`: Danh sách đánh giá đã duyệt (phân trang, public).
- `GET /api/v1/products/{productId}/reviews/all`: Tất cả đánh giá bao gồm chưa duyệt (ADMIN, cần `X-User-Role`).
- `PATCH /api/v1/products/{productId}/reviews/{reviewId}/approve`: Duyệt đánh giá (ADMIN).
- `DELETE /api/v1/products/{productId}/reviews/{reviewId}`: Xóa đánh giá (ADMIN).

### Order Service
- `GET/POST /api/v1/orders/**`: Quản lý đơn hàng (User & Admin).
- `POST /api/v1/checkout`: Quy trình checkout đơn hàng.
- `GET/POST/PUT/DELETE /api/v1/cart/**`: Quản lý giỏ hàng.
- `GET/POST /api/v1/returns/**`: Quản lý yêu cầu trả hàng.

### Payment Service
- `GET/POST /api/v1/payments/**`: Quản lý thanh toán.
- `POST /api/v1/payments/vnpay/create`: Tạo thanh toán VNPay.
- `GET /api/v1/payments/vnpay/ipn`: Xử lý IPN từ VNPay.
- `GET /api/v1/payments/vnpay/return`: Xử lý Return URL từ VNPay.

### Inventory Service
- `GET /api/v1/inventory/{productId}`: Kiểm tra tồn kho sản phẩm.
- `GET /api/v1/inventory/list`: Danh sách tồn kho (phân trang, lọc theo `warehouseLocation`).
- `GET /api/v1/inventory/low-stock`: Sản phẩm tồn kho thấp.
- `PUT /api/v1/inventory`: Cập nhật tồn kho.
- `POST /api/v1/inventory/check`: Kiểm tra số lượng khả dụng.
- `POST /api/v1/inventory/reserve`: Giữ hàng.
- `POST /api/v1/inventory/release`: Giải phóng hàng đã giữ.
- `POST /api/v1/inventory/bulk-import`: Import tồn kho hàng loạt (partial success).
- `PATCH /api/v1/inventory/{productId}/warehouse`: Cập nhật vị trí kho và reorder level.

### Storage Service
- `GET /api/v1/files/{bucket}/{key}`: Truy xuất file (Public).
- `POST /internal/files/upload`: Upload file (Internal).
- `DELETE /internal/files`: Xóa file (Internal).

---

## 3. Kafka Event Schemas

### Common Header (BaseEvent)
```json
{
  "eventId": "UUID",
  "eventType": "String",
  "timestamp": "ISO-8601 Timestamp"
}
```

### Order Events (Topic: `order.events`)
- **Published by:** `order-service`
- **Schema:**
```json
{
  "orderId": "String",
  "orderNumber": "String",
  "customerId": "String",
  "customerEmail": "String",
  "status": "String (PENDING, CANCELLED, DELIVERED)",
  "totalAmount": "BigDecimal"
}
```

### Payment Events (Topic: `payment.events`)
- **Published by:** `payment-service`
- **Schema:**
```json
{
  "paymentId": "String",
  "orderId": "String",
  "customerId": "String",
  "customerEmail": "String",
  "amount": "BigDecimal",
  "status": "String (COMPLETED, REFUNDED, FAILED)"
}
```

### Inventory Events (Topic: `inventory.events`)
- **Published by:** `inventory-service`
- **Schema:**
```json
{
  "productId": "String",
  "productName": "String",
  "quantity": "Integer",
  "reorderLevel": "Integer",
  "warehouseLocation": "String"
}
```

### User Events (Topic: `user.events`)
- **Published by:** `identity-service`
- **Schema (UserRegisteredEvent):**
```json
{
  "userId": "String",
  "email": "String",
  "displayName": "String"
}
```

### Product Catalog Events (Topic: `product.events`)
- **Published by:** `product-service`
- **Schema:**
```json
{
  "eventId": "UUID",
  "eventType": "String (PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED)",
  "timestamp": "ISO-8601 Timestamp",
  "productId": "String",
  "name": "String",
  "categoryId": "String",
  "active": "Boolean"
}
```

### Review Events (Topic: `review.events`)
- **Published by:** `product-service` (khi admin approve review)
- **Consumed by:** `notification-service`
- **Schema:**
```json
{
  "eventId": "UUID",
  "eventType": "REVIEW_APPROVED",
  "timestamp": "ISO-8601 Timestamp",
  "reviewId": "String",
  "productId": "String",
  "userId": "UUID",
  "rating": "Integer (1-5)"
}
```

---

## 4. API Gateway

### Header Relay
API Gateway tự động extract claims từ JWT (Keycloak) và inject vào request headers gửi đến downstream services:

| Header | Source | Description |
|---|---|---|
| `X-User-Id` | JWT `sub` claim | UUID người dùng (Keycloak credential ID) |
| `X-User-Role` | JWT `realm_access.roles` | Role đầu tiên (non-default), fallback `USER` |
| `X-User-Email` | JWT `email` claim | Email người dùng |

### Public Routes (không cần JWT)
- `/actuator/**`
- `/api/v1/auth/**`

### Gateway Routes

| Route ID | Target Service | Predicates |
|---|---|---|
| `identity-auth` | `identity-service` | `/api/v1/auth/**` (StripPrefix=2) |
| `user-service` | `user-service` | `/api/v1/users/**`, `/api/v1/groups/**`, `/api/v1/roles/**`, `/api/v1/permissions/**` (StripPrefix=2) |
| `product-service` | `product-service` | `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/brands/**` |
| `order-service` | `order-service` | `/api/v1/orders/**`, `/api/v1/admin/orders/**`, `/api/v1/returns/**` |
| `inventory-service` | `inventory-service` | `/api/v1/inventory/**` |
| `payment-service` | `payment-service` | `/api/v1/payments/**` |
| `notification-service` | `notification-service` | `/api/v1/notifications/**` |
| `storage-service` | `storage-service` | `/api/v1/files/**` |
