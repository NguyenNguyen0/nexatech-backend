from __future__ import annotations

from typing import Annotated, Any

from app.mcp.server import mcp
from app.services.audit import AUDIT_API
from pydantic import Field


@mcp.tool()
async def search_audit_logs(
    params: Annotated[
        dict[str, Any] | None,
        Field(
            description=(
                "Optional audit search filters. Supported keys: action, entityType, "
                "entityId, actor, sourceService, from, to, page, size, sortBy, "
                "sortDir. Empty values and unknown keys are ignored."
            ),
        ),
    ] = None,
) -> dict[str, Any]:
    """Search audit log entries with optional filters and pagination.

    Use this as the primary audit discovery tool when investigating user actions,
    entity changes, source-service events, or a time window. Dates should be passed
    in the format expected by audit-service, typically ISO-8601 strings.
    """
    return await AUDIT_API.search_audit_logs(params=params)


@mcp.tool()
async def get_audit_entry(
    entry_id: Annotated[
        str,
        Field(description="Unique audit entry ID to retrieve."),
    ],
) -> dict[str, Any]:
    """Get one audit log entry by its audit entry ID.

    Use this after search_audit_logs when the caller needs the complete detail of a
    specific audit record, including actor, action, entity, source service, payload,
    timestamps, and trace metadata if present.
    """
    return await AUDIT_API.get_audit_entry(entry_id)


@mcp.tool()
async def get_audit_trace(
    lookup_id: Annotated[
        str,
        Field(
            description=(
                "Correlation ID, trace ID, entity ID, order ID, or other lookup "
                "identifier supported by audit-service trace lookup."
            ),
        ),
    ],
) -> dict[str, Any]:
    """Trace related audit events for a correlation ID or domain lookup ID.

    Use this to follow one request or business operation across services. It is
    especially useful with X-Correlation-Id from error responses, order IDs, entity
    IDs, or trace IDs captured in logs.
    """
    return await AUDIT_API.trace_by_lookup_id(lookup_id)


@mcp.tool()
async def get_audit_facets() -> dict[str, Any]:
    """Return available audit search facets.

    Use this before building a filtered audit query when the caller needs known
    actions, entity types, actors, source services, or other selectable filter
    values exposed by audit-service.
    """
    return await AUDIT_API.get_audit_facets()
