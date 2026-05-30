from __future__ import annotations

from typing import Any


def route_after_permission(state: dict[str, Any]) -> str:
    if state.get("approval_result") == "denied":
        return "format_response"
    return "agent_think"


def route_after_agent_think(state: dict[str, Any]) -> str:
    if not state.get("selected_tool"):
        return "format_response"
    return "needs_approval"


def route_after_needs_approval(state: dict[str, Any]) -> str:
    if state.get("response_text"):
        return "format_response"
    sensitivity = (state.get("request_sensitivity") or "low").lower()
    if sensitivity == "low":
        return "execute_tool"
    if sensitivity == "medium":
        return "build_preview"
    return "route_escalation"


def route_after_interrupt(state: dict[str, Any]) -> str:
    decision = (state.get("approval_result") or "approved").lower()
    if decision == "approved":
        return "execute_tool"
    return "format_response"
