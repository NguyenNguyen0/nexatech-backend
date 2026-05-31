from __future__ import annotations

import logging
from typing import Any


logger = logging.getLogger(__name__)

_checkpointer: Any | None = None
_checkpointer_conn: Any | None = None
_checkpointer_error: str | None = None


async def initialize_checkpointer(connection_string: str | None) -> Any | None:
    """Initialize an AsyncPostgresSaver for LangGraph checkpointing.

    Uses ``AsyncPostgresSaver`` so that ``ainvoke`` / ``aget_tuple`` work
    correctly in the async FastAPI runtime.  The underlying
    ``AsyncConnection`` is kept alive for the process lifetime and closed
    in ``close_checkpointer()``.
    """
    global _checkpointer, _checkpointer_conn, _checkpointer_error

    if not connection_string:
        _checkpointer_error = None
        return None

    if _checkpointer is not None:
        return _checkpointer

    from psycopg import AsyncConnection
    from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

    try:
        conn = await AsyncConnection.connect(
            connection_string,
            autocommit=True,
            prepare_threshold=0,
        )
        _checkpointer_conn = conn
        saver = AsyncPostgresSaver(conn=conn)
        await saver.setup()
        _checkpointer = saver
        _checkpointer_error = None
        logger.info("[STARTUP] + LangGraph checkpointer: postgres configured")
        return saver
    except Exception as exc:
        _checkpointer_error = exc.__class__.__name__
        await close_checkpointer()
        raise


async def check_checkpointer_health() -> tuple[bool, str]:
    """Lightweight readiness probe — tries an empty get_tuple."""
    if _checkpointer is None:
        return False, "disabled"

    try:
        await _checkpointer.aget_tuple(
            {"configurable": {"thread_id": "__agent_service_readiness__"}}
        )
        return True, "ok"
    except Exception as exc:
        return False, f"error: {exc.__class__.__name__}"


def get_checkpointer_error() -> str | None:
    return _checkpointer_error


async def close_checkpointer() -> None:
    """Idempotent cleanup — close the underlying async connection."""
    global _checkpointer, _checkpointer_conn

    conn = _checkpointer_conn
    _checkpointer_conn = None
    _checkpointer = None

    if conn is None:
        return

    try:
        await conn.close()
    except Exception:
        pass
