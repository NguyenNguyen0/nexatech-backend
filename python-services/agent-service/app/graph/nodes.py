from __future__ import annotations

import logging
from typing import Any
from uuid import uuid4

from langgraph.types import interrupt

from app.graph.llm_planner import plan_tool_call_with_llm, synthesize_agent_response
from app.graph.tool_registry import extract_latest_user_text, plan_tool_call
from app.hitl.sensitivity import TOOL_SENSITIVITY
from app.mcp_client import MCP_CLIENT


logger = logging.getLogger(__name__)


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
        logger.warning(
            "Permission gate denied request: required_role=%s roles=%s",
            required_role,
            roles,
        )
        return {
            "approval_result": "denied",
            "final_status": "permission_denied",
            "answer_context": {
                "error": "permission_denied",
                "required_role": required_role,
                "caller_roles": roles,
            },
        }
    return {"approval_result": None}


def _is_tool_catalog_request(input_text: str) -> bool:
    """Return True only when user is asking ABOUT the tool catalog itself.

    Must NOT fire for requests like "dùng tool list_products_by_category_slug ..."
    where "tool" appears but the intent is to invoke a specific tool.
    """
    normalized = input_text.casefold()

    # If a registered tool name appears in the text, the user is invoking a tool,
    # not asking for a catalog listing — short-circuit immediately.
    from app.graph.tool_registry import TOOL_DESCRIPTORS
    if any(d.tool_name in normalized for d in TOOL_DESCRIPTORS):
        return False

    # "tool" alone is too broad; only treat plural / meta terms as catalog signals.
    tool_terms = ("tools", "công cụ", "cong cu", "khả năng", "kha nang")
    list_terms = (
        "liệt kê",
        "liet ke",
        "danh sách",
        "danh sach",
        "có thể sử dụng",
        "co the su dung",
        "bạn có thể",
        "ban co the",
    )
    return any(term in normalized for term in tool_terms) and any(
        term in normalized for term in list_terms
    )


async def agent_think(state: dict[str, Any]) -> dict[str, Any]:
    selected_tool = state.get("selected_tool")
    selected_tool_args = state.get("selected_tool_args") or {}
    if not selected_tool:
        input_text = state.get("input_text") or extract_latest_user_text(state)
        if _is_tool_catalog_request(input_text):
            available_tools = state.get("available_tools") or []
            if not available_tools:
                logger.error("Tool catalog request received but available_tools is empty.")
            return {
                "selected_tool_args": selected_tool_args,
                "final_status": "tool_catalog",
                "answer_context": {
                    "intent": "tool_catalog",
                    "available_tools": available_tools,
                },
            }
        plan = plan_tool_call(input_text)
        if plan is None:
            plan = await plan_tool_call_with_llm(input_text)
        if plan is None:
            logger.warning(
                "No matching tool plan for input. available_tools=%s input=%r",
                state.get("available_tools") or [],
                input_text,
            )
            return {
                "selected_tool_args": selected_tool_args,
                "final_status": "no_matching_tool",
                "answer_context": {
                    "error": "no_matching_tool",
                    "available_tools": state.get("available_tools") or [],
                },
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
        logger.error(
            "Approval unavailable: tool=%s sensitivity=%s checkpointer_enabled=%s",
            selected_tool,
            sensitivity,
            state.get("checkpointer_enabled", False),
        )
        return {
            "request_sensitivity": sensitivity,
            "final_status": "approval_unavailable",
            "answer_context": {
                "error": "approval_unavailable",
                "selected_tool": selected_tool,
                "sensitivity": sensitivity,
            },
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
        },
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
    logger.warning(
        "Escalating request: selected_tool=%s sensitivity=%s",
        state.get("selected_tool"),
        state.get("request_sensitivity"),
    )
    return {
        "approval_result": "escalated",
        "final_status": "escalated",
        "answer_context": {
            "status": "escalated",
            "selected_tool": state.get("selected_tool"),
            "request_sensitivity": state.get("request_sensitivity"),
        },
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
    logger.error(
        "MCP tool execution failed: tool=%s status=%s tag=%s message=%s",
        tool_name,
        result.get("statusCode"),
        result.get("tag"),
        result.get("message"),
    )
    return {
        "tool_results": existing_results,
        "tool_errors": existing_errors,
        "final_status": "tool_error",
        "answer_context": {
            "error": "tool_error",
            "selected_tool": tool_name,
            "tool_error": result,
        },
    }


async def format_response(state: dict[str, Any]) -> dict[str, Any]:
    if state.get("response_text"):
        return {}
    return {
        "response_text": await synthesize_agent_response(state),
        "final_status": state.get("final_status") or "completed",
    }
