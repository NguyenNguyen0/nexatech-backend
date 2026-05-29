# config-server

Spring Cloud Config Server using native config files under `src/main/resources/configs`.

## Service Graph

```mermaid
flowchart LR
  A[config-server]
  B[api-gateway]
  C[identity-service]
  D[user-service]
  E[product-service]
  F[order-service]
  G[inventory-service]
  H[payment-service]
  I[shipping-service]
  J[notification-service]
  K[chat-service]
  L[storage-service]
  M[news-service]
  N[audit-service]
  O[discovery-server]

  B --> A
  C --> A
  D --> A
  E --> A
  F --> A
  G --> A
  H --> A
  I --> A
  J --> A
  K --> A
  L --> A
  M --> A
  N --> A
  A --> O
```

## Dependencies

- Optional registry registration: [discovery-server](../discovery-server/README.md)
- Config consumers:
  - [api-gateway](../api-gateway/README.md)
  - [identity-service](../identity-service/README.md)
  - [user-service](../user-service/README.md)
  - [product-service](../product-service/README.md)
  - [order-service](../order-service/README.md)
  - [inventory-service](../inventory-service/README.md)
  - [payment-service](../payment-service/README.md)
  - [shipping-service](../shipping-service/README.md)
  - [notification-service](../notification-service/README.md)
  - [chat-service](../chat-service/README.md)
  - [storage-service](../storage-service/README.md)
  - [news-service](../news-service/README.md)
  - [audit-service](../audit-service/README.md)
