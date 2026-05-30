from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.graph.agent import compile_graph
from app.graph.nodes import agent_think
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


def test_compile_graph_without_checkpointer() -> None:
    graph = compile_graph()

    assert graph is not None