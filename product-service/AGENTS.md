# AGENTS.md

## Product Service ID Contract (Mandatory)

- All model IDs in `product-service` MUST be stored and processed as `String`.
- Never use `ObjectId` type conversion, fallback queries, or mixed `String/ObjectId` matching logic.
- `_id` fields and foreign keys (`productId`, `categoryId`, `brandId`, arrays like `categoryIds`) MUST be handled as plain strings.
- New queries, handlers, services, and migrations MUST use string-based criteria only.
- Any new model ID annotation MUST use `@MongoId(FieldType.STRING)`.
- Any code review/change touching ID logic must preserve this string-only contract.

## CQRS Update Rule

- For `product-service` CQRS handlers and related services, ID normalization may trim whitespace only.
- Do not lowercase, parse, or reinterpret IDs as MongoDB ObjectId.
