import httpx
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.core.http import ServiceClient, ServiceDefinition
from app.core.logging import (
    authorization_ctx,
    user_email_ctx,
    user_id_ctx,
    user_role_ctx,
)
from app.services.audit import AuditServiceApi
from app.services.news import NewsServiceApi
from app.services.promotion import PromotionServiceApi
from app.server import READINESS_CHECKS, app


@pytest.fixture(autouse=True)
def disable_eureka_for_tests():
    previous = SETTINGS.eureka_enabled
    SETTINGS.eureka_enabled = False
    READINESS_CHECKS["eureka"] = "disabled"
    yield
    SETTINGS.eureka_enabled = previous


def test_health_endpoint() -> None:
    client = TestClient(app)
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.headers["X-Correlation-Id"]


def test_ready_endpoint() -> None:
    client = TestClient(app)
    response = client.get("/ready")

    assert response.status_code == 200
    payload = response.json()
    assert payload["ready"] is True
    assert payload["service"] == "mcp-server"


def test_root_endpoint_describes_skeleton_layout() -> None:
    client = TestClient(app)
    response = client.get("/")

    assert response.status_code == 200
    payload = response.json()
    assert payload["message"] == "mcp-server skeleton is running"
    assert "app/mcp" in payload["layout"]


@pytest.mark.asyncio
async def test_service_client_retries_transient_failure_then_succeeds() -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise httpx.ConnectError("temporary network failure", request=request)
        return httpx.Response(200, json={"result": {"ok": True}})

    client = ServiceClient(
        ServiceDefinition(name="demo-service", base_url="http://example.test", retry_attempts=2),
        transport=httpx.MockTransport(handler),
    )

    result = await client.request("GET", "/health")

    assert result == {"ok": True}
    assert attempts == 2


@pytest.mark.asyncio
async def test_service_client_returns_structured_error_after_retries() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(503, json={"tag": "PRD-503", "message": "service unavailable"})

    client = ServiceClient(
        ServiceDefinition(name="demo-service", base_url="http://example.test", retry_attempts=1),
        transport=httpx.MockTransport(handler),
    )

    result = await client.request("GET", "/health")

    assert result["ok"] is False
    assert result["error"]["statusCode"] == 503
    assert result["error"]["tag"] == "PRD-503"


@pytest.mark.asyncio
async def test_service_client_unwraps_nest_success_data_envelope() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "success": True,
                "data": {"items": [{"id": "flash-1"}], "total": 1},
                "correlationId": "corr-1",
            },
        )

    client = ServiceClient(
        ServiceDefinition(name="demo-service", base_url="http://example.test"),
        transport=httpx.MockTransport(handler),
    )

    result = await client.request("GET", "/api/v1/demo")

    assert result == {"items": [{"id": "flash-1"}], "total": 1}


@pytest.mark.asyncio
async def test_promotion_api_uses_real_flash_sale_endpoints() -> None:
    seen_requests: list[tuple[str, str]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_requests.append((request.method, request.url.path))
        if request.url.path == "/api/v1/admin/flash-sales":
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "data": {
                        "items": [
                            {
                                "id": "flash-1",
                                "promotionId": "promo-1",
                                "code": "FLASH-001",
                            }
                        ],
                        "total": 1,
                    },
                },
            )
        if request.url.path == "/api/v1/flash-sales":
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "data": {"items": [{"id": "flash-active"}], "total": 1},
                },
            )
        return httpx.Response(404, json={"tag": "PRO-404", "message": "not found"})

    api = PromotionServiceApi(
        ServiceClient(
            ServiceDefinition(name="promotion-service", base_url="http://example.test"),
            transport=httpx.MockTransport(handler),
        )
    )

    promotions = await api.list_promotions()
    active_flash_sales = await api.list_flash_sales()
    promotion = await api.get_promotion("promo-1")
    flash_sale = await api.get_flash_sale("flash-1")

    assert promotions == {
        "items": [{"id": "flash-1", "promotionId": "promo-1", "code": "FLASH-001"}],
        "total": 1,
    }
    assert active_flash_sales == {"items": [{"id": "flash-active"}], "total": 1}
    assert promotion["promotionId"] == "promo-1"
    assert flash_sale["id"] == "flash-1"
    assert ("GET", "/api/v1/promotions") not in seen_requests
    assert ("GET", "/api/v1/flash-sales/flash-1") not in seen_requests


