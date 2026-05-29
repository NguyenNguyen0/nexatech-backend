# identity-service

Authentication and IAM facade integrated with Keycloak.

## Service Graph

```mermaid
flowchart LR
  A[identity-service]
  B[user-service]
  C[keycloak]
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

- User profile sync: [user-service](../user-service/README.md)
- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: Keycloak, Redis, Kafka
