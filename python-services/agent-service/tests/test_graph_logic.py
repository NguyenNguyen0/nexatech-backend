from __future__ import annotations

import sys
import time
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.graph.agent import compile_graph
from app.graph.llm_planner import (
    _build_tool_catalog,
    _extract_tool_call_from_verbose_text,
    _planner_system_prompt,
)
from app.graph.nodes import (
    agent_think,
    classify_request,
    domain_executor,
    domain_router,
    format_response,
    needs_approval,
)
from app.graph.routing.keyword_matcher import KeywordToolMatcher
from app.graph.tool_registry import TOOL_DESCRIPTORS
from app.graph.tool_registry import plan_tool_call
from app.runtime.delegation_metrics import DELEGATION_METRICS


def test_plan_tool_call_matches_promotions() -> None:
    plan = plan_tool_call("Cho tôi xem các chương trình khuyến mãi hiện tại")

    assert plan is not None
    assert plan.tool_name == "list_promotions"
    assert plan.required_role == "operator"


@pytest.mark.asyncio
async def test_agent_think_selects_news_listing_tool() -> None:
    result = await agent_think(
        {
            "messages": [{"role": "user", "content": "Liệt kê tin tức mới nhất"}],
            "caller": {"roles": ["operator"]},
        }
    )

    assert result["selected_tool"] == "list_news"
    assert result["selected_tool_args"] == {"page": 0, "size": 10}


def test_plan_tool_call_matches_all_news_listing() -> None:
    plan = plan_tool_call("Liệt kê toàn bộ tin tức bao gồm bản nháp")

    assert plan is not None
    assert plan.tool_name == "list_all_news"
    assert plan.tool_args == {"page": 0, "size": 20}


def test_required_arg_news_tools_are_reserved_for_llm_planner() -> None:
    plan = plan_tool_call("Xem chi tiết tin tức theo slug summer-sale")

    assert plan is not None
    assert plan.tool_name == "list_news"


def test_plan_tool_call_matches_inventory_listing() -> None:
    plan = plan_tool_call("Liệt kê tồn kho hiện tại")

    assert plan is not None
    assert plan.tool_name == "list_inventory"
    assert plan.tool_args == {"params": {}}


def test_plan_tool_call_matches_low_stock_listing() -> None:
    plan = plan_tool_call("Sản phẩm nào sắp hết hàng?")

    assert plan is not None
    assert plan.tool_name == "get_low_stock_items"


def test_plan_tool_call_matches_product_category_slug_without_llm() -> None:
    plan = plan_tool_call("Liệt kê sản phẩm danh mục hera-pc")

    assert plan is not None
    assert plan.tool_name == "list_products_by_category_slug"
    assert plan.tool_args == {"slug": "hera-pc"}


def test_plan_tool_call_filters_by_available_tools() -> None:
    plan = plan_tool_call(
        "Liệt kê sản phẩm danh mục hera-pc",
        available_tools=["list_products"],
    )

    assert plan is not None
    assert plan.tool_name == "list_products"


def test_plan_tool_call_returns_none_when_matched_tool_is_unavailable() -> None:
    plan = plan_tool_call(
        "Dùng tool list_products_by_category_slug danh mục hera-pc",
        available_tools=["list_products"],
    )

    assert plan is None


def test_plan_tool_call_extracts_order_id() -> None:
    plan = plan_tool_call(
        "Xem chi tiết đơn hàng ORD-12345",
        available_tools=["get_order"],
    )

    assert plan is not None
    assert plan.tool_name == "get_order"
    assert plan.tool_args == {"order_id": "ord-12345"}


def test_plan_tool_call_extracts_payment_id() -> None:
    plan = plan_tool_call(
        "Tra cứu thanh toán PAY-999",
        available_tools=["get_payment"],
    )

    assert plan is not None
    assert plan.tool_name == "get_payment"
    assert plan.tool_args == {"payment_id": "pay-999"}


def test_plan_tool_call_extracts_inventory_product_id() -> None:
    plan = plan_tool_call(
        "Kiểm tra tồn kho sản phẩm PRD-42",
        available_tools=["get_inventory", "list_inventory"],
    )

    assert plan is not None
    assert plan.tool_name == "get_inventory"
    assert plan.tool_args == {"product_id": "prd-42"}


def test_plan_tool_call_routes_meta_skill_lookup() -> None:
    plan = plan_tool_call(
        "get skill inventory fulfillment_skill",
        available_tools=["get_skill", "list_skills"],
    )

    assert plan is not None
    assert plan.tool_name == "get_skill"
    assert plan.tool_args == {
        "domain": "inventory",
        "skill_name": "fulfillment_skill",
    }


def test_keyword_matcher_p95_under_50ms() -> None:
    matcher = KeywordToolMatcher(TOOL_DESCRIPTORS)
    samples = [
        "Liệt kê sản phẩm danh mục hera-pc",
        "Cho tôi xem tồn kho hiện tại",
        "Tra cứu thanh toán PAY-999",
        "Xem chi tiết đơn hàng ORD-12345",
        "get skill inventory fulfillment_skill",
        "Cho tôi xem các chương trình khuyến mãi hiện tại",
    ] * 25

    durations: list[float] = []
    for sample in samples:
        start = time.perf_counter()
        matcher.match(sample)
        durations.append((time.perf_counter() - start) * 1000)

    p95 = sorted(durations)[int(len(durations) * 0.95)]
    assert p95 < 50


