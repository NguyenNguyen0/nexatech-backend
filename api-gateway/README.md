# api-gateway

Spring Cloud Gateway edge service for routing client traffic to backend services.

## Service Graph

```mermaid
flowchart LR
  A[api-gateway]
  B[identity-service]
  C[user-service]
  D[product-service]
  E[order-service]
  F[inventory-service]
  G[payment-service]
  H[shipping-service]
  I[notification-service]
  J[chat-service]
  K[storage-service]
  L[news-service]
  M[promotion-service]
  N[audit-service]
  O[agent-service]
  P[mcp-server]
  Q[recommendation-service]
  R[keycloak]
  S[discovery-server]

  A --> B
  A --> C
  A --> D
  A --> E
  A --> F
  A --> G
  A --> H
  A --> I
  A --> J
  A --> K
  A --> L
  A --> M
  A --> N
  A --> O
  A --> P
  A --> Q
  A --> R
  A --> S
```

## Dependencies

- Service discovery: [discovery-server](../discovery-server/README.md)
- Auth provider: Keycloak (infra)
- Routed services:
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
  - [promotion-service](../promotion-service/README.md)
  - [audit-service](../audit-service/README.md)
  - [agent-service](../python-services/agent-service/README.md)
  - [mcp-server](../python-services/mcp-server/README.md)
  - [recommendation-service](../python-services/recommendation-service/README.md)
