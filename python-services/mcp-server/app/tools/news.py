from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.services.news import NEWS_API
from pydantic import Field


@mcp.tool()
async def list_news(
    page: Annotated[
        int,
        Field(description="Zero-based page index for public news results.", ge=0),
    ] = 0,
    size: Annotated[
        int,
        Field(
            description=(
                "Maximum number of public news items to return. The service wrapper "
                "clamps this value to the range 1..100."
            ),
            ge=1,
            le=100,
        ),
    ] = 10,
) -> dict[str, Any]:
    """List public/published news articles for storefront or customer-facing use.

    The news-service public endpoint returns all public records, then this tool
    applies local pagination and returns items, page, size, totalElements,
    totalPages, and hasMore when the upstream response is a list.
    """
    return await NEWS_API.list_public_news(page=page, size=size)


@mcp.tool()
async def list_all_news(
    page: Annotated[
        int,
        Field(description="Zero-based page index for the admin/all-news listing.", ge=0),
    ] = 0,
    size: Annotated[
        int,
        Field(
            description=(
                "Maximum number of news items to return. The service wrapper clamps "
                "this value to the range 1..100."
            ),
            ge=1,
            le=100,
        ),
    ] = 20,
) -> dict[str, Any]:
    """List all news articles from the admin/internal news endpoint.

    Use this when an operator needs published and non-public records, moderation
    state, drafts, or internal news metadata. Pagination is applied locally when
    the upstream response is a list.
    """
    return await NEWS_API.list_all_news(page=page, size=size)


@mcp.tool()
async def get_news_by_slug(
    slug: Annotated[
        str,
        Field(description="Canonical news slug used by the internal/admin endpoint."),
    ],
) -> dict[str, Any]:
    """Get a news article by slug from the internal/admin endpoint.

    Use this when the caller may need fields that are not exposed publicly, such as
    draft status, moderation data, or internal metadata.
    """
    return await NEWS_API.get_news_by_slug(slug)


@mcp.tool()
async def get_public_news_by_slug(
    slug: Annotated[
        str,
        Field(description="Canonical public slug of the published news article."),
    ],
) -> dict[str, Any]:
    """Get a published news article by its public slug.

    Use this for customer-facing article reads. It calls the public news endpoint
    and returns the raw news-service response, including article body and metadata
    if provided by the service.
    """
    return await NEWS_API.get_public_news_by_slug(slug)


@mcp.tool()
async def get_news_images(
    news_id: Annotated[
        str,
        Field(description="News article ID whose attached image records should be listed."),
    ],
) -> dict[str, Any]:
    """List image metadata attached to a news article.

    Use this when rendering or auditing a news article's gallery, thumbnail, or
    embedded assets. The response is delegated directly from news-service.
    """
    return await NEWS_API.get_news_images(news_id)


@mcp.tool()
async def get_news_image(
    image_id: Annotated[
        str,
        Field(description="Image record ID to retrieve from news-service."),
    ],
) -> dict[str, Any]:
    """Get one news image record by image ID.

    Use this for resolving image metadata, URL, alt text, or storage details for a
    specific news asset.
    """
    return await NEWS_API.get_news_image(image_id)
