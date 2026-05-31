from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class FlexibleModel(BaseModel):
    model_config = ConfigDict(
        extra="allow",
        populate_by_name=True,
    )


class PagingParams(FlexibleModel):
    page: int | None = Field(default=None, ge=0)
    size: int | None = Field(default=None, ge=1, le=100)
    sort: str | None = None


class InventoryListParams(PagingParams):
    sku: str | None = None
    status: str | None = None
    keyword: str | None = None
    warehouse_location: str | None = Field(default=None, alias="warehouseLocation")


class ProductListParams(PagingParams):
    keyword: str | None = None
    category_id: str | None = Field(default=None, alias="categoryId")
    category_slug: str | None = Field(default=None, alias="categorySlug")
    brand_id: str | None = Field(default=None, alias="brandId")
    sku: str | None = None
    min_price: float | None = Field(default=None, alias="minPrice", ge=0)
    max_price: float | None = Field(default=None, alias="maxPrice", ge=0)
    min_rating: float | None = Field(default=None, alias="minRating", ge=0, le=5)
    max_rating: float | None = Field(default=None, alias="maxRating", ge=0, le=5)
    featured: bool | None = None
    active: bool | None = None
    include_deleted: bool | None = Field(default=None, alias="includeDeleted")


class ProductSearchParams(PagingParams):
    category_id: str | None = Field(default=None, alias="categoryId")
    category_slug: str | None = Field(default=None, alias="categorySlug")
    brand_id: str | None = Field(default=None, alias="brandId")
    min_price: float | None = Field(default=None, alias="minPrice", ge=0)
    max_price: float | None = Field(default=None, alias="maxPrice", ge=0)
    min_rating: float | None = Field(default=None, alias="minRating", ge=0, le=5)
    max_rating: float | None = Field(default=None, alias="maxRating", ge=0, le=5)
    featured: bool | None = None


class CategoryProductParams(ProductSearchParams):
    pass


class ReviewListParams(PagingParams):
    product_id: str | None = Field(default=None, alias="productId")
    rating: int | None = Field(default=None, ge=1, le=5)
    user_id: str | None = Field(default=None, alias="userId")
    from_: str | None = Field(default=None, alias="from")
    to: str | None = None


class AuditSearchParams(PagingParams):
    action: str | None = None
    entity_type: str | None = Field(default=None, alias="entityType")
    entity_id: str | None = Field(default=None, alias="entityId")
    actor: str | None = None
    source_service: str | None = Field(default=None, alias="sourceService")
    from_: str | None = Field(default=None, alias="from")
    to: str | None = None
    sort_by: str | None = Field(default=None, alias="sortBy")
    sort_dir: str | None = Field(default=None, alias="sortDir")


class ProductPayload(FlexibleModel):
    name: str | None = None
    description: str | None = None
    description_html: str | None = Field(default=None, alias="descriptionHtml")
    price: float | None = Field(default=None, ge=0)
    compare_at_price: float | None = Field(default=None, alias="compareAtPrice", ge=0)
    sku: str | None = None
    thumbnail: str | None = None
    primary_category_id: str | None = Field(default=None, alias="primaryCategoryId")
    primary_category_slug: str | None = Field(default=None, alias="primaryCategorySlug")
    category_ids: list[str] | None = Field(default=None, alias="categoryIds")
    category_slugs: list[str] | None = Field(default=None, alias="categorySlugs")
    brand_id: str | None = Field(default=None, alias="brandId")
    images: list[Any] | None = None
    specs: dict[str, Any] | None = None
    tags: list[str] | None = None
    metadata: dict[str, Any] | None = None


def dump_input(value: BaseModel | None) -> dict[str, Any] | None:
    if value is None:
        return None
    return value.model_dump(by_alias=True, exclude_none=True)
