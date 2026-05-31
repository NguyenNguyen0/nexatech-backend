from __future__ import annotations

import asyncio
import logging
from typing import Any

"""In-memory registry for HITL auto-timeout tasks.

Each pending approval is tracked by ``thread_id``.  When an interrupt fires,
``schedule_timeout()`` creates an asyncio task that will auto-resume the graph
with ``decision="timeout"`` after ``timeout_seconds``.  When the user submits
a manual decision via ``/runs/resume`` (or AG-UI), ``cancel_timeout()`` is
called to prevent the auto-resume from firing.

Limitations (Phase 7 scope):
- Registry is in-memory only.  If the service restarts, pending tasks are lost
  and the graph will remain interrupted until manually resumed or the Postgres
  checkpoint expires.  A persistent registry is out of scope for this phase.
- Only one timeout task per thread_id is tracked.  A new ``schedule_timeout``
  call for the same thread_id will cancel and replace the existing task.
"""

logger = logging.getLogger(__name__)

# thread_id → running asyncio.Task
_PENDING_TIMEOUTS: dict[str, asyncio.Task[Any]] = {}


def schedule_timeout(
    thread_id: str,
    *,
    graph: Any,
    timeout_seconds: int,
) -> None:
    """Schedule an auto-timeout resume for *thread_id* after *timeout_seconds*.

    Cancels any existing task for the same thread_id before scheduling a new one.
    """
    cancel_timeout(thread_id)

    async def _auto_timeout() -> None:
        try:
            await asyncio.sleep(timeout_seconds)
        except asyncio.CancelledError:
            logger.debug("[HITL][%s] Auto-timeout task cancelled", thread_id)
            return

        logger.warning(
            "[HITL][%s] Approval timed out after %ss — auto-resuming with timeout decision",
            thread_id,
            timeout_seconds,
        )
        from langgraph.types import Command

        try:
            config = {"configurable": {"thread_id": thread_id}}
            await graph.ainvoke(
                Command(resume={"decision": "timeout"}),
                config=config,
                durability="sync",
                version="v2",
            )
        except Exception as exc:
            logger.error(
                "[HITL][%s] Auto-timeout resume failed: %s",
                thread_id,
                exc,
            )
        finally:
            _PENDING_TIMEOUTS.pop(thread_id, None)

    task = asyncio.create_task(_auto_timeout(), name=f"hitl-timeout-{thread_id}")
    _PENDING_TIMEOUTS[thread_id] = task
    logger.info(
        "[HITL][%s] Auto-timeout scheduled in %ss",
        thread_id,
        timeout_seconds,
    )


def cancel_timeout(thread_id: str) -> bool:
    """Cancel the pending timeout task for *thread_id*.

    Returns True if a task was found and cancelled, False otherwise.
    """
    task = _PENDING_TIMEOUTS.pop(thread_id, None)
    if task is None:
        return False
    if not task.done():
        task.cancel()
        logger.info("[HITL][%s] Auto-timeout cancelled (manual decision received)", thread_id)
    return True


def pending_timeout_ids() -> list[str]:
    """Return list of thread_ids with active timeout tasks (for tests/debug)."""
    return list(_PENDING_TIMEOUTS.keys())
