import json

import httpx
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.core.http import ServiceClient, ServiceDefinition
from app.core.logging import (
    authorization_ctx,
    user_email_ctx,
    user_id_ctx,
    user_role_ctx,
)
from app.mcp.domains import get_profile_tool_modules, list_domain_metadata
from app.schemas.tool_contract import tool_result, validation_error
from app.services.audit import AuditServiceApi
from app.services.inventory import InventoryServiceApi
from app.services.news import NewsServiceApi
from app.services.product import ProductServiceApi
from app.services.promotion import PromotionServiceApi
from app.server import READINESS_CHECKS, health, ready, root
from app.tools.meta import get_skill, list_skills


@pytest.fixture(autouse=True)
def disable_eureka_for_tests():
    previous = SETTINGS.eureka_enabled
    previous_dependency_checks = SETTINGS.startup_dependency_checks_enabled
    SETTINGS.eureka_enabled = False
    SETTINGS.startup_dependency_checks_enabled = False
    READINESS_CHECKS["eureka"] = "disabled"
    yield
    SETTINGS.eureka_enabled = previous
    SETTINGS.startup_dependency_checks_enabled = previous_dependency_checks


def test_health_endpoint() -> None:
    payload = health()

    assert payload["status"] == "UP"
    assert payload["profile"] == "all"


def test_ready_endpoint() -> None:
    payload = ready()

    assert payload["ready"] is True
    assert payload["service"] == "mcp-server"


def test_root_endpoint_describes_skeleton_layout() -> None:
    payload = root()

    assert payload["message"] == "mcp-server skeleton is running"
    assert payload["profile"] == "all"
    assert "app/mcp" in payload["layout"]
    assert "inventory" in payload["registeredToolModules"]


def test_mcp_profiles_select_domain_tool_modules() -> None:
    assert get_profile_tool_modules("inventory") == ("inventory",)
    assert get_profile_tool_modules("product") == ("product",)
    assert get_profile_tool_modules("meta") == ("meta",)
    assert "health" in get_profile_tool_modules("all")
    assert get_profile_tool_modules("unknown") == get_profile_tool_modules("all")


def test_meta_domain_metadata_lists_independent_mcp_endpoints() -> None:
    domains = {item["name"]: item for item in list_domain_metadata()}

    assert domains["inventory"]["serviceName"] == "inventory-mcp"
    assert domains["inventory"]["mcpEndpoint"].endswith(":8101/mcp")
    assert domains["product"]["toolModules"] == ["product"]
    assert domains["audit"]["dependencies"] == ["audit-service"]


def test_tool_result_wraps_success_payload_with_backward_compatible_ok() -> None:
    result = tool_result({"id": "product-1"}, requires_approval=True, idempotency_key="idem-1")

    assert result["success"] is True
    assert result["ok"] is True
    assert result["data"] == {"id": "product-1"}
    assert result["requiresApproval"] is True
    assert result["idempotencyKey"] == "idem-1"


def test_tool_result_preserves_structured_downstream_error() -> None:
    result = tool_result(
        {
            "ok": False,
            "service": "inventory-service",
            "statusCode": 404,
            "tag": "INV-5001",
            "message": "Inventory item not found",
            "correlationId": "corr-1",
        },
        rollback_needed=True,
    )

    assert result["success"] is False
    assert result["ok"] is False
    assert result["tag"] == "INV-5001"
    assert result["error"]["code"] == "INV-5001"
    assert result["error"]["rollbackNeeded"] is True
    assert result["correlationId"] == "corr-1"


def test_validation_error_uses_tool_contract_error_shape() -> None:
    result = validation_error("Bad request", code="MCP-BAD")

    assert result["success"] is False
    assert result["ok"] is False
    assert result["tag"] == "MCP-BAD"
    assert result["error"]["retryAllowed"] is False


@pytest.mark.asyncio
async def test_meta_lists_skills_for_domain() -> None:
    result = await list_skills("inventory")

    assert result["success"] is True
    assert result["data"]["domain"] == "inventory"
    assert "_index.md" in result["data"]["skills"]
    assert "fulfillment_skill.md" in result["data"]["skills"]


@pytest.mark.asyncio
async def test_meta_get_skill_reads_domain_index() -> None:
    result = await get_skill("inventory")

    assert result["success"] is True
    assert result["data"]["skillName"] == "_index.md"
    assert "# Skill: Inventory Index" in result["data"]["content"]


