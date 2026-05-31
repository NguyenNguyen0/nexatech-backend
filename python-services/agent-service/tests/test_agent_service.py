import sys
from pathlib import Path

import httpx
import pytest

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
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.get("/ready")

    assert response.status_code == 200
    payload = response.json()
    assert payload["ready"] is True
    assert payload["service"] == "agent-service"


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
