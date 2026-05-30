from __future__ import annotations

import sys
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.server import READINESS_CHECKS, AgentRunRequest, app, get_conversation_thread, run_agent_stream


@pytest.fixture(autouse=True)
def disable_eureka_for_tests():
    previous = SETTINGS.eureka_enabled
    SETTINGS.eureka_enabled = False
    READINESS_CHECKS["eureka"] = "disabled"
    yield
    SETTINGS.eureka_enabled = previous


def test_run_agent_executes_low_sensitivity_flow(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_invoke_tool(
        tool_name: str, tool_args: dict[str, object]
    ) -> dict[str, object]:
        return {
            "ok": True,
            "tool": tool_name,
            "data": {"items": []},
            "raw": {"items": []},
        }

    monkeypatch.setattr("app.graph.nodes.MCP_CLIENT.invoke_tool", fake_invoke_tool)

    with TestClient(app) as client:
        response = client.post(
            "/api/v1/agent/runs",
            json={"message": "Cho tôi xem các chương trình khuyến mãi hiện tại"},
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "completed"
    assert payload["output"]["selected_tool"] == "list_promotions"
    assert payload["interrupts"] == []


def test_resume_agent_requires_checkpointer() -> None:
    READINESS_CHECKS["checkpointer"] = "disabled"
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/agent/runs/resume",
            json={"threadId": "demo-thread", "decision": "approved"},
        )

    assert response.status_code == 503
    assert response.json()["message"] == "Resume requires the LangGraph checkpointer"


def test_run_agent_accepts_gateway_style_path_and_merges_header_roles(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    async def fake_invoke_graph(
        graph_input: object, *, thread_id: str
    ) -> dict[str, object]:
        assert isinstance(graph_input, dict)
        captured["caller"] = graph_input.get("caller")
        return {
            "threadId": thread_id,
            "status": "completed",
            "output": {"selected_tool": "list_promotions"},
            "interrupts": [],
            "state": {"values": {}, "next": [], "interrupts": []},
        }

    monkeypatch.setattr("app.server._invoke_graph", fake_invoke_graph)

    with TestClient(app) as client:
        response = client.post(
            "/runs",
            json={
                "message": "liet ke khuyen mai",
                "caller": {"roles": ["operator"]},
            },
            headers={
                "X-User-Id": "user-123",
                "X-User-Role": "manager,operator",
                "X-User-Email": "admin@nexatech.local",
            },
        )

    assert response.status_code == 200
    assert captured["caller"] == {
        "roles": ["operator", "manager"],
        "userId": "user-123",
        "email": "admin@nexatech.local",
    }


def test_stream_endpoint_emits_stable_events(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_invoke_graph(
        graph_input: object, *, thread_id: str
    ) -> dict[str, object]:
        return {
            "threadId": thread_id,
            "status": "completed",
            "output": {
                "selected_tool": "list_promotions",
                "response_text": "Da xu ly yeu cau.",
                "tool_errors": [],
            },
            "interrupts": [],
            "state": {
                "values": {
                    "selected_tool": "list_promotions",
                    "response_text": "Da xu ly yeu cau.",
                    "tool_errors": [],
                },
                "next": [],
                "interrupts": [],
            },
        }

    monkeypatch.setattr("app.server._invoke_graph", fake_invoke_graph)

    with TestClient(app) as client:
        with client.stream(
            "POST",
            "/api/v1/agent/runs/stream",
            json={
                "message": "xin chao",
                "runId": "run-test-1",
                "threadId": "thread-test-1",
            },
            headers={"X-User-Id": "user-123", "X-User-Role": "operator"},
        ) as response:
            payload = response.read().decode("utf-8")

    assert response.status_code == 200
    assert "event: run.started" in payload
    assert "event: tool.started" in payload
    assert "event: tool.completed" in payload
    assert "event: message.chunk" in payload
    assert "event: run.completed" in payload


@pytest.mark.asyncio
async def test_stream_endpoint_persists_conversation_when_mcp_has_no_tools(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class _FakeStore:
        def __init__(self) -> None:
            self.events: dict[str, list[dict[str, object]]] = {}

        async def append_event(
            self,
            *,
            thread_id: str,
            event_type: str,
            payload: dict[str, object],
            correlation_id: str | None,
            user_id: str | None,
            user_role: str | None,
            user_email: str | None,
        ) -> None:
            self.events.setdefault(thread_id, []).append(
                {
                    "eventType": event_type,
                    "payload": payload,
                    "correlationId": correlation_id,
                    "userId": user_id,
                    "userRole": user_role,
                    "userEmail": user_email,
                }
            )

        async def get_thread(self, *, thread_id: str, limit: int = 100):
            events = self.events.get(thread_id)
            if events is None:
                return None
            return {"threadId": thread_id, "events": events[-limit:]}

        async def close(self):
            return None

    async def no_available_tools(_: str) -> list[str]:
        return []

    monkeypatch.setattr("app.server._log_connected_mcp_tools", no_available_tools)
    app.state.conversation_store = _FakeStore()
    raw_request = SimpleNamespace(
        headers={
            "X-User-Id": "user-123",
            "X-User-Role": "operator",
        },
        state=SimpleNamespace(correlation_id="corr-123"),
    )

    response = await run_agent_stream(
        AgentRunRequest(
            message="liet ke tool",
            runId="run-no-tools",
            threadId="thread-no-tools",
        ),
        raw_request,
    )
    payload = "".join([chunk async for chunk in response.body_iterator])
    conversation = await get_conversation_thread("thread-no-tools", limit=100)

    assert "event: run.error" in payload
    events = conversation["events"]
    assert [event["eventType"] for event in events] == ["user_message", "run_error"]
    assert events[1]["payload"]["reason"] == "mcp_tools_unavailable"


def test_interrupt_route_requires_checkpointer() -> None:
    READINESS_CHECKS["checkpointer"] = "disabled"
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/agent/runs/interrupts",
            json={"threadId": "demo-thread"},
        )

    assert response.status_code == 503
    assert (
        response.json()["message"]
        == "Interrupt state requires the LangGraph checkpointer"
    )


def test_list_conversations_requires_user_header_when_query_missing() -> None:
    class _FakeStore:
        async def list_threads_by_user(self, *, user_id: str, limit: int = 20):
            return []

        async def close(self):
            return None

    with TestClient(app) as client:
        client.app.state.conversation_store = _FakeStore()
        response = client.get("/api/v1/agent/conversations")

    assert response.status_code == 400
    assert response.json()["message"] == "X-User-Id header is required"
