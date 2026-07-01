from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from app.graph.llm_planner import plan_tool_call_with_llm
from app.graph.tool_registry import TOOL_DESCRIPTORS, extract_latest_user_text, plan_tool_call
from app.runtime.delegation_metrics import DELEGATION_METRICS


_CONVERSATIONAL_PATTERNS: tuple[str, ...] = (
    # Greetings
    "xin chào", "chào", "hello", "hi ", "hi!", "hey", "chao ban", "chào bạn",
    # Thanks
    "cảm ơn", "cam on", "thanks", "thank you", "cảm ơn bạn",
    # Identity
    "bạn là ai", "ban la ai", "bạn tên gì", "who are you", "what are you",
    # Capabilities — phrased as open question (not "liệt kê tool")
    "bạn có thể làm gì", "bạn giúp được gì", "can you help", "help me",
    "bạn làm được gì", "ban lam duoc gi",
    # Affirmations / acknowledgements
    "ok", "okay", "được", "được rồi", "great", "got it", "hiểu rồi",
    # Goodbye
    "tạm biệt", "bye", "goodbye",
)


def _is_conversational(input_text: str) -> bool:
    """Return True when the message is a greeting, chitchat, or general conversational
    exchange that does not require any MCP tool invocation."""
    stripped = input_text.strip().casefold()
    if not stripped:
        return True
    # Short messages with no domain keywords are almost always conversational.
    # 6 words ≈ upper bound for a greeting/ack.
    word_count = len(stripped.split())
    if word_count <= 6 and any(p in stripped for p in _CONVERSATIONAL_PATTERNS):
        return True
    return False


def _is_tool_catalog_request(input_text: str) -> bool:
    """Return True only when user is asking ABOUT the tool catalog itself.

    Must NOT fire for requests like "dùng tool list_products_by_category_slug ..."
    where "tool" appears but the intent is to invoke a specific tool.
    """
    normalized = input_text.casefold()

    # If a registered tool name appears in the text, the user is invoking a tool,
    # not asking for a catalog listing — short-circuit immediately.
    if any(d.tool_name in normalized for d in TOOL_DESCRIPTORS):
        return False

    # Treat "tool" as catalog intent only when paired with list/capability terms.
    tool_terms = ("tool", "tools", "công cụ", "cong cu", "khả năng", "kha nang")
    list_terms = (
        "liệt kê",
        "liet ke",
        "danh sách",
        "danh sach",
        "có thể sử dụng",
        "co the su dung",
        "bạn có thể",
        "ban co the",
    )
    return any(term in normalized for term in tool_terms) and any(
        term in normalized for term in list_terms
    )


_DOMAIN_TOOL_ALLOWLIST: dict[str, set[str]] = {
    "meta": {
        "list_mcp_domains",
        "get_mcp_domain",
        "route_to_mcp_domain",
        "check_mcp_domain_health",
        "list_skills",
        "get_skill",
    },
    "inventory": {
        "list_inventory",
        "get_inventory",
        "get_inventory_movements",
        "check_inventory",
        "get_low_stock_items",
        "update_stock",
        "reserve_stock",
        "release_stock",
        "bulk_import_inventory",
        "update_warehouse_info",
    },
    "product": {
        "list_products",
        "search_products",
        "get_product_by_id",
        "get_product_by_slug",
        "list_featured_products",
        "list_products_by_tags",
        "list_products_by_category",
        "list_products_by_category_slug",
        "get_related_products",
        "get_related_products_by_slug",
        "create_product",
        "update_product",
        "delete_product",
        "restore_product",
        "list_reviews",
        "list_reviews_by_product",
        "list_reviews_by_product_slug",
        "get_my_review_for_product",
        "create_review",
        "update_review",
        "delete_review",
        "mark_review_helpful",
    },
    "order": {
        "get_my_orders",
        "get_order",
        "get_order_stats",
        "check_purchased",
        "cancel_order",
        "continue_order_payment",
        "get_order_trace",
    },
    "payment": {
        "get_my_payments",
        "get_payment",
        "get_payment_by_order",
        "get_payment_stats",
        "create_payment",
        "refund_payment",
    },
    "promotion": {
        "list_promotions",
        "list_flash_sales",
    },
    "news": {
        "list_news",
        "list_all_news",
        "get_news_by_slug",
        "get_public_news_by_slug",
        "get_news_images",
        "get_news_image",
    },
    "audit": {
        "search_audit_logs",
    },
}


