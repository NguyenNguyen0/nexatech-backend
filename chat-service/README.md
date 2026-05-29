# chat-service

Chat and conversation service (REST + WebSocket).

## Service Graph

```mermaid
flowchart LR
  A[chat-service]
  B[user-service]
  C[postgres]
  D[mongodb]
  E[redis]
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

- User profile integration: [user-service](../user-service/README.md)
- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, MongoDB, Redis, Kafka
