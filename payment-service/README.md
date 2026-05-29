# payment-service

Payment lifecycle service (including VNPay integration).

## Service Graph

```mermaid
flowchart LR
  A[payment-service]
  B[postgres]
  C[kafka]
  D[config-server]
  E[discovery-server]
  F[vnpay]

  A --> B
  A --> C
  A --> D
  A --> E
  A --> F
```

## Dependencies

- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, Kafka
- External provider: VNPay
