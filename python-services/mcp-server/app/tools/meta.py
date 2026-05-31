from __future__ import annotations

from typing import Annotated, Any

import httpx
from pydantic import Field

from app.core.settings import SETTINGS
from app.mcp.domains import DOMAIN_PROFILES, domain_url, list_domain_metadata
from app.mcp.server import mcp
from app.schemas.tool_contract import tool_result, validation_error


@mcp.tool()
async def list_mcp_domains() -> dict[str, Any]:
    """List every domain MCP managed by the meta MCP server.

    Use this before routing a user task to a domain MCP. The response includes
    the domain name, service name, base URL, MCP endpoint, dependencies, and a
    short description of the business boundary.
    """
    return tool_result(
        {
            "profile": SETTINGS.mcp_server_profile,
            "domains": list_domain_metadata(),
        }
    )


@mcp.tool()
async def get_mcp_domain(
    domain: Annotated[
        str,
        Field(
            description=(
                "Domain name to inspect. Supported values: inventory, product, "
                "order, payment, promotion, news, audit."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Get routing metadata for one domain MCP.

    Use this when the agent already knows the target domain and needs the
    corresponding MCP endpoint or dependency list.
    """
    normalized = domain.strip().lower()
    domain_config = DOMAIN_PROFILES.get(normalized)
    if domain_config is None:
        return validation_error(
            f"Unknown MCP domain '{domain}'",
            code="MCP-UNKNOWN-DOMAIN",
        )

    return tool_result({"domain": _domain_payload(normalized)})


@mcp.tool()
async def route_to_mcp_domain(
    intent: Annotated[
        str,
        Field(description="Short natural-language intent that should be routed."),
    ],
) -> dict[str, Any]:
    """Suggest the most likely domain MCP for a short intent.

    This is a deterministic keyword router for coarse domain selection only. It
    does not execute business tools. If several domains match, the response marks
    the route as multi_domain and returns all candidate MCP endpoints.
    """
    normalized = intent.casefold()
    signals: dict[str, tuple[str, ...]] = {
        "inventory": (
            "inventory",
            "stock",
            "warehouse",
            "reserve",
            "release",
            "tồn kho",
            "ton kho",
            "kho",
        ),
        "product": (
            "product",
            "products",
            "catalog",
            "category",
            "review",
            "sản phẩm",
            "san pham",
            "danh mục",
            "danh muc",
            "đánh giá",
            "danh gia",
        ),
        "order": (
            "order",
            "orders",
            "purchased",
            "trace",
            "đơn hàng",
            "don hang",
            "mua hàng",
            "mua hang",
        ),
        "payment": (
            "payment",
            "payments",
            "refund",
            "invoice",
            "thanh toán",
            "thanh toan",
            "hoàn tiền",
            "hoan tien",
        ),
        "promotion": (
            "promotion",
            "promotions",
            "flash sale",
            "voucher",
            "khuyến mãi",
            "khuyen mai",
            "ưu đãi",
            "uu dai",
        ),
        "news": (
            "news",
            "article",
            "tin tức",
            "tin tuc",
            "bài viết",
            "bai viet",
        ),
        "audit": (
            "audit",
            "logs",
            "trace",
            "nhật ký",
            "nhat ky",
            "truy vết",
            "truy vet",
        ),
    }
    matches = [
        domain_name
        for domain_name, keywords in signals.items()
        if any(keyword in normalized for keyword in keywords)
    ]

    if not matches:
        return validation_error(
            "No MCP domain matched the intent.",
            code="MCP-NO-DOMAIN-MATCH",
        )

    return tool_result(
        {
            "routeType": "single_domain" if len(matches) == 1 else "multi_domain",
            "domains": [_domain_payload(domain_name) for domain_name in matches],
        }
    )


@mcp.tool()
async def check_mcp_domain_health(
    domain: Annotated[
        str | None,
        Field(
            description=(
                "Optional domain name to check. Omit to check every configured "
                "domain MCP health endpoint."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """Check whether one or all managed domain MCP servers are reachable.

    This checks the FastAPI `/health` endpoint of the independent MCP servers,
    not the downstream business service health endpoints.
    """
    if domain:
        normalized = domain.strip().lower()
        domain_config = DOMAIN_PROFILES.get(normalized)
        if domain_config is None:
            return validation_error(
                f"Unknown MCP domain '{domain}'",
                code="MCP-UNKNOWN-DOMAIN",
            )
        return tool_result(
            {
                "domain": normalized,
                "health": await _check_domain_health(domain_config),
            }
        )

    dependencies: dict[str, Any] = {}
    ok = True
    for domain_name, domain_config in DOMAIN_PROFILES.items():
        health = await _check_domain_health(domain_config)
        dependencies[domain_name] = health
        ok = ok and bool(health.get("ok"))

    return tool_result({"ok": ok, "domains": dependencies})


def _domain_payload(domain_name: str) -> dict[str, Any]:
    domain_config = DOMAIN_PROFILES[domain_name]
    url = domain_url(domain_config)
    return {
        "name": domain_config.name,
        "serviceName": domain_config.service_name,
        "description": domain_config.description,
        "url": url,
        "mcpEndpoint": f"{url}/mcp",
        "dependencies": list(domain_config.dependencies),
        "toolModules": list(domain_config.tool_modules),
        "defaultPort": domain_config.default_port,
    }


async def _check_domain_health(domain_config: Any) -> dict[str, Any]:
    base_url = domain_url(domain_config)
    try:
        async with httpx.AsyncClient(timeout=SETTINGS.request_timeout_seconds) as client:
            response = await client.get(f"{base_url}/health")
            payload: Any
            try:
                payload = response.json()
            except ValueError:
                payload = response.text

            return {
                "ok": response.is_success,
                "url": base_url,
                "statusCode": response.status_code,
                "response": payload,
            }
    except httpx.HTTPError as exc:
        return {
            "ok": False,
            "url": base_url,
            "message": str(exc),
            "errorType": exc.__class__.__name__,
        }
