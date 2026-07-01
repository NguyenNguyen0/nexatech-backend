from __future__ import annotations

import json
import logging
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

from langgraph.types import interrupt

from app.core.settings import SETTINGS
from app.graph.llm_planner import synthesize_agent_response
from app.graph.subagents import plan_with_subagent
from app.graph.tool_descriptions import TOOL_DESCRIPTIONS
from app.graph.tool_registry import extract_latest_user_text
from app.hitl.sensitivity import TOOL_SENSITIVITY
from app.mcp_client import MCP_CLIENT
from app.runtime.delegation_metrics import DELEGATION_METRICS

try:
    from langchain_core.messages import AIMessage, ToolMessage
    _LC_MESSAGES_AVAILABLE = True
except Exception:
    _LC_MESSAGES_AVAILABLE = False


logger = logging.getLogger(__name__)

_META_TOOLS: set[str] = {
    "list_mcp_domains",
    "get_mcp_domain",
    "route_to_mcp_domain",
    "check_mcp_domain_health",
    "list_skills",
    "get_skill",
}

# Decision normalization map — canonical form used in state and routing.
_DECISION_NORMALIZE: dict[str, str] = {
    "approve": "approved",
    "approved": "approved",
    "reject": "rejected",
    "rejected": "rejected",
    "edit": "edited",
    "edited": "edited",
    "timeout": "timeout",
}