@pytest.mark.asyncio
async def test_audit_api_uses_real_audit_service_endpoints_and_forwards_auth() -> None:
    seen_requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_requests.append(request)
        if request.url.path == "/audit":
            assert request.url.params.get("action") == "ORDER_CREATED"
            assert request.url.params.get("ignored") is None
            return httpx.Response(
                200,
                json={
                    "code": 1000,
                    "message": "Success",
                    "result": {
                        "content": [{"id": "audit-1"}],
                        "page": 1,
                        "size": 20,
                        "totalElements": 1,
                    },
                },
            )
        if request.url.path == "/audit/audit-1":
            return httpx.Response(
                200,
                json={"code": 1000, "message": "Success", "result": {"id": "audit-1"}},
            )
        if request.url.path == "/audit/trace/corr-1":
            return httpx.Response(
                200,
                json={"code": 1000, "message": "Success", "result": [{"id": "audit-1"}]},
            )
        if request.url.path == "/audit/facets":
            return httpx.Response(
                200,
                json={
                    "code": 1000,
                    "message": "Success",
                    "result": {"actions": ["ORDER_CREATED"]},
                },
            )
        return httpx.Response(404, json={"tag": "AUD-404", "message": "not found"})

    api = AuditServiceApi(
        ServiceClient(
            ServiceDefinition(name="audit-service", base_url="http://example.test"),
            transport=httpx.MockTransport(handler),
        )
    )

    token = authorization_ctx.set("Bearer token-123")
    try:
        search = await api.search_audit_logs(
            {"action": "ORDER_CREATED", "ignored": "drop-me", "page": 1}
        )
        entry = await api.get_audit_entry("audit-1")
        trace = await api.trace_by_lookup_id("corr-1")
        facets = await api.get_audit_facets()
    finally:
        authorization_ctx.reset(token)

    assert search["content"] == [{"id": "audit-1"}]
    assert entry["id"] == "audit-1"
    assert trace == [{"id": "audit-1"}]
    assert facets == {"actions": ["ORDER_CREATED"]}
    assert [(request.method, request.url.path) for request in seen_requests] == [
        ("GET", "/audit"),
        ("GET", "/audit/audit-1"),
        ("GET", "/audit/trace/corr-1"),
        ("GET", "/audit/facets"),
    ]
    assert all(
        request.headers.get("Authorization") == "Bearer token-123"
        for request in seen_requests
    )


@pytest.mark.asyncio
async def test_news_api_uses_real_news_service_endpoints() -> None:
    seen_requests: list[tuple[str, str, str]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_requests.append((request.method, request.url.path, request.url.query.decode()))
        if request.url.path == "/api/v1/news/public":
            return httpx.Response(
                200,
                json={
                    "code": 1000,
                    "message": "Public news retrieved successfully",
                    "result": [
                        {"id": "news-1", "slug": "one"},
                        {"id": "news-2", "slug": "two"},
                        {"id": "news-3", "slug": "three"},
                    ],
                },
            )
        if request.url.path == "/api/v1/news":
            return httpx.Response(
                200,
                json={
                    "code": 1000,
                    "message": "All news retrieved successfully",
                    "result": [{"id": "draft-1", "slug": "draft"}],
                },
            )
        if request.url.path == "/api/v1/news/public/one":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "news-1", "slug": "one"}},
            )
        if request.url.path == "/api/v1/news/draft":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "draft-1", "slug": "draft"}},
            )
        if request.url.path == "/api/v1/news/news-1/images":
            return httpx.Response(
                200,
                json={"code": 1000, "result": [{"id": "image-1"}]},
            )
        if request.url.path == "/api/v1/news/images/image-1":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "image-1"}},
            )
        return httpx.Response(404, json={"tag": "NEWS-404", "message": "not found"})

    api = NewsServiceApi(
        ServiceClient(
            ServiceDefinition(name="news-service", base_url="http://example.test"),
            transport=httpx.MockTransport(handler),
        )
    )

    public_news = await api.list_public_news(page=1, size=2)
    all_news = await api.list_all_news(page=0, size=20)
    public_detail = await api.get_public_news_by_slug("one")
    admin_detail = await api.get_news_by_slug("draft")
    images = await api.get_news_images("news-1")
    image = await api.get_news_image("image-1")

    assert public_news == {
        "items": [{"id": "news-3", "slug": "three"}],
        "page": 1,
        "size": 2,
        "totalElements": 3,
        "totalPages": 2,
        "hasMore": False,
    }
    assert all_news["items"] == [{"id": "draft-1", "slug": "draft"}]
    assert public_detail == {"id": "news-1", "slug": "one"}
    assert admin_detail == {"id": "draft-1", "slug": "draft"}
    assert images == [{"id": "image-1"}]
    assert image == {"id": "image-1"}
    assert seen_requests == [
        ("GET", "/api/v1/news/public", ""),
        ("GET", "/api/v1/news", ""),
        ("GET", "/api/v1/news/public/one", ""),
        ("GET", "/api/v1/news/draft", ""),
        ("GET", "/api/v1/news/news-1/images", ""),
        ("GET", "/api/v1/news/images/image-1", ""),
    ]


@pytest.mark.asyncio
async def test_service_client_forwards_user_headers_from_context() -> None:
    seen_headers: dict[str, str] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen_headers["X-User-Id"] = request.headers.get("X-User-Id", "")
        seen_headers["X-User-Role"] = request.headers.get("X-User-Role", "")
        seen_headers["X-User-Email"] = request.headers.get("X-User-Email", "")
        return httpx.Response(200, json={"result": {"ok": True}})

    client = ServiceClient(
        ServiceDefinition(name="demo-service", base_url="http://example.test", retry_attempts=1),
        transport=httpx.MockTransport(handler),
    )

    token_user_id = user_id_ctx.set("user-abc")
    token_user_role = user_role_ctx.set("manager")
    token_user_email = user_email_ctx.set("user@nexatech.local")
    try:
        result = await client.request("GET", "/health")
    finally:
        user_id_ctx.reset(token_user_id)
        user_role_ctx.reset(token_user_role)
        user_email_ctx.reset(token_user_email)

    assert result == {"ok": True}
    assert seen_headers["X-User-Id"] == "user-abc"
    assert seen_headers["X-User-Role"] == "manager"
    assert seen_headers["X-User-Email"] == "user@nexatech.local"
