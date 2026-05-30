from __future__ import annotations

from typing import Any

import httpx

from app.core.logging import correlation_id_ctx
from app.core.settings import SETTINGS


class MCPClient:
    def __init__(
        self,
        base_url: str,
        timeout_seconds: float,
        *,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = httpx.Timeout(timeout_seconds)
        self.transport = transport

    def _build_headers(self) -> dict[str, str]:
        correlation_id = correlation_id_ctx.get()
        headers = {"Accept": "application/json"}
        if correlation_id and correlation_id != "-":
            headers["X-Correlation-Id"] = correlation_id
        return headers

    def _extract_tool_data(self, result: Any) -> Any:
        if not isinstance(result, dict):
            return result

        if "structuredContent" in result:
            return result["structuredContent"]

        content = result.get("content")
        if isinstance(content, list):
            text_parts: list[str] = []
            for item in content:
                if isinstance(item, dict) and item.get("type") == "text" and item.get("text"):
                    text_parts.append(str(item["text"]))
            if text_parts:
                return "\n".join(text_parts)

        return result

    def _normalize_rpc_response(self, response_body: Any, *, tool_name: str) -> dict[str, Any]:
        if not isinstance(response_body, dict):
            return {
                "ok": False,
                "tag": "AGT-MCP-TOOL",
                "message": "MCP server returned a non-object JSON-RPC payload",
                "tool": tool_name,
            }

        if "error" in response_body:
            error = response_body["error"] or {}
            return {
                "ok": False,
                "tag": error.get("tag") or "AGT-MCP-TOOL",
                "message": error.get("message") or f"MCP tool call failed for {tool_name}",
                "tool": tool_name,
                "error": error,
            }

        result = response_body.get("result", response_body)
        if isinstance(result, dict) and result.get("isError"):
            return {
                "ok": False,
                "tag": result.get("tag") or "AGT-MCP-TOOL",
                "message": result.get("message") or f"MCP tool call failed for {tool_name}",
                "tool": tool_name,
                "error": result,
            }

        return {
            "ok": True,
            "tool": tool_name,
            "data": self._extract_tool_data(result),
            "raw": result,
        }

    async def health(self) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=self.timeout, transport=self.transport) as client:
            try:
                response = await client.get(f"{self.base_url}/health", headers=self._build_headers())
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
            "id": correlation_id_ctx.get() if correlation_id_ctx.get() != "-" else "agent-service",
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": tool_args,
            },
        }

        async with httpx.AsyncClient(timeout=self.timeout, transport=self.transport) as client:
            try:
                response = await client.post(
                    f"{self.base_url}/mcp",
                    json=payload,
                    headers=self._build_headers(),
                )
                response.raise_for_status()
                return self._normalize_rpc_response(response.json(), tool_name=tool_name)
            except Exception as exc:
                return {
                    "ok": False,
                    "tag": "AGT-MCP-TOOL",
                    "message": f"MCP tool call failed: {exc.__class__.__name__}",
                    "tool": tool_name,
                }


MCP_CLIENT = MCPClient(SETTINGS.mcp_server_url, SETTINGS.request_timeout_seconds)