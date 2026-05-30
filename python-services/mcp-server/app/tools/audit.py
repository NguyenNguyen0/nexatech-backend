from __future__ import annotations

from typing import Any

from app.mcp.server import mcp
from app.services.audit import AUDIT_API


@mcp.tool()
async def search_audit_logs(params: dict[str, Any] | None = None) -> dict[str, Any]:
    return await AUDIT_API.search_audit_logs(params=params)


@mcp.tool()
async def get_audit_entry(entry_id: str) -> dict[str, Any]:
    return await AUDIT_API.get_audit_entry(entry_id)


@mcp.tool()
async def get_audit_trace(lookup_id: str) -> dict[str, Any]:
    return await AUDIT_API.trace_by_lookup_id(lookup_id)


@mcp.tool()
async def get_audit_facets() -> dict[str, Any]:
    return await AUDIT_API.get_audit_facets()
