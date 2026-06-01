from __future__ import annotations

import json
import sys
from pathlib import Path

import httpx
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.mcp_client import MCPClient
from app.core.logging import authorization_ctx
from app.core.settings import SETTINGS


@pytest.mark.asyncio
async def test_list_available_tools_uses_mcp_tools_list_method() -> None:
    seen_payload: dict[str, object] = {}
    seen_accept = ""

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal seen_accept
        seen_payload.update(json.loads(request.content))
        seen_accept = request.headers.get("Accept", "")
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": "agent-service",
                "result": {
                    "tools": [
                        {"name": "list_promotions"},
                        {"name": "check_dependency_health"},
                    ],
                },
            },
        )

    client = MCPClient("http://example.test", 5, transport=httpx.MockTransport(handler))

    result = await client.list_available_tools()

    assert seen_payload["method"] == "tools/list"
    assert "application/json" in seen_accept
    assert "text/event-stream" in seen_accept
    assert result == ["list_promotions", "check_dependency_health"]


@pytest.mark.asyncio
async def test_mcp_client_forwards_authorization_header() -> None:
    seen_authorization = ""

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal seen_authorization
        seen_authorization = request.headers.get("Authorization", "")
        return httpx.Response(
            200,
            json={"jsonrpc": "2.0", "id": "agent-service", "result": {"tools": []}},
        )

    client = MCPClient("http://example.test", 5, transport=httpx.MockTransport(handler))
    token = authorization_ctx.set("Bearer token-123")
    try:
        await client.list_available_tools()
    finally:
        authorization_ctx.reset(token)

    assert seen_authorization == "Bearer token-123"


@pytest.mark.asyncio
async def test_invoke_tool_normalizes_jsonrpc_success() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": "agent-service",
                "result": {
                    "content": [{"type": "text", "text": "ok"}],
                    "isError": False,
                },
            },
        )

    client = MCPClient("http://example.test", 5, transport=httpx.MockTransport(handler))

    result = await client.invoke_tool("list_promotions", {})

    assert result["ok"] is True
    assert result["tool"] == "list_promotions"
    assert result["data"] == "ok"


@pytest.mark.asyncio
async def test_invoke_tool_normalizes_jsonrpc_error() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": "agent-service",
                "error": {"message": "tool failed", "tag": "MCP-DOWNSTREAM"},
            },
        )

    client = MCPClient("http://example.test", 5, transport=httpx.MockTransport(handler))

    result = await client.invoke_tool("list_promotions", {})

    assert result["ok"] is False
    assert result["tag"] == "MCP-DOWNSTREAM"
    assert result["tool"] == "list_promotions"


@pytest.mark.asyncio
async def test_list_available_tools_supports_base_url_override() -> None:
    seen_paths: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_paths.append(request.url.path)
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": "agent-service",
                "result": {
                    "tools": [{"name": "list_inventory"}],
                },
            },
        )

    client = MCPClient("http://default.test", 5, transport=httpx.MockTransport(handler))
    tools = await client.list_available_tools(base_url="http://inventory.test")

    assert tools == ["list_inventory"]
    assert seen_paths == ["/mcp/"]


@pytest.mark.asyncio
async def test_list_available_tools_uses_cache_within_ttl(monkeypatch: pytest.MonkeyPatch) -> None:
    call_count = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": "agent-service",
                "result": {
                    "tools": [{"name": "list_promotions"}],
                },
            },
        )

    monkeypatch.setattr(SETTINGS, "mcp_tool_catalog_cache_ttl_seconds", 30)
    client = MCPClient("http://example.test", 5, transport=httpx.MockTransport(handler))

    first = await client.list_available_tools()
    second = await client.list_available_tools()

    assert first == ["list_promotions"]
    assert second == ["list_promotions"]
    assert call_count == 1