def test_planner_prompt_forbids_verbose_thinking_leak() -> None:
    prompt = _planner_system_prompt()

    assert "Khong giai thich truoc" in prompt
    assert "DUY NHAT mot JSON object" in prompt
    assert "list_products_by_category_slug" in prompt


def test_verbose_tool_call_text_can_be_recovered_as_plan() -> None:
    parsed = _extract_tool_call_from_verbose_text(
        'Toi se goi tool list_products_by_category_slug(category_slug="hera-pc")',
        ["list_products_by_category_slug"],
    )

    assert parsed == {
        "tool_name": "list_products_by_category_slug",
        "tool_args": {"slug": "hera-pc"},
        "required_role": "operator",
        "rationale": "parsed_from_verbose_tool_call",
    }


@pytest.mark.asyncio
async def test_news_tool_catalog_includes_detail_tools() -> None:
    catalog = await _build_tool_catalog(
        [
            "list_news",
            "list_all_news",
            "get_news_by_slug",
            "get_public_news_by_slug",
            "get_news_images",
            "get_news_image",
        ]
    )

    assert "list_all_news" in catalog
    assert "get_news_by_slug" in catalog
    assert "get_public_news_by_slug" in catalog
    assert "get_news_images" in catalog
    assert "get_news_image" in catalog


@pytest.mark.asyncio
async def test_inventory_tool_catalog_includes_read_and_write_tools() -> None:
    catalog = await _build_tool_catalog(
        [
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
        ]
    )

    assert "list_inventory" in catalog
    assert "get_inventory" in catalog
    assert "get_inventory_movements" in catalog
    assert "check_inventory" in catalog
    assert "get_low_stock_items" in catalog
    assert "update_stock" in catalog
    assert "reserve_stock" in catalog
    assert "release_stock" in catalog
    assert "bulk_import_inventory" in catalog
    assert "update_warehouse_info" in catalog


