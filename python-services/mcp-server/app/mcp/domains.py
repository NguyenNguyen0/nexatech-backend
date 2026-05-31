from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.core.settings import SETTINGS
from app.services.audit import AUDIT_API
from app.services.inventory import INVENTORY_API
from app.services.news import NEWS_API
from app.services.order import ORDER_API
from app.services.payment import PAYMENT_API
from app.services.product import PRODUCT_API
from app.services.promotion import PROMOTION_API


@dataclass(frozen=True)
class McpDomain:
    name: str
    service_name: str
    description: str
    tool_modules: tuple[str, ...]
    dependencies: tuple[str, ...]
    default_port: int
    url_setting: str | None = None


DOMAIN_APIS: dict[str, Any] = {
    "news-service": NEWS_API,
    "product-service": PRODUCT_API,
    "inventory-service": INVENTORY_API,
    "order-service": ORDER_API,
    "payment-service": PAYMENT_API,
    "audit-service": AUDIT_API,
    "promotion-service": PROMOTION_API,
}

DOMAIN_PROFILES: dict[str, McpDomain] = {
    "inventory": McpDomain(
        name="inventory",
        service_name="inventory-mcp",
        description="Inventory stock, reservations, movements, warehouse metadata.",
        tool_modules=("inventory",),
        dependencies=("inventory-service",),
        default_port=8101,
        url_setting="inventory_mcp_url",
    ),
    "product": McpDomain(
        name="product",
        service_name="product-mcp",
        description="Product catalog, categories, related products, and reviews.",
        tool_modules=("product",),
        dependencies=("product-service",),
        default_port=8102,
        url_setting="product_mcp_url",
    ),
    "order": McpDomain(
        name="order",
        service_name="order-mcp",
        description="Orders, purchase checks, order statistics, and order traces.",
        tool_modules=("order",),
        dependencies=("order-service",),
        default_port=8103,
        url_setting="order_mcp_url",
    ),
    "payment": McpDomain(
        name="payment",
        service_name="payment-mcp",
        description="Payment lookup, payment statistics, payment creation, and refunds.",
        tool_modules=("payment",),
        dependencies=("payment-service",),
        default_port=8104,
        url_setting="payment_mcp_url",
    ),
    "promotion": McpDomain(
        name="promotion",
        service_name="promotion-mcp",
        description="Promotions and flash-sale campaigns.",
        tool_modules=("promotion",),
        dependencies=("promotion-service",),
        default_port=8105,
        url_setting="promotion_mcp_url",
    ),
    "news": McpDomain(
        name="news",
        service_name="news-mcp",
        description="Public and admin news articles plus news images.",
        tool_modules=("news",),
        dependencies=("news-service",),
        default_port=8106,
        url_setting="news_mcp_url",
    ),
    "audit": McpDomain(
        name="audit",
        service_name="audit-mcp",
        description="Audit log search, audit entries, and cross-service traces.",
        tool_modules=("audit",),
        dependencies=("audit-service",),
        default_port=8107,
        url_setting="audit_mcp_url",
    ),
}

PROFILE_TOOL_MODULES: dict[str, tuple[str, ...]] = {
    "all": tuple(
        module
        for domain in DOMAIN_PROFILES.values()
        for module in domain.tool_modules
    )
    + ("health",),
    "meta": ("meta",),
    **{
        profile_name: domain.tool_modules
        for profile_name, domain in DOMAIN_PROFILES.items()
    },
}


def normalize_profile(raw_profile: str | None) -> str:
    profile = (raw_profile or "all").strip().lower()
    if profile not in PROFILE_TOOL_MODULES:
        return "all"
    return profile


def get_profile_tool_modules(profile: str) -> tuple[str, ...]:
    return PROFILE_TOOL_MODULES[normalize_profile(profile)]


def get_profile_service_apis(profile: str) -> dict[str, Any]:
    normalized = normalize_profile(profile)
    if normalized in {"all", "meta"}:
        return dict(DOMAIN_APIS)

    domain = DOMAIN_PROFILES[normalized]
    return {
        service_name: DOMAIN_APIS[service_name]
        for service_name in domain.dependencies
    }


def domain_url(domain: McpDomain) -> str:
    if domain.url_setting:
        configured = getattr(SETTINGS, domain.url_setting)
        if configured:
            return configured.rstrip("/")
    return f"http://localhost:{domain.default_port}"


def list_domain_metadata() -> list[dict[str, Any]]:
    return [
        {
            "name": domain.name,
            "serviceName": domain.service_name,
            "description": domain.description,
            "url": domain_url(domain),
            "mcpEndpoint": f"{domain_url(domain)}/mcp",
            "dependencies": list(domain.dependencies),
            "toolModules": list(domain.tool_modules),
            "defaultPort": domain.default_port,
        }
        for domain in DOMAIN_PROFILES.values()
    ]
