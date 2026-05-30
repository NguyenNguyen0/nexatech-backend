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


def test_health_endpoint() -> None:
    client = TestClient(app)
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.headers["X-Correlation-Id"]


def test_ready_endpoint() -> None:
    client = TestClient(app)
    response = client.get("/ready")

    assert response.status_code == 200
    payload = response.json()
    assert payload["ready"] is True
    assert payload["service"] == "mcp-server"


def test_root_endpoint_describes_skeleton_layout() -> None:
    client = TestClient(app)
    response = client.get("/")

    assert response.status_code == 200
    payload = response.json()
    assert payload["message"] == "mcp-server skeleton is running"
    assert "app/mcp" in payload["layout"]
