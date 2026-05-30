from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.news import NEWS_API


@mcp.tool()
async def list_news(page: int = 0, size: int = 10) -> dict[str, Any]:
    return await NEWS_API.list_public_news(page=page, size=size)


@mcp.tool()
async def list_all_news(page: int = 0, size: int = 20) -> dict[str, Any]:
    return await NEWS_API.list_all_news(page=page, size=size)


@mcp.tool()
async def get_news_by_slug(slug: str) -> dict[str, Any]:
    return await NEWS_API.get_news_by_slug(slug)


@mcp.tool()
async def get_public_news_by_slug(slug: str) -> dict[str, Any]:
    return await NEWS_API.get_public_news_by_slug(slug)


@mcp.tool()
async def get_news_images(news_id: str) -> dict[str, Any]:
    return await NEWS_API.get_news_images(news_id)


@mcp.tool()
async def get_news_image(image_id: str) -> dict[str, Any]:
    return await NEWS_API.get_news_image(image_id)
