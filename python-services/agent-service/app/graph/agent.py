from __future__ import annotations

from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.edges import (
    route_after_agent_think,
    route_after_interrupt,
    route_after_needs_approval,
    route_after_permission,
)
from app.graph.nodes import (
    agent_think,
    build_preview,
    classify_request,
    execute_tool,
    format_response,
    interrupt_caller,
    needs_approval,
    permission_gate,
    route_escalation,
)
from app.graph.state import AgentState


def compile_graph(checkpointer: Any | None = None):
    workflow = StateGraph(AgentState)

    workflow.add_node("classify_request", classify_request)
    workflow.add_node("permission_gate", permission_gate)
    workflow.add_node("agent_think", agent_think)
    workflow.add_node("needs_approval", needs_approval)
    workflow.add_node("build_preview", build_preview)
    workflow.add_node("interrupt_caller", interrupt_caller)
    workflow.add_node("route_escalation", route_escalation)
    workflow.add_node("execute_tool", execute_tool)
    workflow.add_node("format_response", format_response)

    workflow.add_edge(START, "classify_request")
    workflow.add_edge("classify_request", "permission_gate")

    workflow.add_conditional_edges(
        "permission_gate",
        route_after_permission,
        {
            "agent_think": "agent_think",
            "format_response": "format_response",
        },
    )

    workflow.add_conditional_edges(
        "agent_think",
        route_after_agent_think,
        {
            "needs_approval": "needs_approval",
            "format_response": "format_response",
        },
    )

    workflow.add_conditional_edges(
        "needs_approval",
        route_after_needs_approval,
        {
            "execute_tool": "execute_tool",
            "build_preview": "build_preview",
            "route_escalation": "route_escalation",
        },
    )

    workflow.add_edge("build_preview", "interrupt_caller")
    workflow.add_conditional_edges(
        "interrupt_caller",
        route_after_interrupt,
        {
            "execute_tool": "execute_tool",
            "format_response": "format_response",
        },
    )

    workflow.add_edge("route_escalation", "format_response")
    workflow.add_edge("execute_tool", "format_response")
    workflow.add_edge("format_response", END)

    if checkpointer is None:
        return workflow.compile()
    return workflow.compile(checkpointer=checkpointer)