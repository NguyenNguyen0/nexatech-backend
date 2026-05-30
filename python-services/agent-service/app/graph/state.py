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
