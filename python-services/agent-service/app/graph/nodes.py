from __future__ import annotations

from typing import Any
from uuid import uuid4

from langgraph.types import interrupt

from app.graph.llm_planner import plan_tool_call_with_llm
from app.graph.tool_registry import extract_latest_user_text, plan_tool_call
from app.hitl.sensitivity import TOOL_SENSITIVITY
from app.mcp_client import MCP_CLIENT


async def classify_request(state: dict[str, Any]) -> dict[str, Any]:
    input_text = state.get("input_text") or extract_latest_user_text(state)
    return {
        "input_text": input_text,
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
        input_text = state.get("input_text") or extract_latest_user_text(state)
        plan = plan_tool_call(input_text)
        if plan is None:
            plan = await plan_tool_call_with_llm(input_text)
        if plan is None:
            return {
                "response_text": "I can currently help with promotions, flash sales, news, and audit logs. Please be more specific about one of those areas.",
                "selected_tool_args": selected_tool_args,
                "final_status": "no_matching_tool",
            }
        return {
            "selected_tool": plan.tool_name,
            "selected_tool_args": plan.tool_args,
            "required_role": state.get("required_role") or plan.required_role,
            "response_text": None,
        }
    return {"selected_tool_args": selected_tool_args, "response_text": None}


async def needs_approval(state: dict[str, Any]) -> dict[str, Any]:
    selected_tool = state.get("selected_tool")
    sensitivity = TOOL_SENSITIVITY.get(selected_tool or "", "low")
    if sensitivity != "low" and not state.get("checkpointer_enabled", False):
        return {
            "request_sensitivity": sensitivity,
            "response_text": "Approval workflow requires the LangGraph checkpointer to be available. Retry after agent persistence is configured.",
            "final_status": "approval_unavailable",
        }
    return {"request_sensitivity": sensitivity}


async def build_preview(state: dict[str, Any]) -> dict[str, Any]:
    selected_tool = state.get("selected_tool")
    return {
        "approval_request_id": str(uuid4()),
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
        "final_status": "escalated",
        "response_text": "Escalation flow is configured in skeleton mode.",
    }


async def execute_tool(state: dict[str, Any]) -> dict[str, Any]:
    tool_name = state.get("selected_tool")
    tool_args = state.get("selected_tool_args") or {}
    if not tool_name:
        return {}

    result = await MCP_CLIENT.invoke_tool(tool_name, tool_args)
    existing_results = state.get("tool_results") or []
    existing_errors = state.get("tool_errors") or []
    if result.get("ok"):
        existing_results.append({"tool": tool_name, "result": result})
        return {"tool_results": existing_results, "final_status": "executed"}

    existing_errors.append({"tool": tool_name, "error": result})
    return {
        "tool_results": existing_results,
        "tool_errors": existing_errors,
        "response_text": result.get("message") or f"Tool execution failed for {tool_name}.",
        "final_status": "tool_error",
    }


async def format_response(state: dict[str, Any]) -> dict[str, Any]:
    if state.get("response_text"):
        return {}
    tool_results = state.get("tool_results") or []
    if not tool_results:
        return {"response_text": "No tool results available yet."}
    last_result = tool_results[-1]
    return {
        "response_text": f"Executed tool '{last_result['tool']}' successfully.",
        "final_status": state.get("final_status") or "completed",
    }