@pytest.mark.asyncio
async def test_meta_get_skill_rejects_path_traversal() -> None:
    result = await get_skill("inventory", "../secret")

    assert result["success"] is False
    assert result["tag"] == "MCP-INVALID-SKILL-NAME"


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
async def test_inventory_api_uses_real_inventory_service_endpoints() -> None:
    seen_requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_requests.append(request)
        if request.url.path == "/api/v1/inventory/list":
            assert request.url.params.get("keyword") == "phone"
            assert request.url.params.get("ignored") is None
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "data": {"content": [{"productId": "product-1"}], "totalElements": 1},
                },
            )
        if request.url.path == "/api/v1/inventory/product-1":
            return httpx.Response(
                200,
                json={"success": True, "data": {"productId": "product-1", "quantity": 10}},
            )
        if request.url.path == "/api/v1/inventory/product-1/movements":
            assert request.url.params.get("page") == "0"
            return httpx.Response(
                200,
                json={"success": True, "data": {"content": [{"id": "move-1"}]}},
            )
        if request.url.path == "/api/v1/inventory" and request.method == "PUT":
            assert json.loads(request.content) == {
                "productId": "product-1",
                "sku": "SKU-1",
                "quantity": 12,
                "reason": "manual",
            }
            return httpx.Response(
                200,
                json={"success": True, "data": {"productId": "product-1", "quantity": 12}},
            )
        if request.url.path == "/api/v1/inventory/check":
            return httpx.Response(200, json={"success": True, "data": True})
        if request.url.path == "/api/v1/inventory/reserve":
            return httpx.Response(200, json={"success": True, "data": None})
        if request.url.path == "/api/v1/inventory/release":
            return httpx.Response(200, json={"success": True, "data": None})
        if request.url.path == "/api/v1/inventory/low-stock":
            return httpx.Response(
                200,
                json={"success": True, "data": [{"productId": "low-1"}]},
            )
        if request.url.path == "/api/v1/inventory/bulk-import":
            assert json.loads(request.content) == [{"productId": "product-1", "quantity": 12}]
            return httpx.Response(
                200,
                json={"success": True, "data": {"successCount": 1, "failureCount": 0}},
            )
        if request.url.path == "/api/v1/inventory/product-1/warehouse":
            assert json.loads(request.content) == {
                "warehouseLocation": "A1",
                "reorderLevel": 5,
            }
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "data": {
                        "productId": "product-1",
                        "warehouseLocation": "A1",
                        "reorderLevel": 5,
                    },
                },
            )
        return httpx.Response(404, json={"tag": "INV-404", "message": "not found"})

    api = InventoryServiceApi(
        ServiceClient(
            ServiceDefinition(name="inventory-service", base_url="http://example.test"),
            transport=httpx.MockTransport(handler),
        )
    )

    inventory = await api.list_inventory({"keyword": "phone", "ignored": "drop-me"})
    stock = await api.get_inventory("product-1")
    movements = await api.get_inventory_movements(
        "product-1",
        {"page": 0, "ignored": "drop-me"},
    )
    updated = await api.update_stock(
        {
            "productId": "product-1",
            "sku": "SKU-1",
            "quantity": 12,
            "reason": "manual",
        }
    )
    available = await api.check_inventory(
        {"items": [{"productId": "product-1", "quantity": 1}]}
    )
    reserved = await api.reserve_stock(
        {"items": [{"productId": "product-1", "quantity": 1}]}
    )
    released = await api.release_stock(
        {"items": [{"productId": "product-1", "quantity": 1}]}
    )
    low_stock = await api.low_stock_items()
    imported = await api.bulk_import([{"productId": "product-1", "quantity": 12}])
    warehouse = await api.update_warehouse_info(
        "product-1",
        {"warehouseLocation": "A1", "reorderLevel": 5},
    )

    assert inventory["content"] == [{"productId": "product-1"}]
    assert stock == {"productId": "product-1", "quantity": 10}
    assert movements == {"content": [{"id": "move-1"}]}
    assert updated["quantity"] == 12
    assert available is True
    assert reserved is None
    assert released is None
    assert low_stock == [{"productId": "low-1"}]
    assert imported == {"successCount": 1, "failureCount": 0}
    assert warehouse["warehouseLocation"] == "A1"
    assert [(request.method, request.url.path) for request in seen_requests] == [
        ("GET", "/api/v1/inventory/list"),
        ("GET", "/api/v1/inventory/product-1"),
        ("GET", "/api/v1/inventory/product-1/movements"),
        ("PUT", "/api/v1/inventory"),
        ("POST", "/api/v1/inventory/check"),
        ("POST", "/api/v1/inventory/reserve"),
        ("POST", "/api/v1/inventory/release"),
        ("GET", "/api/v1/inventory/low-stock"),
        ("POST", "/api/v1/inventory/bulk-import"),
        ("PATCH", "/api/v1/inventory/product-1/warehouse"),
    ]


