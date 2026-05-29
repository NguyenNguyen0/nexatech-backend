# news-service

News and editorial content service.

## Service Graph

```mermaid
flowchart LR
  A[news-service]
  B[postgres]
  C[redis]
  D[minio-s3]
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

- Shared platform: [config-server](../config-server/README.md), [discovery-server](../discovery-server/README.md)
- Infra: PostgreSQL, Redis, MinIO/S3, Kafka
