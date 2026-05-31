from __future__ import annotations

from app.graph.tool_registry import TOOL_DESCRIPTORS


# Human-readable one-line description for each tool.
# Used by HITL interrupt payload (actionRequests) and CopilotKit UI display.
TOOL_DESCRIPTIONS: dict[str, str] = {
    d.tool_name: d.description or d.rationale[:80].rstrip(". ")
    for d in TOOL_DESCRIPTORS
}
