from __future__ import annotations

import logging
from contextlib import AbstractContextManager
from typing import Any


logger = logging.getLogger(__name__)

_checkpointer_cm: AbstractContextManager[Any] | None = None
_checkpointer: Any | None = None


def initialize_checkpointer(connection_string: str | None) -> Any | None:
    global _checkpointer_cm, _checkpointer

    if not connection_string:
        return None

    if _checkpointer is not None:
        return _checkpointer

    from langgraph.checkpoint.postgres import PostgresSaver

    _checkpointer_cm = PostgresSaver.from_conn_string(connection_string)
    _checkpointer = _checkpointer_cm.__enter__()
    _checkpointer.setup()
    logger.info("[STARTUP] + LangGraph checkpointer: postgres configured")
    return _checkpointer


def close_checkpointer() -> None:
    global _checkpointer_cm, _checkpointer

    if _checkpointer_cm is None:
        return

    _checkpointer_cm.__exit__(None, None, None)
    _checkpointer_cm = None
    _checkpointer = None
