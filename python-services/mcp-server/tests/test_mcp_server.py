import httpx
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.core.http import ServiceClient, ServiceDefinition
from app.core.logging import user_email_ctx, user_id_ctx, user_role_ctx
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


@pytest.mark.asyncio
async def test_service_client_retries_transient_failure_then_succeeds() -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise httpx.ConnectError("temporary network failure", request=request)
        return httpx.Response(200, json={"result": {"ok": True}})

    client = ServiceClient(
        ServiceDefinition(name="demo-service", base_url="http://example.test", retry_attempts=2),
        transport=httpx.MockTransport(handler),
    )

    result = await client.request("GET", "/health")

    assert result == {"ok": True}
    assert attempts == 2


@pytest.mark.asyncio
async def test_service_client_returns_structured_error_after_retries() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(503, json={"tag": "PRD-503", "message": "service unavailable"})

    client = ServiceClient(
        ServiceDefinition(name="demo-service", base_url="http://example.test", retry_attempts=1),
        transport=httpx.MockTransport(handler),
    )

    result = await client.request("GET", "/health")

    assert result["ok"] is False
    assert result["error"]["statusCode"] == 503
    assert result["error"]["tag"] == "PRD-503"


@pytest.mark.asyncio
async def test_service_client_forwards_user_headers_from_context() -> None:
    seen_headers: dict[str, str] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen_headers["X-User-Id"] = request.headers.get("X-User-Id", "")
        seen_headers["X-User-Role"] = request.headers.get("X-User-Role", "")
        seen_headers["X-User-Email"] = request.headers.get("X-User-Email", "")
        return httpx.Response(200, json={"result": {"ok": True}})

    client = ServiceClient(
        ServiceDefinition(name="demo-service", base_url="http://example.test", retry_attempts=1),
        transport=httpx.MockTransport(handler),
    )

    token_user_id = user_id_ctx.set("user-abc")
    token_user_role = user_role_ctx.set("manager")
    token_user_email = user_email_ctx.set("user@nexatech.local")
    try:
        result = await client.request("GET", "/health")
    finally:
        user_id_ctx.reset(token_user_id)
        user_role_ctx.reset(token_user_role)
        user_email_ctx.reset(token_user_email)

    assert result == {"ok": True}
    assert seen_headers["X-User-Id"] == "user-abc"
    assert seen_headers["X-User-Role"] == "manager"
    assert seen_headers["X-User-Email"] == "user@nexatech.local"
