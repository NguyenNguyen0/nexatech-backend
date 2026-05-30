from __future__ import annotations

from typing import Any

from langgraph.types import interrupt

from app.hitl.sensitivity import TOOL_SENSITIVITY
from app.mcp_client import MCP_CLIENT


async def classify_request(state: dict[str, Any]) -> dict[str, Any]:
    # Skeleton classifier: real intent/role classification will be added in implementation phase.
    return {
        "required_role": state.get("required_role") or "operator",
        "request_sensitivity": state.get("request_sensitivity") or "low",
    }


async def permission_gate(state: dict[str, Any]) -> dict[str, Any]:
    roles = state.get("caller", {}).get("roles", [])
    required_role = state.get("required_role")
    if required_role and required_role not in roles:
        return {
            "approval_result": "denied",
            "response_text": f"Permission denied: missing role '{required_role}'",
        }
    return {"approval_result": None}


async def agent_think(state: dict[str, Any]) -> dict[str, Any]:
    selected_tool = state.get("selected_tool")
    selected_tool_args = state.get("selected_tool_args") or {}
    if not selected_tool:
        return {
            "response_text": "Agent skeleton is ready. No tool selected yet.",
            "selected_tool_args": selected_tool_args,
        }
    return {"selected_tool_args": selected_tool_args}


async def needs_approval(state: dict[str, Any]) -> dict[str, Any]:
    selected_tool = state.get("selected_tool")
    sensitivity = TOOL_SENSITIVITY.get(selected_tool or "", "low")
    return {"request_sensitivity": sensitivity}


async def build_preview(state: dict[str, Any]) -> dict[str, Any]:
    selected_tool = state.get("selected_tool")
    return {
        "pending_approval": {
            "tool_name": selected_tool,
            "tool_args": state.get("selected_tool_args") or {},
            "sensitivity": state.get("request_sensitivity") or "low",
        }
    }


async def interrupt_caller(state: dict[str, Any]) -> dict[str, Any]:
    decision = interrupt(
        {
            "type": "approval_request",
            "payload": state.get("pending_approval"),
        }
    )
    if isinstance(decision, str):
        return {"approval_result": decision}
    return {"approval_result": "approved"}


async def route_escalation(state: dict[str, Any]) -> dict[str, Any]:
    return {
        "approval_result": "escalated",
        "response_text": "Escalation flow is configured in skeleton mode.",
    }


async def execute_tool(state: dict[str, Any]) -> dict[str, Any]:
    tool_name = state.get("selected_tool")
    tool_args = state.get("selected_tool_args") or {}
    if not tool_name:
        return {}

    result = await MCP_CLIENT.invoke_tool(tool_name, tool_args)
    existing_results = state.get("tool_results") or []
    existing_results.append({"tool": tool_name, "result": result})
    return {"tool_results": existing_results}


async def format_response(state: dict[str, Any]) -> dict[str, Any]:
    if state.get("response_text"):
        return {}
    tool_results = state.get("tool_results") or []
    if not tool_results:
        return {"response_text": "No tool results available yet."}
    return {"response_text": f"Executed {len(tool_results)} tool call(s)."}