@pytest.mark.asyncio
async def test_agent_think_uses_llm_fallback_when_heuristic_misses(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_llm_plan(_: str, **__: object):
        from app.graph.tool_registry import ToolPlan

        return ToolPlan(
            tool_name="list_promotions",
            tool_args={},
            required_role="operator",
            rationale="llm",
        )

    monkeypatch.setattr("app.graph.nodes.plan_tool_call_with_llm", fake_llm_plan)

    result = await agent_think(
        {
            "messages": [
                {"role": "user", "content": "Giúp tôi chọn hành động marketing phù hợp"}
            ],
            "caller": {"roles": ["operator"]},
        }
    )

    assert result["selected_tool"] == "list_promotions"
    assert result["selected_tool_args"] == {}


@pytest.mark.asyncio
async def test_agent_think_lists_available_tools_for_capability_question() -> None:
    result = await agent_think(
        {
            "messages": [
                {
                    "role": "user",
                    "content": "hãy liệt kê các tool mà bạn có thể sử dụng thành 1 danh sách",
                }
            ],
            "available_tools": ["list_promotions", "list_flash_sales"],
            "caller": {"roles": ["operator"]},
        }
    )

    assert result["final_status"] == "tool_catalog"
    assert result["answer_context"] == {
        "intent": "tool_catalog",
        "available_tools": ["list_promotions", "list_flash_sales"],
    }
    assert "response_text" not in result


@pytest.mark.asyncio
async def test_format_response_uses_llm_synthesis(monkeypatch: pytest.MonkeyPatch) -> None:
    captured_state: dict[str, object] = {}

    async def fake_synthesize(state: dict[str, object]) -> str:
        captured_state.update(state)
        return "Danh sách tool gồm list_promotions và list_flash_sales."

    monkeypatch.setattr("app.graph.nodes.synthesize_agent_response", fake_synthesize)

    result = await format_response(
        {
            "input_text": "hãy liệt kê các tool",
            "final_status": "tool_catalog",
            "available_tools": ["list_promotions", "list_flash_sales"],
            "answer_context": {
                "intent": "tool_catalog",
                "available_tools": ["list_promotions", "list_flash_sales"],
            },
        }
    )

    assert captured_state["final_status"] == "tool_catalog"
    assert result["response_text"] == "Danh sách tool gồm list_promotions và list_flash_sales."


@pytest.mark.asyncio
async def test_audit_search_is_read_only_and_does_not_require_hitl() -> None:
    result = await needs_approval(
        {
            "selected_tool": "search_audit_logs",
            "checkpointer_enabled": False,
        }
    )

    assert result == {"request_sensitivity": "low"}


@pytest.mark.asyncio
async def test_medium_sensitivity_requires_checkpointer_for_hitl() -> None:
    result = await needs_approval(
        {
            "selected_tool": "create_payment",
            "checkpointer_enabled": False,
        }
    )

    assert result["request_sensitivity"] == "medium"
    assert result["final_status"] == "approval_unavailable"


@pytest.mark.asyncio
async def test_domain_router_routes_meta_intent_to_meta_subagent() -> None:
    result = await domain_router(
        {
            "messages": [{"role": "user", "content": "hãy đọc skill inventory"}],
            "mcp_target_domain": "product",
            "mcp_target_url": "http://localhost:8102",
            "mcp_meta_url": "http://localhost:8094",
        }
    )

    assert result["mcp_target_domain"] == "meta"
    assert result["mcp_target_url"] == "http://localhost:8094"
    assert result["subagent_name"] == "meta_subagent"


@pytest.mark.asyncio
async def test_domain_executor_scopes_tools_for_target_domain() -> None:
    result = await domain_executor(
        {
            "messages": [{"role": "user", "content": "liệt kê sản phẩm"}],
            "mcp_target_domain": "product",
            "available_tools": ["list_products", "get_order"],
            "tool_results": [],
            "tool_errors": [],
        }
    )

    assert result["selected_tool"] == "list_products"
    assert result["subagent_name"] == "product_subagent"
    assert result["delegation_trace"][-1]["domain"] == "product"
    assert result["delegation_trace"][-1]["selected_tool"] == "list_products"


@pytest.mark.asyncio
async def test_domain_executor_returns_no_matching_when_scoped_tools_cannot_plan(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_llm_plan(_: str, **__: object):
        return None

    monkeypatch.setattr("app.graph.subagents.plan_tool_call_with_llm", fake_llm_plan)

    result = await domain_executor(
        {
            "messages": [{"role": "user", "content": "liệt kê sản phẩm"}],
            "mcp_target_domain": "audit",
            "available_tools": ["search_audit_logs"],
            "tool_results": [],
            "tool_errors": [],
        }
    )

    assert result["final_status"] == "no_matching_tool"
    assert result["subagent_name"] == "audit_subagent"
    assert result["answer_context"]["domain"] == "audit"
    assert result["answer_context"]["error"] == "no_matching_tool"


@pytest.mark.asyncio
async def test_domain_executor_updates_delegation_metrics() -> None:
    DELEGATION_METRICS.reset()

    result = await domain_executor(
        {
            "messages": [{"role": "user", "content": "liệt kê sản phẩm"}],
            "mcp_target_domain": "product",
            "available_tools": ["list_products", "get_order"],
            "tool_results": [],
            "tool_errors": [],
        }
    )

    snapshot = DELEGATION_METRICS.snapshot()
    assert result["selected_tool"] == "list_products"
    assert snapshot["totals"]["delegation_requests_total"] >= 1
    assert snapshot["byDomain"]["product"]["delegation_requests_total"] >= 1
    assert snapshot["byDomain"]["product"]["scoped_tools_count"] >= 1


def test_compile_graph_without_checkpointer() -> None:
    graph = compile_graph()

    assert graph is not None


@pytest.mark.asyncio
async def test_classify_request_resets_stale_scratch_state_from_previous_turn() -> None:
    stale_state = {
        "messages": [{"role": "user", "content": "sản phẩm nào bán chạy nhất?"}],
        "caller": {"roles": ["operator"]},
        "selected_tool": "list_products",
        "selected_tool_args": {"params": {}},
        "response_text": "Hiện tại có 803 sản phẩm trong hệ thống.",
        "final_status": "completed",
        "tool_results": [{"tool": "list_products", "result": {"ok": True}}],
        "tool_errors": [{"tool": "list_products", "error": {"ok": False}}],
        "pending_approval": {"tool_name": "list_products"},
        "approval_result": "approved",
        "approval_request_id": "old-request-id",
        "expires_at": "2020-01-01T00:00:00Z",
        "timeout_seconds": 30,
        "answer_context": {"intent": "conversational"},
        "last_tool_call": {"id": "old-call-id", "name": "list_products"},
        "subagent_name": "product_subagent",
    }

    result = await classify_request(stale_state)

    assert result["selected_tool"] is None
    assert result["selected_tool_args"] == {}
    assert result["response_text"] is None
    assert result["final_status"] is None
    assert result["tool_results"] == []
    assert result["tool_errors"] == []
    assert result["pending_approval"] is None
    assert result["approval_result"] is None
    assert result["approval_request_id"] is None
    assert result["expires_at"] is None
    assert result["timeout_seconds"] is None
    assert result["answer_context"] == {}
    assert result["last_tool_call"] is None
    assert result["subagent_name"] is None


@pytest.mark.asyncio
async def test_classify_request_seeds_selected_tool_from_forced_tool() -> None:
    result = await classify_request(
        {
            "messages": [{"role": "user", "content": "bất kỳ nội dung nào"}],
            "caller": {"roles": ["operator"]},
            "forced_tool": "create_payment",
            "forced_tool_args": {"order_id": "ORD-001"},
            # leftover from a previous turn — must not win over forced_tool
            "selected_tool": "list_products",
        }
    )

    assert result["selected_tool"] == "create_payment"
    assert result["selected_tool_args"] == {"order_id": "ORD-001"}
