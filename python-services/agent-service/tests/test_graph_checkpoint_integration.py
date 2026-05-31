from __future__ import annotations

import os
import sys
import uuid
from pathlib import Path

import httpx
import pytest
from langgraph.types import Command

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.graph.agent import compile_graph
from app.server import READINESS_CHECKS, app


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
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
    except Exception:
        pytest.skip("langgraph async postgres checkpointer dependencies are unavailable")

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

    async def fake_synthesize_response(_: dict[str, object]) -> str:
        return "Đã thực thi tool sau khi được phê duyệt."

    monkeypatch.setattr(
        "app.graph.nodes.synthesize_agent_response",
        fake_synthesize_response,
    )

    async with AsyncPostgresSaver.from_conn_string(database_url) as checkpointer:
        await checkpointer.setup()
        graph = compile_graph(checkpointer=checkpointer)

        thread_id = str(uuid.uuid4())
        config = {"configurable": {"thread_id": thread_id}}

        initial_state = {
            "messages": [{"role": "user", "content": "Tạo payment cho đơn ORD-001"}],
            "caller": {"roles": ["operator"]},
            "checkpointer_enabled": True,
            "selected_tool": "create_payment",
            "selected_tool_args": {"order_id": "ORD-001"},
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


@pytest.mark.asyncio
async def test_postgres_checkpointer_persists_run_state_across_api_requests(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    database_url = os.getenv("AGENT_DB_URL")
    if not database_url:
        pytest.skip(
            "AGENT_DB_URL is not configured for Postgres checkpointer integration test"
        )

    try:
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
    except Exception:
        pytest.skip("langgraph async postgres checkpointer dependencies are unavailable")

    async def fake_available_tools(_: str) -> list[str]:
        return ["create_payment"]

    async def fake_invoke_tool(
        tool_name: str, tool_args: dict[str, object]
    ) -> dict[str, object]:
        return {
            "ok": True,
            "tool": tool_name,
            "data": {"tool_args": tool_args},
            "raw": {"tool": tool_name},
        }

    monkeypatch.setattr("app.server._log_connected_mcp_tools", fake_available_tools)
    monkeypatch.setattr("app.graph.nodes.MCP_CLIENT.invoke_tool", fake_invoke_tool)

    async def fake_synthesize_response(_: dict[str, object]) -> str:
        return "Đã tạo payment sau khi được phê duyệt."

    monkeypatch.setattr(
        "app.graph.nodes.synthesize_agent_response",
        fake_synthesize_response,
    )

    previous_graph = getattr(app.state, "compiled_graph", None)
    previous_checkpointer_state = READINESS_CHECKS.get("checkpointer", "pending")
    previous_graph_state = READINESS_CHECKS.get("graph", "pending")

    try:
        async with AsyncPostgresSaver.from_conn_string(database_url) as checkpointer:
            await checkpointer.setup()
            app.state.compiled_graph = compile_graph(checkpointer=checkpointer)
            READINESS_CHECKS["checkpointer"] = "ok"
            READINESS_CHECKS["graph"] = "ok"

            thread_id = str(uuid.uuid4())
            async with httpx.AsyncClient(
                transport=httpx.ASGITransport(app=app),
                base_url="http://testserver",
            ) as client:
                first_response = await client.post(
                    "/api/v1/agent/runs",
                    json={
                        "message": "Tạo payment cho đơn hàng ORD-123",
                        "threadId": thread_id,
                        "selectedTool": "create_payment",
                        "selectedToolArgs": {"order_id": "ORD-123"},
                    },
                )
                interrupts_response = await client.post(
                    "/api/v1/agent/runs/interrupts",
                    json={"threadId": thread_id},
                )
                state_response = await client.get(f"/api/v1/agent/runs/{thread_id}")
                resume_response = await client.post(
                    "/api/v1/agent/runs/resume",
                    json={"threadId": thread_id, "decision": "approved"},
                )
    finally:
        app.state.compiled_graph = previous_graph
        READINESS_CHECKS["checkpointer"] = previous_checkpointer_state
        READINESS_CHECKS["graph"] = previous_graph_state

    assert first_response.status_code == 200
    assert first_response.json()["status"] == "interrupted"
    assert first_response.json()["interrupts"]

    assert interrupts_response.status_code == 200
    assert interrupts_response.json()["interrupts"]

    assert state_response.status_code == 200
    state_payload = state_response.json()["state"]
    assert state_payload["interrupts"]
    assert state_payload["next"]

    assert resume_response.status_code == 200
    resumed = resume_response.json()
    assert resumed["status"] == "completed"
    assert resumed["state"]["values"]["approval_result"] == "approved"
    assert resumed["state"]["values"]["tool_results"]
