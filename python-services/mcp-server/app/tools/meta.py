from __future__ import annotations

import re
from pathlib import Path
from typing import Annotated, Any

import httpx
from pydantic import Field

from app.core.settings import SETTINGS
from app.mcp.domains import DOMAIN_PROFILES, domain_url, list_domain_metadata
from app.mcp.server import mcp
from app.schemas.tool_contract import tool_result, validation_error


SKILLS_ROOT = Path(__file__).resolve().parents[2] / "skills"
SAFE_SKILL_NAME = re.compile(r"^[a-zA-Z0-9_-]+(?:\.md)?$")


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


@mcp.tool()
async def list_skills(
    domain: Annotated[
        str | None,
        Field(
            description=(
                "Optional skill domain to list. Supported values include shared, "
                "orchestrator, inventory, product, order, payment, promotion, news, audit."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """List skill files available to the meta MCP.

    Use this before `get_skill` when the agent does not know which concrete skill
    file to load. This tool reads static markdown skill files from disk and does
    not execute business logic.
    """
    if domain:
        normalized = domain.strip().lower()
        domain_dir = SKILLS_ROOT / normalized
        if not domain_dir.is_dir():
            return validation_error(
                f"Unknown skill domain '{domain}'",
                code="MCP-UNKNOWN-SKILL-DOMAIN",
            )
        return tool_result(
            {
                "domain": normalized,
                "skills": _list_skill_files(domain_dir),
            }
        )

    domains: dict[str, list[str]] = {}
    for domain_dir in sorted(SKILLS_ROOT.iterdir()):
        if domain_dir.is_dir():
            domains[domain_dir.name] = _list_skill_files(domain_dir)
    return tool_result({"domains": domains})


@mcp.tool()
async def get_skill(
    domain: Annotated[
        str,
        Field(
            description=(
                "Skill domain. Supported values include shared, orchestrator, "
                "inventory, product, order, payment, promotion, news, audit."
            ),
        ),
    ],
    skill_name: Annotated[
        str | None,
        Field(
            description=(
                "Skill file name without extension, for example routing_skill. "
                "Omit to read the domain _index.md."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """Read a business skill markdown file from disk.

    Call this before complex, multi-step, cross-domain, or rollback-sensitive
    tasks. When `skill_name` is omitted, the domain index is returned.
    """
    normalized_domain = domain.strip().lower()
    domain_dir = SKILLS_ROOT / normalized_domain
    if not domain_dir.is_dir():
        return validation_error(
            f"Unknown skill domain '{domain}'",
            code="MCP-UNKNOWN-SKILL-DOMAIN",
        )

    normalized_skill = _normalize_skill_name(skill_name)
    if normalized_skill is None:
        return validation_error(
            f"Invalid skill name '{skill_name}'",
            code="MCP-INVALID-SKILL-NAME",
        )

    skill_path = (domain_dir / normalized_skill).resolve()
    if not str(skill_path).startswith(str(domain_dir.resolve())):
        return validation_error(
            "Invalid skill path",
            code="MCP-INVALID-SKILL-PATH",
        )
    if not skill_path.is_file():
        return validation_error(
            f"Skill '{normalized_skill}' not found in domain '{normalized_domain}'",
            code="MCP-SKILL-NOT-FOUND",
        )

    content = skill_path.read_text(encoding="utf-8")
    return tool_result(
        {
            "domain": normalized_domain,
            "skillName": normalized_skill,
            "content": content,
        }
    )


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


def _list_skill_files(domain_dir: Path) -> list[str]:
    return [
        path.name
        for path in sorted(domain_dir.glob("*.md"))
        if path.is_file()
    ]


def _normalize_skill_name(skill_name: str | None) -> str | None:
    if skill_name is None or not skill_name.strip():
        return "_index.md"

    normalized = skill_name.strip()
    if not SAFE_SKILL_NAME.fullmatch(normalized):
        return None
    if not normalized.endswith(".md"):
        normalized = f"{normalized}.md"
    return normalized


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
