# product-service

Product catalog service (products, categories, brands, reviews, wishlist).

## Service Graph

```mermaid
flowchart LR
  A[product-service]
  B[storage-service]
  C[order-service]
  D[inventory-service]
  E[mongodb]
  F[kafka]
  G[config-server]
  H[discovery-server]

  A --> B
  A --> C
  A --> D
  A --> E
  A --> F
  A --> G
  A --> H
```

## Dependencies

- Internal file APIs: [storage-service](../storage-service/README.md)
- Purchase validation / stock checks: [order-service](../order-service/README.md), [inventory-service](../inventory-service/README.md)
- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: MongoDB, Kafka
