# discovery-server

Eureka registry for service registration and discovery.

## Service Graph

```mermaid
flowchart LR
  A[discovery-server]
  B[api-gateway]
  C[config-server]
  D[identity-service]
  E[user-service]
  F[product-service]
  G[order-service]
  H[inventory-service]
  I[payment-service]
  J[shipping-service]
  K[notification-service]
  L[chat-service]
  M[storage-service]
  N[news-service]
  O[audit-service]
  P[agent-service]
  Q[mcp-server]
  R[recommendation-service]

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
  O --> A
  P --> A
  Q --> A
  R --> A
```

## Related Services

- [api-gateway](../api-gateway/README.md)
- [config-server](../config-server/README.md)
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
- [agent-service](../python-services/agent-service/README.md)
- [mcp-server](../python-services/mcp-server/README.md)
- [recommendation-service](../python-services/recommendation-service/README.md)
