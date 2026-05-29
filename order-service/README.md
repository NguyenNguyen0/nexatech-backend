# order-service

Order, cart, checkout, and return orchestration service.

## Service Graph

```mermaid
flowchart LR
  A[order-service]
  B[product-service]
  C[inventory-service]
  D[payment-service]
  E[postgres]
  F[redis]
  G[kafka]
  H[config-server]
  I[discovery-server]

  A --> B
  A --> C
  A --> D
  A --> E
  A --> F
  A --> G
  A --> H
  A --> I
```

## Dependencies

- Synchronous orchestration: [product-service](../product-service/README.md), [inventory-service](../inventory-service/README.md), [payment-service](../payment-service/README.md)
- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, Redis, Kafka
