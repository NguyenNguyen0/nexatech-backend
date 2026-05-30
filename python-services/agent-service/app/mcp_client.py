from __future__ import annotations

from typing import Any

import httpx

from app.core.settings import SETTINGS


class MCPClient:
    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = httpx.Timeout(timeout_seconds)

    async def health(self) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                response = await client.get(f"{self.base_url}/health")
                response.raise_for_status()
                return {"ok": True, "result": response.json()}
            except Exception as exc:
                return {
                    "ok": False,
                    "tag": "AGT-MCP-HEALTH",
                    "message": f"MCP health check failed: {exc.__class__.__name__}",
                }

    async def invoke_tool(self, tool_name: str, tool_args: dict[str, Any]) -> dict[str, Any]:
        payload = {
            "jsonrpc": "2.0",
            "id": "agent-service",
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": tool_args,
            },
        }

        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                response = await client.post(f"{self.base_url}/mcp", json=payload)
                response.raise_for_status()
                return {"ok": True, "result": response.json()}
            except Exception as exc:
                return {
                    "ok": False,
                    "tag": "AGT-MCP-TOOL",
                    "message": f"MCP tool call failed: {exc.__class__.__name__}",
                    "tool": tool_name,
                }


MCP_CLIENT = MCPClient(SETTINGS.mcp_server_url, SETTINGS.request_timeout_seconds)