import logging

from app.clients.product_service_client import ProductServiceClient
from app.repositories.mongo_repo import MongoRepository


logger = logging.getLogger("recommendation-service.product-catalog-sync")


class ProductCatalogSync:
    def __init__(self, mongo_repo: MongoRepository, product_client: ProductServiceClient, page_size: int = 200) -> None:
        self._mongo_repo = mongo_repo
        self._product_client = product_client
        self._page_size = page_size

    async def initial_sync_if_empty(self) -> None:
        current_count = await self._mongo_repo.count_product_catalog()
        if current_count > 0:
            logger.info("Skip initial sync, product catalog already has %s items", current_count)
            return

        logger.info("Starting initial product catalog sync")
        page = 0
        total = 0
        while True:
            items, is_last = await self._product_client.fetch_products_page(page=page, size=self._page_size)
            await self._mongo_repo.upsert_product_catalog_items(items)
            total += len(items)

            if is_last or not items:
                break
            page += 1

        logger.info("Initial product catalog sync completed, total=%s", total)

    async def apply_event(self, event: dict) -> None:
        await self._mongo_repo.apply_product_event(event)
