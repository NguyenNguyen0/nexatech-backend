# notification-service

Notification service for email/in-app delivery and event-driven notifications.

## Service Graph

```mermaid
flowchart LR
  A[notification-service]
  B[postgres]
  C[kafka]
  D[config-server]
  E[discovery-server]

  A --> B
  A --> C
  A --> D
  A --> E
```

## Dependencies

- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, Kafka
