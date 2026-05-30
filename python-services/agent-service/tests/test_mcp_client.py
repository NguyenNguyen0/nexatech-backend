from __future__ import annotations

import json
import sys
from pathlib import Path

import httpx
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.mcp_client import MCPClient


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
