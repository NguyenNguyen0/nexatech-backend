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