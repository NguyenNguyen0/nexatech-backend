# shipping-service

Shipping quote and shipment management service.

## Service Graph

```mermaid
flowchart LR
  A[shipping-service]
  B[postgres]
  C[kafka]
  D[config-server]
  E[discovery-server]
  F[ghn-or-mock]

  A --> B
  A --> C
  A --> D
  A --> E
  A --> F
```

## Dependencies

- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, Kafka
- External provider: GHN or mock shipping adapter
