# inventory-service

Inventory and stock reservation service.

## Service Graph

```mermaid
flowchart LR
  A[inventory-service]
  B[product-service]
  C[postgres]
  D[redis]
  E[kafka]
  F[config-server]
  G[discovery-server]

  A --> B
  A --> C
  A --> D
  A --> E
  A --> F
  A --> G
```

## Dependencies

- Product metadata lookup: [product-service](../product-service/README.md)
- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, Redis, Kafka
