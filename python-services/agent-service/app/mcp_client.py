from __future__ import annotations

import logging
import time
from typing import Any

import httpx

from app.core.logging import (
    authorization_ctx,
    correlation_id_ctx,
    user_email_ctx,
    user_id_ctx,
    user_role_ctx,
)
from app.core.settings import SETTINGS


logger = logging.getLogger("agent-mcp")


class MCPClient:
    async def list_available_tools(
        self,
        *,
        base_url: str | None = None,
        use_cache: bool = True,
    ) -> list[str]:
        """Query MCP server for available tool names (tools that are actually registered and callable)."""
        target_base_url = self._resolve_base_url(base_url)
        if use_cache:
            cached = self._get_cached_tools(target_base_url)
            if cached is not None:
                return cached

        async with httpx.AsyncClient(
            timeout=self.timeout,
            transport=self.transport,
            follow_redirects=True,
        ) as client:
            try:
                response = await client.post(
                    f"{target_base_url}/mcp/",
                    json={
                        "jsonrpc": "2.0",
                        "id": correlation_id_ctx.get() or "agent-service",
                        "method": "tools/list",
                        "params": {},
                    },
                    headers=self._build_headers(),
                )
                response.raise_for_status()
                tool_names = self._extract_tool_names(response.json())
                if use_cache:
                    self._set_cached_tools(target_base_url, tool_names)
                return tool_names
            except Exception as exc:
                logger.warning(
                    "[MCP-TOOLS] Failed to fetch available tools from %s: %s",
                    target_base_url,
                    exc,
                )
                return []

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
        self._tool_catalog_cache_ttl_seconds = max(
            0,
            int(SETTINGS.mcp_tool_catalog_cache_ttl_seconds),
        )
        self._tool_catalog_cache: dict[str, tuple[float, list[str]]] = {}

    def _resolve_base_url(self, base_url: str | None) -> str:
        return (base_url or self.base_url).rstrip("/")

    def _get_cached_tools(self, base_url: str) -> list[str] | None:
        if self._tool_catalog_cache_ttl_seconds <= 0:
            return None
        cached = self._tool_catalog_cache.get(base_url)
        if cached is None:
            return None
        expires_at, tools = cached
        if time.monotonic() >= expires_at:
            self._tool_catalog_cache.pop(base_url, None)
            return None
        return list(tools)

    def _set_cached_tools(self, base_url: str, tools: list[str]) -> None:
        if self._tool_catalog_cache_ttl_seconds <= 0:
            return
        expires_at = time.monotonic() + float(self._tool_catalog_cache_ttl_seconds)
        self._tool_catalog_cache[base_url] = (expires_at, list(tools))

    def _build_headers(self) -> dict[str, str]:
        correlation_id = correlation_id_ctx.get()
        headers = {"Accept": "application/json, text/event-stream"}
        if correlation_id and correlation_id != "-":
            headers["X-Correlation-Id"] = correlation_id
        user_id = user_id_ctx.get()
        if user_id and user_id != "-":
            headers["X-User-Id"] = user_id
        user_role = user_role_ctx.get()
        if user_role and user_role != "-":
            headers["X-User-Role"] = user_role
        user_email = user_email_ctx.get()
        if user_email and user_email != "-":
            headers["X-User-Email"] = user_email
        authorization = authorization_ctx.get()
        if authorization and authorization != "-":
            headers["Authorization"] = authorization
        return headers

    def _extract_tool_names(self, response_body: Any) -> list[str]:
        if not isinstance(response_body, dict):
            return []

        result = response_body.get("result", response_body)
        if not isinstance(result, dict):
            return []

        tools = result.get("tools")
        if not isinstance(tools, list):
            return []

        names: list[str] = []
        for tool in tools:
            name = None
            if isinstance(tool, dict):
                name = tool.get("name")
            else:
                name = getattr(tool, "name", None)

            if isinstance(name, str) and name and name not in names:
                names.append(name)

        return names

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

    async def health(self, *, base_url: str | None = None) -> dict[str, Any]:
        target_base_url = self._resolve_base_url(base_url)
        async with httpx.AsyncClient(
            timeout=self.timeout,
            transport=self.transport,
            follow_redirects=True,
        ) as client:
            try:
                response = await client.get(
                    f"{target_base_url}/health",
                    headers=self._build_headers(),
                )
                response.raise_for_status()
                return {"ok": True, "result": response.json()}
            except Exception as exc:
                return {
                    "ok": False,
                    "tag": "AGT-MCP-HEALTH",
                    "message": f"MCP health check failed: {exc.__class__.__name__}",
                }

    async def invoke_tool(
        self,
        tool_name: str,
        tool_args: dict[str, Any],
        *,
        base_url: str | None = None,
    ) -> dict[str, Any]:
        target_base_url = self._resolve_base_url(base_url)
        payload = {
            "jsonrpc": "2.0",
            "id": correlation_id_ctx.get() if correlation_id_ctx.get() != "-" else "agent-service",
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": tool_args,
            },
        }

        async with httpx.AsyncClient(
            timeout=self.timeout,
            transport=self.transport,
            follow_redirects=True,
        ) as client:
            try:
                response = await client.post(
                    f"{target_base_url}/mcp/",
                    json=payload,
                    headers=self._build_headers(),
                )
                response.raise_for_status()
                return self._normalize_rpc_response(response.json(), tool_name=tool_name)
            except httpx.HTTPStatusError as exc:
                status_code = exc.response.status_code
                try:
                    payload = exc.response.json()
                    message = payload.get("message") or payload.get("detail") or str(payload)
                except ValueError:
                    message = exc.response.text or exc.__class__.__name__
                correlation_id = self._build_headers().get("X-Correlation-Id")
                # Log MCP tool call failure with correlationId for grep
                import logging
                logging.getLogger("agent-mcp").error(
                    "[MCP-FAIL][%s] tool=%s status=%s error=%s",
                    correlation_id or "none",
                    tool_name,
                    status_code,
                    message,
                )
                return {
                    "ok": False,
                    "tag": "AGT-MCP-TOOL",
                    "message": f"MCP tool call failed: HTTP {status_code} - {message}",
                    "tool": tool_name,
                    "statusCode": status_code,
                    "correlationId": correlation_id,
                }
            except Exception as exc:
                return {
                    "ok": False,
                    "tag": "AGT-MCP-TOOL",
                    "message": f"MCP tool call failed: {exc.__class__.__name__}",
                    "tool": tool_name,
                }


MCP_CLIENT = MCPClient(SETTINGS.mcp_server_url, SETTINGS.request_timeout_seconds)
