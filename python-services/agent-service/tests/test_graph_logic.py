from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.graph.agent import compile_graph
from app.graph.nodes import agent_think, format_response
from app.graph.tool_registry import plan_tool_call


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


@pytest.mark.asyncio
async def test_agent_think_uses_llm_fallback_when_heuristic_misses(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_llm_plan(_: str):
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


def test_compile_graph_without_checkpointer() -> None:
    graph = compile_graph()

    assert graph is not None
