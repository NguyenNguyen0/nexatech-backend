from __future__ import annotations

import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.server import READINESS_CHECKS, app


@pytest.fixture(autouse=True)
def disable_eureka_for_tests():
    previous = SETTINGS.eureka_enabled
    SETTINGS.eureka_enabled = False
    READINESS_CHECKS["eureka"] = "disabled"
    yield
    SETTINGS.eureka_enabled = previous


def test_run_agent_executes_low_sensitivity_flow(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_invoke_tool(tool_name: str, tool_args: dict[str, object]) -> dict[str, object]:
        return {"ok": True, "tool": tool_name, "data": {"items": []}, "raw": {"items": []}}

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


def test_run_agent_accepts_gateway_style_path_and_merges_header_roles(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}

    async def fake_invoke_graph(graph_input: object, *, thread_id: str) -> dict[str, object]:
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