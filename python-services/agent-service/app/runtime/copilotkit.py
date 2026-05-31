from __future__ import annotations

import logging
from typing import Any


logger = logging.getLogger(__name__)


def attach_copilotkit_endpoint(
    app: Any,
    *,
    agent_name: str,
    agent_description: str,
    graph: Any,
    path: str = "/api/v1/agent/copilotkit",
) -> bool:
    """Mount an AG-UI / CopilotKit endpoint on *app* using the compiled LangGraph.

    Uses ``add_langgraph_fastapi_endpoint`` from ``ag-ui-langgraph`` so the
    frontend can connect via the CopilotKit ``runtimeUrl``.

    Falls back gracefully if either ``copilotkit`` or ``ag-ui-langgraph``
    is unavailable — the service continues running with legacy SSE endpoints.

    Args:
        app: FastAPI application instance.
        agent_name: CopilotKit agent identifier (matches ``AGUI_AGENT_NAME``).
        agent_description: Short description used by the CopilotKit runtime.
        graph: Compiled LangGraph ``CompiledStateGraph`` instance.
        path: Mount path for the AG-UI endpoint.

    Returns:
        True when the endpoint was successfully mounted, False otherwise.
    """
    try:
        from ag_ui_langgraph import add_langgraph_fastapi_endpoint
        from copilotkit import LangGraphAGUIAgent
    except Exception as exc:  # pragma: no cover
        logger.warning(
            "[STARTUP] - CopilotKit endpoint unavailable (missing packages): %s", exc
        )
        return False

    try:
        agent = LangGraphAGUIAgent(
            name=agent_name,
            description=agent_description,
            graph=graph,
        )
        add_langgraph_fastapi_endpoint(app=app, agent=agent, path=path)
        logger.info("[STARTUP] + CopilotKit AG-UI endpoint mounted at %s", path)
        return True
    except Exception as exc:  # pragma: no cover
        logger.warning(
            "[STARTUP] - Failed to mount CopilotKit endpoint at %s: %s", path, exc
        )
        return False
