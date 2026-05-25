# Frontend Integration Guide: Recommendation + Behavior Tracking

This guide describes how frontend should integrate with recommendation and product services after the latest backend updates.

## 1) What is ready now

- Recommendation APIs return ranked items and, by default, enriched product metadata for UI rendering.
- Product internal catalog now uses `ProductMinimalResponse` for recommendation sync.
- Product suggest route `/api/v1/products/suggest` was removed.
- Recommendation service remains ML-safe: content/collaborative training still reads the same catalog fields needed for feature building (`name`, `description`, categories, brand, specs, price, active).

## 2) Recommendation APIs for frontend

Base URL (through gateway/Kong): your deployed recommendation route.
Local direct URL (service): `http://localhost:8095`.

### 2.1 Get user recommendations

`GET /recommend/user/{userId}?top_k=10&enrich=true`

Sample response:

```json
{
  "userId": "u-123",
  "topK": 10,
  "source": "hybrid",
  "recommendations": [
    {
      "productId": "665f0e3cb9d9f63d40a7d111",
      "score": 0.9123,
      "product": {
        "id": "665f0e3cb9d9f63d40a7d111",
        "name": "iPhone 15 Pro",
        "slug": "iphone-15-pro",
        "price": 27990000,
        "thumbnail": "...",
        "brandName": "Apple",
        "primaryCategoryName": "Phones",
        "averageRating": 4.8,
        "reviewCount": 154
      }
    }
  ]
}
```

### 2.2 Get similar products

`GET /recommend/similar/{productId}?top_k=10&enrich=true`

### 2.3 Get popular fallback

`GET /recommend/popular?top_k=10&enrich=true`

### 2.4 Optional raw ranking mode

Set `enrich=false` to return rank-only payload (no embedded `product`).
Use this when frontend already has a product cache and wants to reduce response size.

## 3) Frontend rendering contract

For each recommendation item:

- Use `score` for sorting/weighting only (do not display as user-facing rating).
- Render from `product` when available.
- If `product` is null (product deleted/inactive/out-of-sync), skip rendering that card.

Recommended fallback priority:

1. `recommend/user`
2. `recommend/similar` (for PDP)
3. `recommend/popular`

## 4) Behavior events to improve recommendations

Recommendation service consumes behavior from Kafka topic `user-behavior-events`.
The accepted event shape is:

```json
{
  "eventId": "optional-uuid-or-hash",
  "userId": "u-123",
  "productId": "665f0e3cb9d9f63d40a7d111",
  "eventType": "VIEW",
  "weight": 1.0,
  "rating": 0,
  "timestamp": "2026-05-25T15:24:11.000Z",
  "categoryIds": ["phone"],
  "brandId": "apple",
  "price": 27990000
}
```

Supported `eventType` values with default weights:

- `VIEW`: 1.0
- `SEARCH_CLICK`: 1.5
- `CART_ADD`: 3.0
- `PURCHASE`: 5.0
- `RATE`: derived from rating (clamped to 1..5)

## 5) Behavior ingestion endpoint (implemented)

Backend now provides behavior ingestion API in recommendation-service:

- Service route: `POST /behaviors`
- Gateway route: `POST /api/v1/behaviors`

Requirements:

- `X-User-Id` header must be forwarded by gateway/edge layer.
- Idempotency is enforced via Redis key `behavior:dedup:{eventId}` with configurable TTL.
- Valid events are published to Kafka topic `user-behavior-events`.

Current remaining dependency:

- Ensure your gateway chain always injects `X-User-Id` from validated JWT claims before forwarding to recommendation-service.

## 6) Backend readiness checklist

Verified in this update:

- Product service compiles successfully after API changes.
- Recommendation service tests pass.
- Recommendation APIs support enriched product payload.
- Internal product sync contract switched to `ProductMinimalResponse` while preserving ML features.
- Behavior ingestion endpoint is available with Redis idempotency + Kafka publishing.

## 7) Frontend quick-start flow

1. On home page load: call `GET /recommend/popular?top_k=10&enrich=true`.
2. On authenticated sessions: call `GET /recommend/user/{userId}?top_k=10&enrich=true`.
3. On product detail page: call `GET /recommend/similar/{productId}?top_k=10&enrich=true`.
4. Track interactions (view/click/cart/purchase/rate) and send to `POST /api/v1/behaviors`.
