from __future__ import annotations

from typing import Annotated, Any, TypedDict

from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    caller: dict[str, Any]
    required_role: str | None
    request_sensitivity: str | None
    pending_approval: dict[str, Any] | None
    approval_result: str | None
    selected_tool: str | None
    selected_tool_args: dict[str, Any] | None
    tool_results: list[dict[str, Any]]
    response_text: str | None