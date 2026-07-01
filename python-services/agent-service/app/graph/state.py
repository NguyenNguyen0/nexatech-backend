from __future__ import annotations

from typing import Annotated, Any, NotRequired, TypedDict

from langgraph.graph.message import add_messages

try:
    from copilotkit import CopilotKitState as _CopilotKitState  # type: ignore[import-untyped]
    _COPILOTKIT_AVAILABLE = True
except Exception:
    _CopilotKitState = None  # type: ignore[assignment, misc]
    _COPILOTKIT_AVAILABLE = False


# AgentState extends CopilotKitState when copilotkit is installed.
# CopilotKitState adds a `copilotkit` key (CopilotKitProperties) and
# Annotated[list[AnyMessage], add_messages] for `messages` — both required
# for CopilotKitMiddleware to inject frontend actions / context and for
# the AG-UI wire protocol to stream tool calls and text chunks correctly.
if _COPILOTKIT_AVAILABLE and _CopilotKitState is not None:
    class AgentState(_CopilotKitState, total=False):  # type: ignore[misc]
        caller: dict[str, Any]
        forced_tool: str | None
        forced_tool_args: dict[str, Any] | None
        checkpointer_enabled: bool
        thread_id: str
        request_id: str
        available_tools: list[str]
        mcp_target_domain: str | None
        mcp_target_url: str | None
        mcp_meta_url: str | None
        subagent_name: str | None
        delegation_trace: list[dict[str, Any]]
        input_text: str
        required_role: str | None
        request_sensitivity: str | None
        pending_approval: dict[str, Any] | None
        approval_request_id: str | None
        approval_result: str | None
        selected_tool: str | None
        selected_tool_args: dict[str, Any] | None
        tool_results: list[dict[str, Any]]
        tool_errors: list[dict[str, Any]]
        response_text: str | None
        answer_context: dict[str, Any]
        final_status: str | None
        last_tool_call: dict[str, Any] | None
        tool_call_history: list[dict[str, Any]]
        expires_at: str | None
        timeout_seconds: int | None
else:
    class AgentState(TypedDict, total=False):  # type: ignore[no-redef]
        messages: Annotated[list, add_messages]
        caller: dict[str, Any]
        forced_tool: str | None
        forced_tool_args: dict[str, Any] | None
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
        last_tool_call: NotRequired[dict[str, Any] | None]
        tool_call_history: NotRequired[list[dict[str, Any]]]
        expires_at: NotRequired[str | None]
        timeout_seconds: NotRequired[int | None]
