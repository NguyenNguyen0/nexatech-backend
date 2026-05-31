from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.services.product import PRODUCT_API
from pydantic import Field


@mcp.tool()
async def list_products(
    params: Annotated[
        dict[str, Any] | None,
        Field(
            description=(
                "Optional product listing filters. Supported keys: keyword, categoryId, "
                "categorySlug, brandId, sku, minPrice, maxPrice, minRating, maxRating, "
                "featured, active, includeDeleted, page, size, sort, and specs.<key>."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """List products with optional filters, specs filters, sorting, and pagination.

    Use this for broad catalog browsing or admin/product search when the caller
    needs many products. Product IDs are plain strings from product-service; do not
    reinterpret them as Mongo ObjectIds.
    """
    return await PRODUCT_API.list_products(params=params)


@mcp.tool()
async def search_products(
    keyword: Annotated[
        str,
        Field(description="Required search keyword for product name/content matching."),
    ],
    params: Annotated[
        dict[str, Any] | None,
        Field(
            description=(
                "Optional search filters. Supported keys: categoryId, categorySlug, "
                "brandId, minPrice, maxPrice, minRating, maxRating, featured, page, "
                "size, sort, and specs.<key>."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """Search products by keyword with optional catalog filters.

    Use this when the user's intent is textual search. For exact product ID lookup,
    use get_product; for exact slug lookup, use get_product_by_slug.
    """
    return await PRODUCT_API.search_products(keyword, params=params)


@mcp.tool()
async def get_product(
    product_id: Annotated[
        str,
        Field(description="Product ID string to retrieve. Do not convert to ObjectId."),
    ],
) -> dict[str, Any]:
    """Get one product by its string product ID.

    Use this when the caller already has a product-service ID and needs full product
    details such as name, price, categories, brand, images, specs, tags, metadata,
    rating, and active/deleted state.
    """
    return await PRODUCT_API.get_product(product_id)


@mcp.tool()
async def get_product_by_slug(
    slug: Annotated[
        str,
        Field(description="Canonical product slug to retrieve."),
    ],
) -> dict[str, Any]:
    """Get one product by slug.

    Use this for storefront URLs, SEO routes, or user-facing product links where the
    slug is known but the product ID is not.
    """
    return await PRODUCT_API.get_product_by_slug(slug)


@mcp.tool()
async def list_featured_products(
    params: Annotated[
        dict[str, Any] | None,
        Field(description="Optional paging params: page, size, sort."),
    ] = None,
) -> dict[str, Any]:
    """List featured products.

    Use this for homepage, campaign, or recommendation surfaces that need products
    explicitly marked as featured. Do not use it for a full catalog listing.
    """
    return await PRODUCT_API.list_featured_products(params=params)


@mcp.tool()
async def list_products_by_tags(
    tags: Annotated[
        list[str],
        Field(description="One or more product tags to match."),
    ],
    params: Annotated[
        dict[str, Any] | None,
        Field(description="Optional paging params: page, size, sort."),
    ] = None,
) -> dict[str, Any]:
    """List products that match one or more tags.

    Use this for tag-based collections such as gaming, new-arrival, best-seller, or
    other merchandising tags. For textual search, use search_products instead.
    """
    return await PRODUCT_API.list_products_by_tags(tags, params=params)


@mcp.tool()
async def list_products_by_category(
    category_id: Annotated[
        str,
        Field(description="Category ID string. Do not convert to ObjectId."),
    ],
    params: Annotated[
        dict[str, Any] | None,
        Field(description="Optional paging params: page, size, sort."),
    ] = None,
) -> dict[str, Any]:
    """List products in a category by category ID.

    Use this when the caller has a product-service category ID. For storefront
    category routes that use slugs, use list_products_by_category_slug.
    """
    return await PRODUCT_API.list_products_by_category(category_id, params=params)


@mcp.tool()
async def list_products_by_category_slug(
    slug: Annotated[
        str,
        Field(description="Category slug used in storefront/category URLs."),
    ],
    params: Annotated[
        dict[str, Any] | None,
        Field(
            description=(
                "Optional category filters. Supported keys: brandId, minPrice, "
                "maxPrice, minRating, maxRating, featured, page, size, sort, and "
                "specs.<key>."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """List products in a category by category slug.

    Use this for customer-facing category pages, faceted browsing, and SEO category
    routes. For direct category IDs, use list_products_by_category.
    """
    return await PRODUCT_API.list_products_by_category_slug(slug, params=params)


@mcp.tool()
async def get_related_products(
    product_id: Annotated[
        str,
        Field(description="Product ID string whose related products should be returned."),
    ],
) -> dict[str, Any]:
    """Get products related to a product ID.

    Use this for product detail recommendations when the caller has the canonical
    product ID. The tool is read-only and returns product-service related items.
    """
    return await PRODUCT_API.get_related_products(product_id)


@mcp.tool()
async def get_related_products_by_slug(
    slug: Annotated[
        str,
        Field(description="Product slug whose related products should be returned."),
    ],
) -> dict[str, Any]:
    """Get products related to a product slug.

    Use this for storefront product pages where URL slugs are available but IDs are
    not. The tool is read-only.
    """
    return await PRODUCT_API.get_related_products_by_slug(slug)


@mcp.tool()
async def create_product(
    payload: Annotated[
        dict[str, Any],
        Field(
            description=(
                "Product payload. Required keys: name and price. Optional keys: "
                "description, descriptionHtml, compareAtPrice, sku, thumbnail, "
                "primaryCategoryId, primaryCategorySlug, categoryIds, categorySlugs, "
                "brandId, images, specs, tags, metadata."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Create a product.

    This tool has side effects. Use it only for explicit admin/catalog creation
    requests. Product and related IDs must be plain strings; never coerce them to
    Mongo ObjectIds.
    """
    return await PRODUCT_API.create_product(payload)


@mcp.tool()
async def update_product(
    product_id: Annotated[
        str,
        Field(description="Product ID string to update. Do not convert to ObjectId."),
    ],
    payload: Annotated[
        dict[str, Any],
        Field(
            description=(
                "Replacement product payload with ProductRequest fields: name, "
                "description, descriptionHtml, price, compareAtPrice, sku, thumbnail, "
                "primaryCategoryId, primaryCategorySlug, categoryIds, categorySlugs, "
                "brandId, images, specs, tags, metadata."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Update a product by ID.

    This tool has side effects. Use it only for explicit catalog update requests.
    The downstream product-service applies its own validation and authorization.
    """
    return await PRODUCT_API.update_product(product_id, payload)


@mcp.tool()
async def delete_product(
    product_id: Annotated[
        str,
        Field(description="Product ID string to delete/soft-delete."),
    ],
) -> dict[str, Any]:
    """Delete or soft-delete a product by ID.

    This tool has side effects. Use it only when the caller explicitly wants to
    remove a product from active catalog visibility.
    """
    return await PRODUCT_API.delete_product(product_id)


@mcp.tool()
async def restore_product(
    product_id: Annotated[
        str,
        Field(description="Product ID string to restore."),
    ],
) -> dict[str, Any]:
    """Restore a previously deleted product by ID.

    This tool has side effects. Use it only when the caller explicitly wants to
    reactivate or restore a product record.
    """
    return await PRODUCT_API.restore_product(product_id)


@mcp.tool()
async def list_reviews_by_product(
    product_id: Annotated[
        str,
        Field(description="Product ID string whose reviews should be listed."),
    ],
    params: Annotated[
        dict[str, Any] | None,
        Field(description="Optional review filters/paging: rating, page, size, sort."),
    ] = None,
) -> dict[str, Any]:
    """List reviews for a product by product ID.

    Use this for product detail review sections or support checks when the product
    ID is known. The optional rating filter accepts values 1 through 5.
    """
    return await PRODUCT_API.list_reviews_by_product(product_id, params=params)


@mcp.tool()
async def list_reviews_by_product_slug(
    slug: Annotated[
        str,
        Field(description="Product slug whose reviews should be listed."),
    ],
    params: Annotated[
        dict[str, Any] | None,
        Field(description="Optional review filters/paging: rating, page, size, sort."),
    ] = None,
) -> dict[str, Any]:
    """List reviews for a product by product slug.

    Use this for storefront product pages where the slug is available. For direct
    product IDs, use list_reviews_by_product.
    """
    return await PRODUCT_API.list_reviews_by_product_slug(slug, params=params)


@mcp.tool()
async def get_my_review_for_product(
    product_id: Annotated[
        str,
        Field(description="Product ID string to check for the current user's review."),
    ],
) -> dict[str, Any]:
    """Get the current user's review/eligibility state for a product.

    Use this when the authenticated user asks whether they can review a product or
    wants to view their existing review for that product.
    """
    return await PRODUCT_API.get_my_review_for_product(product_id)


@mcp.tool()
async def create_review(
    product_id: Annotated[
        str,
        Field(description="Product ID string being reviewed."),
    ],
    rating: Annotated[
        int,
        Field(description="Review rating from 1 to 5.", ge=1, le=5),
    ],
    title: Annotated[
        str,
        Field(description="Short review title."),
    ],
    content: Annotated[
        str,
        Field(description="Review body/content."),
    ],
    images: Annotated[
        list[str] | None,
        Field(description="Optional uploaded review image URLs or storage keys."),
    ] = None,
    videos: Annotated[
        list[str] | None,
        Field(description="Optional uploaded review video URLs or storage keys."),
    ] = None,
) -> dict[str, Any]:
    """Create a review for a product as the current authenticated user.

    This tool has side effects. Use it only when the caller explicitly wants to
    submit a review. It does not upload media files; pass already-uploaded image or
    video references if available.
    """
    payload: dict[str, Any] = {"rating": rating, "title": title, "content": content}
    if images is not None:
        payload["images"] = images
    if videos is not None:
        payload["videos"] = videos
    return await PRODUCT_API.create_review(product_id, payload)


@mcp.tool()
async def list_reviews(
    params: Annotated[
        dict[str, Any] | None,
        Field(
            description=(
                "Optional admin review filters. Supported keys: productId, rating, "
                "userId, from, to, page, size, sort. Date filters should be ISO-8601."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """List reviews from the admin review endpoint.

    Use this for moderation, support, analytics, or filtered review investigation
    across products/users. For a storefront product page, prefer
    list_reviews_by_product or list_reviews_by_product_slug.
    """
    return await PRODUCT_API.list_reviews(params=params)


@mcp.tool()
async def update_review(
    review_id: Annotated[
        str,
        Field(description="Review ID string to update."),
    ],
    rating: Annotated[
        int,
        Field(description="Updated rating from 1 to 5.", ge=1, le=5),
    ],
    title: Annotated[
        str,
        Field(description="Updated review title."),
    ],
    content: Annotated[
        str,
        Field(description="Updated review content."),
    ],
    images: Annotated[
        list[str] | None,
        Field(description="Optional replacement image references."),
    ] = None,
    videos: Annotated[
        list[str] | None,
        Field(description="Optional replacement video references."),
    ] = None,
) -> dict[str, Any]:
    """Update an existing review.

    This tool has side effects. Use it only when the caller explicitly wants to
    edit a review. Authorization is enforced by product-service using user context.
    """
    payload: dict[str, Any] = {"rating": rating, "title": title, "content": content}
    if images is not None:
        payload["images"] = images
    if videos is not None:
        payload["videos"] = videos
    return await PRODUCT_API.update_review(review_id, payload)


@mcp.tool()
async def delete_review(
    review_id: Annotated[
        str,
        Field(description="Review ID string to delete."),
    ],
) -> dict[str, Any]:
    """Delete a review by ID.

    This tool has side effects. Use it only when the caller explicitly wants to
    remove a review. Product-service enforces owner/admin authorization.
    """
    return await PRODUCT_API.delete_review(review_id)


@mcp.tool()
async def mark_review_helpful(
    review_id: Annotated[
        str,
        Field(description="Review ID string to mark as helpful."),
    ],
) -> dict[str, Any]:
    """Mark a review as helpful.

    This tool has side effects because it increments or records helpful feedback
    through product-service. Use it only for explicit helpful-vote actions.
    """
    return await PRODUCT_API.mark_review_helpful(review_id)
