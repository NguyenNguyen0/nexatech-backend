from __future__ import annotations

import logging
from typing import Any


logger = logging.getLogger(__name__)


def attach_copilotkit_middleware(
    app: Any,
    *,
    agent_name: str,
    agent_description: str,
    graph: Any,
) -> bool:
    try:
        from copilotkit import CopilotKitMiddleware, LangGraphAGUIAgent
    except Exception as exc:  # pragma: no cover
        logger.warning("CopilotKit middleware is not available: %s", exc)
        return False

    try:
        agent = LangGraphAGUIAgent(
            name=agent_name,
            description=agent_description,
            graph=graph,
        )
        app.state.copilotkit_agents = [agent]
        app.state.copilotkit_middleware = CopilotKitMiddleware(expose_state=False)
        return True
    except Exception as exc:  # pragma: no cover
        logger.warning("Failed to attach CopilotKit middleware: %s", exc)
        return False
