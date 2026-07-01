from __future__ import annotations

from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.edges import (
    route_after_domain_executor,
    route_after_interrupt,
    route_after_needs_approval,
)
from app.graph.nodes import (
    build_preview,
    classify_request,
    domain_executor,
    domain_router,
    execute_tool,
    format_response,
    interrupt_caller,
    needs_approval,
    route_escalation,
)
from app.graph.state import AgentState


def compile_graph(checkpointer: Any | None = None):
    workflow = StateGraph(AgentState)

    workflow.add_node("classify_request", classify_request)
    workflow.add_node("domain_router", domain_router)
    workflow.add_node("domain_executor", domain_executor)
    workflow.add_node("needs_approval", needs_approval)
    workflow.add_node("build_preview", build_preview)
    workflow.add_node("interrupt_caller", interrupt_caller)
    workflow.add_node("route_escalation", route_escalation)
    workflow.add_node("execute_tool", execute_tool)
    workflow.add_node("format_response", format_response)

    workflow.add_edge(START, "classify_request")
    workflow.add_edge("classify_request", "domain_router")
    workflow.add_edge("domain_router", "domain_executor")

    workflow.add_conditional_edges(
        "domain_executor",
        route_after_domain_executor,
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
