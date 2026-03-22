Task received: auditing product/inventory services against the requested feature list.
# Plan checklist:
- Inspect product-service service layer to confirm Product/Category behaviors
- Verify advanced Product queries, repository support, and Mongo text search config
- Review inventory-service core service behaviors for stock operations
- Check InventoryEventConsumer.handleOrderEvent and related Kafka handling
## Product + Category Services (0%)
- product-service/src/main/java/com/nexatech/product/service/ProductService.java still throws UnsupportedOperationException for every method; no repository/mapper injections, no Mongo writes, and no event publication (create/update/get/delete, pagination, search) implemented.
- product-service/src/main/java/com/nexatech/product/service/CategoryService.java likewise has no logic; category CRUD, duplicate-name validation, or ResourceNotFoundException handling are absent.
- Controllers in product-service/src/main/java/com/nexatech/product/controller/ProductController.java depend on the unimplemented service, so nothing functions yet.
## Advanced Product Queries (10%)
- Repository scaffolding exists (product-service/src/main/java/com/nexatech/product/repository/ProductRepository.java already exposes findByCategoryId, findByNameContainingIgnoreCase, etc.), and controllers expose the endpoints.
- However, service methods (getProducts, getProductsByCategory, searchProducts) still throw, Product lacks @TextIndexed fields, and MongoConfig has no text-index setup—so pagination/search pipeline is effectively 0% functional despite partial plumbing.
## Inventory Service Operations (0%)
- inventory-service/src/main/java/com/nexatech/inventory/service/InventoryService.java leaves all operations unimplemented, so stock lookup/update/reserve/release flows, movement logging, and low-stock detection are missing entirely.
- Repositories/models (InventoryItemRepository, StockMovementRepository, StockMovement.MovementType) exist but aren’t used; there’s no ADJUSTMENT/RESERVE/RELEASE logic nor low-stock alert emission.
## Inventory Event Handling (0%)
- inventory-service/src/main/java/com/nexatech/inventory/event/InventoryEventConsumer.java only logs raw strings. It doesn’t deserialize OrderEvent, branch on ORDER_CANCELLED or ORDER_DELIVERED, call stock operations, emit SALE movements, or publish low-stock alerts via InventoryEventProducer.
- Additional gaps: no configured Kafka deserializer bean for the event payload, and no logic to emit inventory.events when thresholds breach.
Next steps: implement the missing service logic (CRUD, validation, pagination, search, text indexing), flesh out inventory stock workflows plus event handling, then back them with integration/unit tests to guard the new behaviors.