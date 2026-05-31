import sys
from pathlib import Path

import httpx
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.server import READINESS_CHECKS, app
import app.server as server


@pytest.fixture(autouse=True)
def disable_eureka_for_tests():
    previous = SETTINGS.eureka_enabled
    SETTINGS.eureka_enabled = False
    READINESS_CHECKS["eureka"] = "disabled"
    yield
    SETTINGS.eureka_enabled = previous


@pytest.mark.asyncio
async def test_health_endpoint() -> None:
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.headers["X-Correlation-Id"]


@pytest.mark.asyncio
async def test_ready_endpoint() -> None:
    READINESS_CHECKS["checkpointer"] = "ok"
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.get("/ready")

    assert response.status_code == 200
    payload = response.json()
    assert payload["ready"] is True
    assert payload["service"] == "agent-service"
    assert payload["degraded"] is False


@pytest.mark.asyncio
async def test_ready_endpoint_reports_degraded_when_checkpointer_disabled() -> None:
    READINESS_CHECKS["checkpointer"] = "disabled"
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.get("/ready")

    assert response.status_code == 200
    payload = response.json()
    assert payload["ready"] is True
    assert payload["degraded"] is True
    assert payload["checks"]["checkpointer"] == "disabled"


@pytest.mark.asyncio
async def test_graph_runtime_falls_back_to_stateless_when_checkpointer_fails(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    sentinel_graph = object()
    previous_checkpointer_state = READINESS_CHECKS.get("checkpointer", "pending")
    previous_graph_state = READINESS_CHECKS.get("graph", "pending")

    async def raise_checkpointer_error(_: str | None):
        raise RuntimeError("postgres unavailable")

    monkeypatch.setattr(server, "initialize_checkpointer", raise_checkpointer_error)
    monkeypatch.setattr(server, "compile_graph", lambda checkpointer=None: sentinel_graph)
    monkeypatch.setattr(server, "attach_copilotkit_middleware", lambda *_, **__: False)
    monkeypatch.setattr(SETTINGS, "agent_db_url", "postgresql://invalid")

    await server._initialize_graph_runtime(app)

    assert app.state.compiled_graph is sentinel_graph
    assert READINESS_CHECKS["graph"] == "ok"
    assert READINESS_CHECKS["checkpointer"] == "error: RuntimeError"

    READINESS_CHECKS["checkpointer"] = previous_checkpointer_state
    READINESS_CHECKS["graph"] = previous_graph_state


@pytest.mark.asyncio
async def test_root_endpoint_describes_skeleton_layout() -> None:
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.get("/")

    assert response.status_code == 200
    payload = response.json()
    assert payload["message"] == "agent-service skeleton is running"
    assert "app/graph" in payload["layout"]
