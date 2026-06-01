from __future__ import annotations

from typing import Annotated, Any, NotRequired, TypedDict

from langgraph.graph.message import add_messages


class AgentState(TypedDict, total=False):
    messages: Annotated[list, add_messages]
    caller: dict[str, Any]
    checkpointer_enabled: NotRequired[bool]
    thread_id: NotRequired[str]
    request_id: NotRequired[str]
    available_tools: NotRequired[list[str]]
    mcp_target_domain: NotRequired[str | None]
    mcp_target_url: NotRequired[str | None]
    mcp_meta_url: NotRequired[str | None]
    subagent_name: NotRequired[str | None]
    delegation_trace: NotRequired[list[dict[str, Any]]]
    input_text: NotRequired[str]
    required_role: str | None
    request_sensitivity: str | None
    pending_approval: dict[str, Any] | None
    approval_request_id: NotRequired[str | None]
    approval_result: str | None
    selected_tool: str | None
    selected_tool_args: dict[str, Any] | None
    tool_results: list[dict[str, Any]]
    tool_errors: NotRequired[list[dict[str, Any]]]
    response_text: str | None
    answer_context: NotRequired[dict[str, Any]]
    final_status: NotRequired[str | None]
    # Phase 6 — tool-call display state for AG-UI / frontend
    last_tool_call: NotRequired[dict[str, Any] | None]
    tool_call_history: NotRequired[list[dict[str, Any]]]
    # Phase 7 — HITL timeout tracking
    expires_at: NotRequired[str | None]
    timeout_seconds: NotRequired[int | None]
