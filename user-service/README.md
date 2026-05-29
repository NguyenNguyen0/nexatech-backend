# user-service

User profile, address, role, permission, and group management service.

## Service Graph

```mermaid
flowchart LR
  A[user-service]
  B[identity-service]
  C[postgres]
  D[kafka]
  E[keycloak]
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

- IAM calls: [identity-service](../identity-service/README.md)
- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, Kafka, Keycloak