async def classify_request(state: dict[str, Any]) -> dict[str, Any]:
    """Entry node for every fresh turn.

    The checkpointer persists the full ``AgentState`` across turns on the
    same thread, so this is the single node every non-resume invocation
    passes through (``START -> classify_request``) before anything else
    runs. It clears per-turn scratch fields left over from the previous
    turn — without this, a stale ``selected_tool``/``response_text`` makes
    ``plan_with_subagent``/``format_response`` short-circuit and the agent
    repeats its previous answer verbatim instead of handling the new
    message.

    ``forced_tool``/``forced_tool_args`` are the one input-only exception:
    a caller may set them to force-execute a specific tool for this turn,
    bypassing planning (used by direct tool-invoke callers and tests).

    ``input_text`` is always recomputed from the latest message — it must
    never fall back to ``state.get("input_text")``, since that value is
    itself this node's own output from the *previous* turn and would make
    every later turn re-plan/re-synthesize against the first message.
    """
    input_text = extract_latest_user_text(state)
    forced_tool = state.get("forced_tool")
    forced_tool_args = state.get("forced_tool_args") or {}

    return {
        "input_text": input_text,
        "required_role": state.get("required_role") or "operator",
        "request_sensitivity": state.get("request_sensitivity") or "low",
        "selected_tool": forced_tool,
        "selected_tool_args": forced_tool_args if forced_tool else {},
        "response_text": None,
        "final_status": None,
        "tool_results": [],
        "tool_errors": [],
        "pending_approval": None,
        "approval_result": None,
        "approval_request_id": None,
        "expires_at": None,
        "timeout_seconds": None,
        "answer_context": {},
        "last_tool_call": None,
        "subagent_name": None,
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


async def domain_router(state: dict[str, Any]) -> dict[str, Any]:
    """Select the subagent responsible for this request domain."""
    domain = (state.get("mcp_target_domain") or "").strip().lower() or "general"
    input_text = (state.get("input_text") or extract_latest_user_text(state)).casefold()

    # Meta-intent requests should be delegated to the meta subagent.
    if "skill" in input_text or "mcp" in input_text:
        DELEGATION_METRICS.record("route_meta_intent_total", domain="meta")
        return {
            "mcp_target_domain": "meta",
            "mcp_target_url": state.get("mcp_meta_url") or state.get("mcp_target_url"),
            "subagent_name": "meta_subagent",
        }

    if domain == "meta":
        DELEGATION_METRICS.record("route_meta_domain_total", domain="meta")
        return {
            "mcp_target_url": state.get("mcp_meta_url") or state.get("mcp_target_url"),
            "subagent_name": "meta_subagent",
        }

    if domain == "general":
        DELEGATION_METRICS.record("route_general_total", domain="general")
        return {"subagent_name": "general_subagent"}

    DELEGATION_METRICS.record("route_domain_total", domain=domain)
    return {"subagent_name": f"{domain}_subagent"}


async def domain_executor(state: dict[str, Any]) -> dict[str, Any]:
    """Delegate planning to the selected domain subagent."""
    return await plan_with_subagent(state)


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
    """Build approval preview and compute expiry for HITL timeout."""
    selected_tool = state.get("selected_tool")
    timeout_seconds = SETTINGS.approval_timeout_seconds
    expires_at = (
        datetime.now(UTC) + timedelta(seconds=timeout_seconds)
    ).isoformat().replace("+00:00", "Z")

    return {
        "approval_request_id": str(uuid4()),
        "expires_at": expires_at,
        "timeout_seconds": timeout_seconds,
        "pending_approval": {
            "tool_name": selected_tool,
            "tool_args": state.get("selected_tool_args") or {},
            "sensitivity": state.get("request_sensitivity") or "low",
            "expires_at": expires_at,
            "timeout_seconds": timeout_seconds,
        },
    }


async def interrupt_caller(state: dict[str, Any]) -> dict[str, Any]:
    """Interrupt graph execution and wait for human decision.

    Emits a structured payload compatible with both CopilotKit AG-UI
    (``actionRequests``/``reviewConfigs``) and the legacy SSE bridge
    (``type``/``payload``).

    Supported decisions (case-insensitive):
    - ``approve`` / ``approved``  → execute tool as-is
    - ``edit`` / ``edited``       → update ``selected_tool_args`` then execute
    - ``reject`` / ``rejected``   → skip tool, go to format_response
    - ``timeout``                 → auto-rejected after timeout, go to format_response
    """
    selected_tool = state.get("selected_tool") or ""
    tool_args = state.get("selected_tool_args") or {}
    pending = state.get("pending_approval") or {}
    approval_request_id = state.get("approval_request_id") or str(uuid4())
    timeout_seconds = state.get("timeout_seconds") or SETTINGS.approval_timeout_seconds
    expires_at = state.get("expires_at") or (
        datetime.now(UTC) + timedelta(seconds=timeout_seconds)
    ).isoformat().replace("+00:00", "Z")

    decision = interrupt(
        {
            # CopilotKit AG-UI standard payload
            "actionRequests": [
                {
                    "id": approval_request_id,
                    "action": selected_tool,
                    "args": tool_args,
                    "description": TOOL_DESCRIPTIONS.get(selected_tool, selected_tool),
                }
            ],
            "reviewConfigs": [
                {
                    "allowedDecisions": ["approve", "reject", "edit"],
                    "timeoutSeconds": timeout_seconds,
                    "expiresAt": expires_at,
                }
            ],
            # Backward-compatible payload for legacy SSE consumers
            "type": "approval_request",
            "approval_request_id": approval_request_id,
            "timeout_seconds": timeout_seconds,
            "expires_at": expires_at,
            "payload": pending,
        }
    )

    # Normalize decision — supports string or dict from resume command
    if isinstance(decision, dict):
        raw_decision = str(decision.get("decision", "approved"))
        edited_args = decision.get("args")
    else:
        raw_decision = str(decision)
        edited_args = None

    normalized = _DECISION_NORMALIZE.get(raw_decision.lower(), raw_decision.lower())

    update: dict[str, Any] = {"approval_result": normalized}

    if normalized == "edited" and isinstance(edited_args, dict):
        # User submitted modified args — update before execute_tool runs
        update["selected_tool_args"] = edited_args
        logger.info(
            "[HITL][%s] Edit decision: tool=%s old_args=%s new_args=%s",
            approval_request_id,
            selected_tool,
            tool_args,
            edited_args,
        )
    elif normalized in ("rejected", "timeout"):
        logger.info(
            "[HITL][%s] Decision=%s for tool=%s — skipping execution",
            approval_request_id,
            normalized,
            selected_tool,
        )

    return update


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


def _safe_json(value: Any) -> str:
    """Serialize value to JSON string, falling back to str() on failure."""
    try:
        return json.dumps(value, ensure_ascii=False, default=str)
    except Exception:
        return str(value)


async def execute_tool(state: dict[str, Any]) -> dict[str, Any]:
    """Invoke the selected MCP tool.

    Emits an AIMessage with a tool_call and a ToolMessage with the result
    into ``state["messages"]`` so that the AG-UI / CopilotKit wire protocol
    can stream TOOL_CALL_START, TOOL_CALL_END, and TOOL_CALL_RESULT events
    to the frontend without any extra plumbing.
    """
    tool_name = state.get("selected_tool")
    tool_args = state.get("selected_tool_args") or {}
    if not tool_name:
        return {}

    target_base_url = state.get("mcp_target_url")
    if tool_name in _META_TOOLS:
        target_base_url = state.get("mcp_meta_url")

    tool_call_id = str(uuid4())
    started_at = datetime.now(UTC).isoformat().replace("+00:00", "Z")

    pending_call: dict[str, Any] = {
        "id": tool_call_id,
        "name": tool_name,
        "args": tool_args,
        "status": "pending",
        "result": None,
        "error": None,
        "started_at": started_at,
        "completed_at": None,
    }

    result = await MCP_CLIENT.invoke_tool(
        tool_name,
        tool_args,
        base_url=target_base_url,
    )
    completed_at = datetime.now(UTC).isoformat().replace("+00:00", "Z")

    existing_results = list(state.get("tool_results") or [])
    existing_errors = list(state.get("tool_errors") or [])
    existing_history = list(state.get("tool_call_history") or [])

    # Build AG-UI-compatible messages (AIMessage + ToolMessage) when
    # langchain_core is available. These are appended to state["messages"]
    # via the Annotated[list, add_messages] reducer so LangGraphAGUIAgent
    # can stream them as TOOL_CALL_* events to the frontend.
    agui_messages: list[Any] = []
    if _LC_MESSAGES_AVAILABLE:
        ai_msg = AIMessage(
            id=str(uuid4()),
            content="",
            tool_calls=[
                {
                    "id": tool_call_id,
                    "name": tool_name,
                    "args": tool_args,
                }
            ],
        )
        tool_result_content = _safe_json(result.get("data") or result)
        tool_msg = ToolMessage(
            id=str(uuid4()),
            tool_call_id=tool_call_id,
            name=tool_name,
            content=tool_result_content,
        )
        agui_messages = [ai_msg, tool_msg]

    if result.get("ok"):
        completed_call: dict[str, Any] = {
            **pending_call,
            "status": "completed",
            "result": result,
            "completed_at": completed_at,
        }
        existing_results.append({"tool": tool_name, "result": result})
        existing_history.append(completed_call)
        update: dict[str, Any] = {
            "tool_results": existing_results,
            "final_status": "executed",
            "last_tool_call": completed_call,
            "tool_call_history": existing_history,
        }
        if agui_messages:
            update["messages"] = agui_messages
        return update

    error_call: dict[str, Any] = {
        **pending_call,
        "status": "error",
        "error": result,
        "completed_at": completed_at,
    }
    existing_errors.append({"tool": tool_name, "error": result})
    existing_history.append(error_call)
    logger.error(
        "MCP tool execution failed: tool=%s status=%s tag=%s message=%s",
        tool_name,
        result.get("statusCode"),
        result.get("tag"),
        result.get("message"),
    )
    error_update: dict[str, Any] = {
        "tool_results": existing_results,
        "tool_errors": existing_errors,
        "final_status": "tool_error",
        "last_tool_call": error_call,
        "tool_call_history": existing_history,
        "answer_context": {
            "error": "tool_error",
            "selected_tool": tool_name,
            "tool_error": result,
        },
    }
    if agui_messages:
        error_update["messages"] = agui_messages
    return error_update


async def format_response(state: dict[str, Any]) -> dict[str, Any]:
    """Synthesize a natural-language response and emit it as an AIMessage.

    Writing the final text into ``state["messages"]`` as an AIMessage is
    what allows LangGraphAGUIAgent to stream TEXT_MESSAGE_* events to the
    CopilotKit frontend. ``response_text`` is kept for the legacy SSE path.
    """
    if state.get("response_text"):
        return {}

    text = await synthesize_agent_response(state)
    update: dict[str, Any] = {
        "response_text": text,
        "final_status": state.get("final_status") or "completed",
    }

    # Append AIMessage so AG-UI streams the text as TEXT_MESSAGE events.
    if _LC_MESSAGES_AVAILABLE and text:
        update["messages"] = [AIMessage(id=str(uuid4()), content=text)]

    return update
