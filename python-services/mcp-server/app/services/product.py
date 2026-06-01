from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


PRODUCT_LIST_PARAM_KEYS = {
    "keyword",
    "categoryId",
    "categorySlug",
    "brandId",
    "sku",
    "minPrice",
    "maxPrice",
    "minRating",
    "maxRating",
    "featured",
    "active",
    "includeDeleted",
    "page",
    "size",
    "sort",
}

PRODUCT_PAGE_PARAM_KEYS = {
    "page",
    "size",
    "sort",
}

PRODUCT_CATEGORY_PARAM_KEYS = {
    "brandId",
    "minPrice",
    "maxPrice",
    "minRating",
    "maxRating",
    "featured",
    "page",
    "size",
    "sort",
}

PRODUCT_SEARCH_PARAM_KEYS = PRODUCT_CATEGORY_PARAM_KEYS | {
    "categoryId",
    "categorySlug",
}

REVIEW_ADMIN_PARAM_KEYS = {
    "productId",
    "rating",
    "userId",
    "from",
    "to",
    "page",
    "size",
    "sort",
}

REVIEW_PAGE_PARAM_KEYS = {
    "rating",
    "page",
    "size",
    "sort",
}


class ProductServiceApi(BaseServiceApi):
    async def list_products(self, params: dict[str, object] | None = None) -> object:
        return await self.client.request(
            "GET",
            "/api/v1/products",
            params=_clean_params(params, PRODUCT_LIST_PARAM_KEYS, allow_specs=True),
        )

    async def search_products(
        self,
        keyword: str,
        params: dict[str, object] | None = None,
    ) -> object:
        query_params = {
            **_clean_params(params, PRODUCT_SEARCH_PARAM_KEYS, allow_specs=True),
            "keyword": keyword,
        }
        return await self.client.request("GET", "/api/v1/products/search", params=query_params)

    async def get_product_by_id(self, product_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/products/{product_id}")

    async def get_product_by_slug(self, slug: str) -> object:
        return await self.client.request("GET", f"/api/v1/products/slug/{slug}")

    async def list_featured_products(self, params: dict[str, object] | None = None) -> object:
        return await self.client.request(
            "GET",
            "/api/v1/products/featured",
            params=_clean_params(params, PRODUCT_PAGE_PARAM_KEYS),
        )

    async def list_products_by_tags(
        self,
        tags: list[str],
        params: dict[str, object] | None = None,
    ) -> object:
        query_params = {**_clean_params(params, PRODUCT_PAGE_PARAM_KEYS), "tags": tags}
        return await self.client.request("GET", "/api/v1/products/tags", params=query_params)

    async def list_products_by_category(
        self,
        category_id: str,
        params: dict[str, object] | None = None,
    ) -> object:
        return await self.client.request(
            "GET",
            f"/api/v1/products/category/{category_id}",
            params=_clean_params(params, PRODUCT_PAGE_PARAM_KEYS),
        )

    async def list_products_by_category_slug(
        self,
        slug: str,
        params: dict[str, object] | None = None,
    ) -> object:
        return await self.client.request(
            "GET",
            f"/api/v1/products/category/slug/{slug}",
            params=_clean_params(params, PRODUCT_CATEGORY_PARAM_KEYS, allow_specs=True),
        )

    async def get_related_products(self, product_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/products/{product_id}/related")

    async def get_related_products_by_slug(self, slug: str) -> object:
        return await self.client.request("GET", f"/api/v1/products/slug/{slug}/related")

    async def create_product(self, payload: dict[str, object]) -> object:
        return await self.client.request("POST", "/api/v1/products", json=payload)

    async def update_product(self, product_id: str, payload: dict[str, object]) -> object:
        return await self.client.request("PUT", f"/api/v1/products/{product_id}", json=payload)

    async def delete_product(self, product_id: str) -> object:
        return await self.client.request("DELETE", f"/api/v1/products/{product_id}")

    async def restore_product(self, product_id: str) -> object:
        return await self.client.request("PATCH", f"/api/v1/products/{product_id}/restore")

    async def list_reviews_by_product(
        self,
        product_id: str,
        params: dict[str, object] | None = None,
    ) -> object:
        return await self.client.request(
            "GET",
            f"/api/v1/products/{product_id}/reviews",
            params=_clean_params(params, REVIEW_PAGE_PARAM_KEYS),
        )

    async def list_reviews_by_product_slug(
        self,
        slug: str,
        params: dict[str, object] | None = None,
    ) -> object:
        return await self.client.request(
            "GET",
            f"/api/v1/products/slug/{slug}/reviews",
            params=_clean_params(params, REVIEW_PAGE_PARAM_KEYS),
        )

    async def get_my_review_for_product(self, product_id: str) -> object:
        return await self.client.request("GET", f"/api/v1/products/{product_id}/reviews/mine")

    async def create_review(self, product_id: str, payload: dict[str, object]) -> object:
        return await self.client.request("POST", f"/api/v1/products/{product_id}/reviews", json=payload)

    async def list_reviews(self, params: dict[str, object] | None = None) -> object:
        return await self.client.request(
            "GET",
            "/api/v1/reviews",
            params=_clean_params(params, REVIEW_ADMIN_PARAM_KEYS),
        )

    async def update_review(self, review_id: str, payload: dict[str, object]) -> object:
        return await self.client.request("PUT", f"/api/v1/reviews/{review_id}", json=payload)

    async def delete_review(self, review_id: str) -> object:
        return await self.client.request("DELETE", f"/api/v1/reviews/{review_id}")

    async def mark_review_helpful(self, review_id: str) -> object:
        return await self.client.request("POST", f"/api/v1/reviews/{review_id}/helpful")


def _clean_params(
    params: dict[str, object] | None,
    allowed_keys: set[str],
    *,
    allow_specs: bool = False,
) -> dict[str, object]:
    if not params:
        return {}

    cleaned: dict[str, object] = {}
    for key, value in params.items():
        if value is None or value == "":
            continue
        if key in allowed_keys or (allow_specs and key.startswith("specs.")):
            cleaned[key] = value
    return cleaned


PRODUCT_API = ProductServiceApi(
    ServiceClient(
        ServiceDefinition(
            name="product-service",
            base_url=SETTINGS.product_service_base_url,
            health_path=SETTINGS.product_service_health_path,
            timeout_seconds=SETTINGS.service_request_timeout_seconds,
            retry_attempts=SETTINGS.service_retry_attempts,
            retry_backoff_seconds=SETTINGS.service_retry_backoff_seconds,
            retry_max_backoff_seconds=SETTINGS.service_retry_max_backoff_seconds,
        )
    )
)
