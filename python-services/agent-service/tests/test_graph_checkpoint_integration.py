from __future__ import annotations

import os
import sys
import uuid
from pathlib import Path

import pytest
from langgraph.types import Command

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.graph.agent import compile_graph


@pytest.mark.asyncio
async def test_graph_interrupt_and_resume_with_postgres_checkpointer(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    database_url = os.getenv("AGENT_DB_URL")
    if not database_url:
        pytest.skip(
            "AGENT_DB_URL is not configured for Postgres checkpointer integration test"
        )

    try:
        from langgraph.checkpoint.postgres import PostgresSaver
    except Exception:
        pytest.skip("langgraph postgres checkpointer dependencies are unavailable")

    async def fake_invoke_tool(
        tool_name: str, tool_args: dict[str, object]
    ) -> dict[str, object]:
        return {
            "ok": True,
            "tool": tool_name,
            "data": {"tool_args": tool_args},
            "raw": {"tool": tool_name},
        }

    monkeypatch.setattr("app.graph.nodes.MCP_CLIENT.invoke_tool", fake_invoke_tool)

    with PostgresSaver.from_conn_string(database_url) as checkpointer:
        checkpointer.setup()
        graph = compile_graph(checkpointer=checkpointer)

        thread_id = str(uuid.uuid4())
        config = {"configurable": {"thread_id": thread_id}}

        initial_state = {
            "messages": [{"role": "user", "content": "Cho tôi xem audit logs hôm nay"}],
            "caller": {"roles": ["manager"]},
            "checkpointer_enabled": True,
            "tool_results": [],
            "tool_errors": [],
        }

        first_result = await graph.ainvoke(
            initial_state, config=config, durability="sync", version="v2"
        )
        assert getattr(first_result, "interrupts", ())

        resumed_result = await graph.ainvoke(
            Command(resume="approved"), config=config, durability="sync", version="v2"
        )
        resumed_output = resumed_result.value

        assert resumed_output.get("approval_result") == "approved"
        assert resumed_output.get("tool_results")
        assert resumed_output.get("response_text")