def _now_iso() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def _normalize_domain(domain: str | None) -> str:
    normalized = (domain or "").strip().lower()
    if not normalized:
        return "general"
    return normalized


def _scope_tools_by_domain(domain: str, available_tools: list[str]) -> list[str]:
    allowlist = _DOMAIN_TOOL_ALLOWLIST.get(domain)
    if not allowlist:
        return list(available_tools)
    return [tool for tool in available_tools if tool in allowlist]


def _append_delegation_trace(
    state: dict[str, Any],
    *,
    domain: str,
    selected_tool: str | None,
    scoped_tools: list[str],
) -> list[dict[str, Any]]:
    trace = list(state.get("delegation_trace") or [])
    trace.append(
        {
            "timestamp": _now_iso(),
            "domain": domain,
            "subagent": f"{domain}_subagent" if domain != "general" else "general_subagent",
            "selected_tool": selected_tool,
            "scoped_tool_count": len(scoped_tools),
        }
    )
    return trace


async def plan_with_subagent(state: dict[str, Any]) -> dict[str, Any]:
    """Plan the next tool call inside a domain-scoped subagent context."""
    selected_tool = state.get("selected_tool")
    if selected_tool:
        return {}

    domain = _normalize_domain(state.get("mcp_target_domain"))
    input_text = state.get("input_text") or extract_latest_user_text(state)
    subagent_name = f"{domain}_subagent" if domain != "general" else "general_subagent"

    if _is_conversational(input_text):
        return {
            "final_status": "conversational",
            "answer_context": {"intent": "conversational", "input_text": input_text},
            "subagent_name": subagent_name,
        }

    available_tools = list(state.get("available_tools") or [])

    if _is_tool_catalog_request(input_text):
        if not available_tools:
            DELEGATION_METRICS.record("tool_catalog_empty_total", domain=domain)
        return {
            "final_status": "tool_catalog",
            "answer_context": {"intent": "tool_catalog", "available_tools": available_tools},
            "subagent_name": subagent_name,
        }

    scoped_tools = _scope_tools_by_domain(domain, available_tools)
    DELEGATION_METRICS.record("delegation_requests_total", domain=domain)
    DELEGATION_METRICS.record("scoped_tools_count", domain=domain, increment=len(scoped_tools))

    # If domain filtering over-constrains a request, gracefully fall back to
    # the runtime-provided tool catalog instead of failing hard.
    effective_tools = scoped_tools or available_tools
    if not scoped_tools and available_tools:
        DELEGATION_METRICS.record("scope_fallback_total", domain=domain)

    plan = plan_tool_call(input_text, available_tools=effective_tools)
    if plan is None:
        DELEGATION_METRICS.record("llm_fallback_total", domain=domain)
        plan = await plan_tool_call_with_llm(
            input_text,
            available_tools=effective_tools,
        )

    if plan is None:
        DELEGATION_METRICS.record("no_matching_tool_total", domain=domain)
        return {
            "final_status": "no_matching_tool",
            "answer_context": {
                "error": "no_matching_tool",
                "domain": domain,
                "available_tools": effective_tools,
            },
            "subagent_name": f"{domain}_subagent" if domain != "general" else "general_subagent",
            "delegation_trace": _append_delegation_trace(
                state,
                domain=domain,
                selected_tool=None,
                scoped_tools=effective_tools,
            ),
        }

    return {
        "selected_tool": plan.tool_name,
        "selected_tool_args": plan.tool_args,
        "required_role": state.get("required_role") or plan.required_role,
        "response_text": None,
        "subagent_name": f"{domain}_subagent" if domain != "general" else "general_subagent",
        "delegation_trace": _append_delegation_trace(
            state,
            domain=domain,
            selected_tool=plan.tool_name,
            scoped_tools=effective_tools,
        ),
    }
