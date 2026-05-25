from __future__ import annotations

import logging

from app.clients.product_service_client import ProductServiceClient


logger = logging.getLogger("recommendation-service.product-enrichment")


class ProductRecommendationEnricher:
    def __init__(self, product_client: ProductServiceClient) -> None:
        self._product_client = product_client

    async def enrich_ranked_items(self, items: list[dict], id_key: str = "productId") -> list[dict]:
        if not items:
            return []

        unique_ids: list[str] = []
        seen: set[str] = set()
        for item in items:
            product_id = str(item.get(id_key) or "").strip()
            if not product_id or product_id in seen:
                continue
            seen.add(product_id)
            unique_ids.append(product_id)

        try:
            products_by_id = await self._product_client.fetch_products_by_ids(unique_ids)
        except Exception as exc:  # pragma: no cover
            logger.warning("Product enrichment lookup failed, fallback to rank-only payload: %s", exc)
            products_by_id = {}

        enriched: list[dict] = []
        for item in items:
            item_copy = dict(item)
            product_id = str(item_copy.get(id_key) or "").strip()
            item_copy["product"] = products_by_id.get(product_id)
            enriched.append(item_copy)

        return enriched
