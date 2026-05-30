from __future__ import annotations

from mcp.server.fastmcp import FastMCP


mcp = FastMCP(
    "NexaTech MCP Skeleton",
    json_response=True,
    streamable_http_path="/",
)