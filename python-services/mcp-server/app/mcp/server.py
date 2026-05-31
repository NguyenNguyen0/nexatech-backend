from __future__ import annotations

from fastmcp import FastMCP

from app.core.settings import SETTINGS

mcp = FastMCP(f"NexaTech MCP ({SETTINGS.mcp_server_profile})")
