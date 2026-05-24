# Nexatech Admin Frontend API

Tài liệu này mô tả các API backend cần cho frontend admin sau khi hoàn thành Phase 1-5 của luồng quản lý `product`, `category`, `brand`, `review`, `inventory`.

## Base URL

- Gateway prefix: `/api/v1`
- Các endpoint dưới đây được gọi qua API Gateway.

## Auth Headers

- `Authorization: Bearer <token>`
- `X-User-Id`: Gateway inject từ JWT.
- `X-User-Role`: Gateway inject từ JWT.
- `X-User-Email`: Gateway inject từ JWT.

Frontend không cần tự set `X-User-*` khi request đi qua Gateway đúng chuẩn auth hiện tại.

## Response Wrapper

Hầu hết API trả về dạng:

```json
{
  "success": true,
  "data": {},
  "message": null,
  "timestamp": "2026-05-24T14:00:00Z"
}
```

Với API phân trang, `data` có dạng:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6,
  "last": false
}
```

## Pagination And Sorting

- `page`: zero-based.
- `size`: số phần tử mỗi trang.
- `sort`: Spring format, ví dụ `sort=createdAt,desc` hoặc `sort=name,asc`.

Ví dụ:

```text
GET /api/v1/products?page=0&size=20&sort=updatedAt,desc
```

## Soft Delete Rules

- `DELETE` hiện là soft delete cho `product`, `category`, `brand`.
- `PATCH .../restore` sẽ bật lại `active=true`.
- Mặc định list API chỉ trả record active.
- Để xem deleted items, dùng `active=false` hoặc `includeDeleted=true` tùy endpoint.

---

## 1. Product APIs

### 1.1 List Products For Admin

- `GET /api/v1/products`

### Query Params

- `keyword`: tìm theo `name`, `sku`, `description`.
- `categoryId`
- `categorySlug`
- `brandId`
- `sku`
- `minPrice`
- `maxPrice`
- `minRating`
- `maxRating`
- `featured`
- `active`: `true` hoặc `false`.
- `includeDeleted`: `true` để lấy cả active và deleted.
- `specs.<key>=<value>`: filter theo specs.
- `page`, `size`, `sort`

### Allowed Sort Fields

- `price`
- `name`
- `createdAt`
- `updatedAt`
- `sku`

### Example

```text
GET /api/v1/products?keyword=iphone&active=true&page=0&size=20&sort=updatedAt,desc
```

### Product Response Fields

```json
{
  "id": "6650e3d3c16d7e38e6f7a001",
  "name": "iPhone 15 Pro",
  "slug": "iphone-15-pro",
  "description": "...",
  "descriptionHtml": "<p>...</p>",
  "price": 29990000,
  "compareAtPrice": 31990000,
  "sku": "IP15PRO-256-BLK",
  "thumbnail": "https://...",
  "primaryCategoryId": "6650e3...",
  "primaryCategorySlug": "smartphones",
  "primaryCategoryName": "Smartphones",
  "categoryIds": ["6650e3..."],
  "categorySlugs": ["smartphones"],
  "brandId": "6650e4...",
  "brandName": "Apple",
  "brandSlug": "apple",
  "images": ["https://..."],
  "specs": {
    "ram": "8GB",
    "storage": "256GB"
  },
  "tags": ["flagship", "ios"],
  "metadata": {
    "seoTitle": "..."
  },
  "active": true,
  "averageRating": 4.7,
  "stockQuantity": 25,
  "stockStatus": "IN_STOCK",
  "createdAt": "2026-05-24T14:00:00Z",
  "updatedAt": "2026-05-24T14:10:00Z"
}
```

### 1.2 Get Product Detail

- `GET /api/v1/products/{id}`

### 1.3 Create Product

- `POST /api/v1/products`

### Request Body

```json
{
  "name": "iPhone 15 Pro",
  "description": "Short description",
  "descriptionHtml": "<p>Short description</p>",
  "price": 29990000,
  "compareAtPrice": 31990000,
  "sku": "IP15PRO-256-BLK",
  "thumbnail": "https://.../thumb.jpg",
  "primaryCategoryId": "6650e3d3c16d7e38e6f7a100",
  "primaryCategorySlug": "smartphones",
  "categoryIds": ["6650e3d3c16d7e38e6f7a100"],
  "categorySlugs": ["smartphones"],
  "brandId": "6650e3d3c16d7e38e6f7a200",
  "images": [
    "https://.../1.jpg",
    "https://.../2.jpg"
  ],
  "specs": {
    "ram": "8GB",
    "storage": "256GB"
  },
  "tags": ["flagship", "ios"],
  "metadata": {
    "seoTitle": "iPhone 15 Pro"
  }
}
```

### 1.4 Update Product

- `PUT /api/v1/products/{id}`
- Body giống create.

### 1.5 Soft Delete Product

- `DELETE /api/v1/products/{id}`

### 1.6 Restore Product

- `PATCH /api/v1/products/{id}/restore`

### 1.7 Product Image Upload

- `POST /api/v1/products/{id}/images`
- Content-Type: `multipart/form-data`
- Form field: `files`
- Có thể upload nhiều file trong một request.

### 1.8 Delete Product Image

- `DELETE /api/v1/products/{id}/images`

```json
{
  "imageUrl": "https://.../1.jpg"
}
```

### 1.9 Reorder Product Images

- `PUT /api/v1/products/{id}/images/reorder`

```json
{
  "imageUrls": [
    "https://.../2.jpg",
    "https://.../1.jpg"
  ]
}
```

---

## 2. Category APIs

### 2.1 List Categories For Admin

- `GET /api/v1/categories`

### Query Params

- `active=true|false`
- `includeDeleted=true|false`

### Response Fields

```json
{
  "id": "6650e3d3c16d7e38e6f7a100",
  "name": "Smartphones",
  "slug": "smartphones",
  "description": "...",
  "parentId": null,
  "sortOrder": 1,
  "active": true,
  "filter": {
    "brandSlug": "apple",
    "filterableSpecs": ["ram", "storage"],
    "defaultPriceRange": {
      "min": 10000000,
      "max": 50000000
    }
  },
  "metadata": {
    "seoTitle": "..."
  }
}
```

### 2.2 Create Category

- `POST /api/v1/categories`

```json
{
  "name": "Smartphones",
  "description": "...",
  "parentId": null,
  "sortOrder": 1,
  "filter": {
    "brandSlug": "apple",
    "filterableSpecs": ["ram", "storage"],
    "defaultPriceRange": {
      "min": 10000000,
      "max": 50000000
    }
  },
  "metadata": {
    "seoTitle": "Smartphones"
  }
}
```

### 2.3 Update Category

- `PUT /api/v1/categories/{id}`

### 2.4 Delete Category

- `DELETE /api/v1/categories/{id}`

Delete sẽ fail nếu category còn subcategories active hoặc còn active products.

### 2.5 Restore Category

- `PATCH /api/v1/categories/{id}/restore`

Restore sẽ fail nếu `parentId` đang trỏ tới category đã inactive hoặc không tồn tại.

---

## 3. Brand APIs

### 3.1 List Brands For Admin

- `GET /api/v1/brands`

### Query Params

- `active=true|false`
- `includeDeleted=true|false`

### Response Fields

```json
{
  "id": "6650e3d3c16d7e38e6f7a200",
  "name": "Apple",
  "slug": "apple",
  "parentBrandId": null,
  "logoUrl": "https://.../apple.png",
  "active": true,
  "createdAt": "2026-05-24T14:00:00Z",
  "updatedAt": "2026-05-24T14:10:00Z"
}
```

### 3.2 Create Brand

- `POST /api/v1/brands`

```json
{
  "name": "Apple",
  "parentBrandId": null,
  "logoUrl": "https://.../apple.png"
}
```

### 3.3 Update Brand

- `PUT /api/v1/brands/{id}`

### 3.4 Delete Brand

- `DELETE /api/v1/brands/{id}`

Delete sẽ fail nếu brand còn active products.

### 3.5 Restore Brand

- `PATCH /api/v1/brands/{id}/restore`

---

## 4. Review APIs For Admin

### 4.1 List Reviews

- `GET /api/v1/reviews`

### Query Params

- `productId`
- `rating`
- `userId`
- `from`: ISO-8601 timestamp.
- `to`: ISO-8601 timestamp.
- `page`, `size`, `sort`

### Allowed Sort Fields

- `createdAt`
- `rating`
- `helpfulCount`

### Example

```text
GET /api/v1/reviews?rating=5&page=0&size=20&sort=createdAt,desc
```

### Review Response

```json
{
  "id": "6650f1d3c16d7e38e6f7b100",
  "productId": "6650e3d3c16d7e38e6f7a001",
  "productName": "iPhone 15 Pro",
  "productSlug": "iphone-15-pro",
  "productThumbnail": "https://.../thumb.jpg",
  "userId": "de305d54-75b4-431b-adb2-eb6b9e546014",
  "rating": 5,
  "title": "Great product",
  "content": "Very good camera and battery.",
  "images": ["https://.../review-1.jpg"],
  "helpfulCount": 3,
  "createdAt": "2026-05-24T14:00:00Z"
}
```

### 4.2 Delete Review

- `DELETE /api/v1/reviews/{id}`
- Backend hiện cho phép owner hoặc admin xóa; frontend admin có thể dùng endpoint này trực tiếp.

---

## 5. Inventory APIs For Admin/Warehouse

### 5.1 List Inventory

- `GET /api/v1/inventory/list`

### Query Params

- `sku`
- `status`: `IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK`
- `keyword`: match theo `sku`, `productName`, `productId`, `warehouseLocation`
- `warehouseLocation`
- `page`, `size`, `sort`

### Allowed Sort Fields

- `productId`
- `productName`
- `sku`
- `quantity`
- `reservedQuantity`
- `availableQuantity`
- `reorderLevel`
- `status`
- `warehouseLocation`

### Inventory Response

```json
{
  "id": "1d2c4d08-35f6-4f1d-b313-f6939f8b08c9",
  "productId": "6650e3d3c16d7e38e6f7a001",
  "productName": "iPhone 15 Pro",
  "productSlug": "iphone-15-pro",
  "productThumbnail": "https://.../thumb.jpg",
  "sku": "IP15PRO-256-BLK",
  "quantity": 40,
  "reservedQuantity": 5,
  "availableQuantity": 35,
  "reorderLevel": 10,
  "status": "IN_STOCK",
  "warehouseLocation": "WH-A1"
}
```

### 5.2 Get Inventory Detail By Product

- `GET /api/v1/inventory/{productId}`

### 5.3 Update Stock

- `PUT /api/v1/inventory`

```json
{
  "productId": "6650e3d3c16d7e38e6f7a001",
  "sku": "IP15PRO-256-BLK",
  "quantity": 40,
  "reason": "Initial sync from admin"
}
```

### Notes

- `sku` là optional khi inventory row đã tồn tại.
- Khi tạo inventory row lần đầu, backend sẽ dùng `sku` từ request hoặc thử lấy từ `product-service`.
- Nếu cả hai đều không có, request sẽ fail.

### 5.4 Update Warehouse Info

- `PATCH /api/v1/inventory/{productId}/warehouse`

```json
{
  "warehouseLocation": "WH-A1",
  "reorderLevel": 10
}
```

### 5.5 Get Low Stock Items

- `GET /api/v1/inventory/low-stock`

### 5.6 Stock Movement History

- `GET /api/v1/inventory/{productId}/movements`

### Query Params

- `page`, `size`, `sort`

### Allowed Sort Fields

- `createdAt`
- `type`
- `quantity`

### Movement Response

```json
{
  "id": "25e63eaf-c52a-4c32-81be-9cc89e8f05c1",
  "productId": "6650e3d3c16d7e38e6f7a001",
  "type": "ADJUSTMENT",
  "quantity": 20,
  "reference": "MANUAL_UPDATE",
  "reason": "Initial stock load",
  "performedBy": null,
  "createdAt": "2026-05-24T14:30:00Z"
}
```

### 5.7 Bulk Import By JSON

- `POST /api/v1/inventory/bulk-import`

```json
[
  {
    "productId": "6650e3d3c16d7e38e6f7a001",
    "sku": "IP15PRO-256-BLK",
    "quantity": 40,
    "reason": "bulk import"
  }
]
```

### Bulk Import Response

```json
{
  "successCount": 1,
  "failureCount": 0,
  "errors": []
}
```

### 5.8 CSV Import

- `POST /api/v1/inventory/import`
- Content-Type: `multipart/form-data`
- Form field: `file`

### Expected CSV Format

```csv
sku,quantity,warehouse
IP15PRO-256-BLK,40,WH-A1
```

### CSV Import Response

```json
{
  "successCount": 1,
  "failureCount": 0,
  "totalLines": 1,
  "errorLines": []
}
```

### CSV Error Line Example

```json
{
  "lineNumber": 2,
  "sku": "UNKNOWN-SKU",
  "reason": "SKU not found in inventory"
}
```

---

## 6. Public Product Review APIs Still Used By Storefront

Các API này vẫn tồn tại để storefront dùng, frontend admin thường không cần gọi:

- `POST /api/v1/products/{productId}/reviews`
- `GET /api/v1/products/{productId}/reviews`

---

## 7. Common Error Cases

### Soft Delete / Restore

- Restore category fail nếu parent category inactive hoặc không tồn tại.
- Delete category fail nếu còn subcategory active.
- Delete category fail nếu còn product active.
- Delete brand fail nếu còn product active.

### Product / Inventory

- Update stock fail nếu `quantity < reservedQuantity`.
- Create inventory fail nếu không resolve được `sku`.
- CSV import fail từng dòng nếu format sai hoặc `sku` không tồn tại trong inventory.

### Review

- Review list fail nếu `from > to`.
- Delete review fail nếu caller không phải owner hoặc admin.

---

## 8. Frontend Implementation Notes

- Product admin table nên dùng `GET /api/v1/products` với `active` hoặc `includeDeleted` để chuyển tab active/deleted.
- Category và brand admin tabs nên dùng `GET /api/v1/categories` và `GET /api/v1/brands` với `includeDeleted=true` khi cần render trạng thái restore.
- Review admin table có thể render trực tiếp `productName`, `productThumbnail`, `rating`, `createdAt` từ `GET /api/v1/reviews` mà không cần join thêm.
- Inventory table nên lấy từ `GET /api/v1/inventory/list`; không cần gọi thêm product API để lấy tên, slug, thumbnail.
- Movement timeline nên lấy từ `GET /api/v1/inventory/{productId}/movements`.
- Khi upload ảnh sản phẩm, nên upload xong rồi cập nhật `thumbnail` hoặc reorder nếu muốn thay ảnh đại diện.