@pytest.mark.asyncio
async def test_product_api_uses_real_product_and_review_endpoints() -> None:
    seen_requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_requests.append(request)
        if request.url.path == "/api/v1/products" and request.method == "GET":
            assert request.url.params.get("keyword") == "phone"
            assert request.url.params.get("specs.ram") == "16GB"
            assert request.url.params.get("ignored") is None
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "product-1", "thumbnail": "http://example.com/thumbnail-1.png"}]}},
            )
        if request.url.path == "/api/v1/products/search":
            assert request.url.params.get("keyword") == "laptop"
            assert request.url.params.get("categorySlug") == "laptops"
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "product-2", "thumbnail": "http://example.com/thumbnail-2.png"}]}},
            )
        if request.url.path == "/api/v1/products/product-1" and request.method == "GET":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "product-1", "name": "Phone", "thumbnail": "http://example.com/thumbnail-1.png"}},
            )
        if request.url.path == "/api/v1/products/slug/phone-1" and request.method == "GET":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "product-1", "slug": "phone-1", "thumbnail": "http://example.com/thumbnail-1.png"}},
            )
        if request.url.path == "/api/v1/products/featured":
            assert request.url.params.get("page") == "0"
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "featured-1", "thumbnail": "http://example.com/thumbnail-featured.png"}]}},
            )
        if request.url.path == "/api/v1/products/tags":
            assert list(request.url.params.get_list("tags")) == ["gaming", "sale"]
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "tagged-1"}]}},
            )
        if request.url.path == "/api/v1/products/category/category-1":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "category-1"}]}},
            )
        if request.url.path == "/api/v1/products/category/slug/laptops":
            assert request.url.params.get("brandId") == "brand-1"
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "laptop-1"}]}},
            )
        if request.url.path == "/api/v1/products/product-1/related":
            return httpx.Response(200, json={"code": 1000, "result": [{"id": "related-1"}]})
        if request.url.path == "/api/v1/products/slug/phone-1/related":
            return httpx.Response(200, json={"code": 1000, "result": [{"id": "related-2"}]})
        if request.url.path == "/api/v1/products" and request.method == "POST":
            assert json.loads(request.content) == {"name": "Phone", "price": 100}
            return httpx.Response(
                201,
                json={"code": 1000, "result": {"id": "product-new", "name": "Phone"}},
            )
        if request.url.path == "/api/v1/products/product-1" and request.method == "PUT":
            assert json.loads(request.content)["name"] == "Updated Phone"
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "product-1", "name": "Updated Phone"}},
            )
        if request.url.path == "/api/v1/products/product-1" and request.method == "DELETE":
            return httpx.Response(200, json={"code": 1000, "result": None})
        if request.url.path == "/api/v1/products/product-1/restore":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "product-1", "active": True}},
            )
        if request.url.path == "/api/v1/products/product-1/reviews":
            if request.method == "GET":
                assert request.url.params.get("rating") == "5"
                return httpx.Response(
                    200,
                    json={"code": 1000, "result": {"content": [{"id": "review-1"}]}},
                )
            assert request.headers.get("X-User-Id") == "user-1"
            assert json.loads(request.content)["rating"] == 5
            return httpx.Response(
                201,
                json={"code": 1000, "result": {"id": "review-new", "rating": 5}},
            )
        if request.url.path == "/api/v1/products/slug/phone-1/reviews":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "review-slug"}]}},
            )
        if request.url.path == "/api/v1/products/product-1/reviews/mine":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"eligible": True, "review": None}},
            )
        if request.url.path == "/api/v1/reviews" and request.method == "GET":
            assert request.url.params.get("productId") == "product-1"
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"content": [{"id": "review-admin"}]}},
            )
        if request.url.path == "/api/v1/reviews/review-1" and request.method == "PUT":
            assert json.loads(request.content)["title"] == "Better title"
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "review-1", "title": "Better title"}},
            )
        if request.url.path == "/api/v1/reviews/review-1" and request.method == "DELETE":
            return httpx.Response(200, json={"code": 1000, "result": None})
        if request.url.path == "/api/v1/reviews/review-1/helpful":
            return httpx.Response(
                200,
                json={"code": 1000, "result": {"id": "review-1", "helpfulCount": 1}},
            )
        return httpx.Response(404, json={"tag": "PRD-404", "message": "not found"})

    api = ProductServiceApi(
        ServiceClient(
            ServiceDefinition(name="product-service", base_url="http://example.test"),
            transport=httpx.MockTransport(handler),
        )
    )

    token_user_id = user_id_ctx.set("user-1")
    try:
        products = await api.list_products(
            {"keyword": "phone", "specs.ram": "16GB", "ignored": "drop-me"}
        )
        search = await api.search_products("laptop", {"categorySlug": "laptops"})
        product = await api.get_product_by_id("product-1")
        product_by_slug = await api.get_product_by_slug("phone-1")
        featured = await api.list_featured_products({"page": 0})
        tagged = await api.list_products_by_tags(["gaming", "sale"], {"size": 5})
        category = await api.list_products_by_category("category-1", {"page": 1})
        category_slug = await api.list_products_by_category_slug("laptops", {"brandId": "brand-1"})
        related = await api.get_related_products("product-1")
        related_by_slug = await api.get_related_products_by_slug("phone-1")
        created = await api.create_product({"name": "Phone", "price": 100})
        updated = await api.update_product("product-1", {"name": "Updated Phone", "price": 120})
        deleted = await api.delete_product("product-1")
        restored = await api.restore_product("product-1")
        product_reviews = await api.list_reviews_by_product("product-1", {"rating": 5})
        slug_reviews = await api.list_reviews_by_product_slug("phone-1")
        my_review = await api.get_my_review_for_product("product-1")
        created_review = await api.create_review(
            "product-1",
            {"rating": 5, "title": "Great", "content": "Works well"},
        )
        admin_reviews = await api.list_reviews({"productId": "product-1", "ignored": "drop-me"})
        updated_review = await api.update_review(
            "review-1",
            {"rating": 4, "title": "Better title", "content": "Updated"},
        )
        deleted_review = await api.delete_review("review-1")
        helpful = await api.mark_review_helpful("review-1")
    finally:
        user_id_ctx.reset(token_user_id)

    assert products["content"] == [{"id": "product-1", "thumbnail": "http://example.com/thumbnail-1.png"}]
    assert search["content"] == [{"id": "product-2", "thumbnail": "http://example.com/thumbnail-2.png"}]
    assert product["id"] == "product-1"
    assert product["thumbnail"] == "http://example.com/thumbnail-1.png"
    assert product_by_slug["slug"] == "phone-1"
    assert product_by_slug["thumbnail"] == "http://example.com/thumbnail-1.png"
    assert featured["content"] == [{"id": "featured-1", "thumbnail": "http://example.com/thumbnail-featured.png"}]
    assert tagged["content"] == [{"id": "tagged-1"}]
    assert category["content"] == [{"id": "category-1"}]
    assert category_slug["content"] == [{"id": "laptop-1"}]
    assert related == [{"id": "related-1"}]
    assert related_by_slug == [{"id": "related-2"}]
    assert created["id"] == "product-new"
    assert updated["name"] == "Updated Phone"
    assert deleted is None
    assert restored["active"] is True
    assert product_reviews["content"] == [{"id": "review-1"}]
    assert slug_reviews["content"] == [{"id": "review-slug"}]
    assert my_review == {"eligible": True, "review": None}
    assert created_review["id"] == "review-new"
    assert admin_reviews["content"] == [{"id": "review-admin"}]
    assert updated_review["title"] == "Better title"
    assert deleted_review is None
    assert helpful["helpfulCount"] == 1
    assert [(request.method, request.url.path) for request in seen_requests] == [
        ("GET", "/api/v1/products"),
        ("GET", "/api/v1/products/search"),
        ("GET", "/api/v1/products/product-1"),
        ("GET", "/api/v1/products/slug/phone-1"),
        ("GET", "/api/v1/products/featured"),
        ("GET", "/api/v1/products/tags"),
        ("GET", "/api/v1/products/category/category-1"),
        ("GET", "/api/v1/products/category/slug/laptops"),
        ("GET", "/api/v1/products/product-1/related"),
        ("GET", "/api/v1/products/slug/phone-1/related"),
        ("POST", "/api/v1/products"),
        ("PUT", "/api/v1/products/product-1"),
        ("DELETE", "/api/v1/products/product-1"),
        ("PATCH", "/api/v1/products/product-1/restore"),
        ("GET", "/api/v1/products/product-1/reviews"),
        ("GET", "/api/v1/products/slug/phone-1/reviews"),
        ("GET", "/api/v1/products/product-1/reviews/mine"),
        ("POST", "/api/v1/products/product-1/reviews"),
        ("GET", "/api/v1/reviews"),
        ("PUT", "/api/v1/reviews/review-1"),
        ("DELETE", "/api/v1/reviews/review-1"),
        ("POST", "/api/v1/reviews/review-1/helpful"),
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
