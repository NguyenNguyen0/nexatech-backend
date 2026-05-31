from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import TypeVar


T = TypeVar("T")

RETRYABLE_STATUS_CODES = {408, 425, 429, 500, 502, 503, 504}


async def retry_async(
    operation: Callable[[], Awaitable[T]],
    *,
    attempts: int,
    base_delay_seconds: float,
    max_delay_seconds: float,
    should_retry: Callable[[Exception], bool],
    on_retry: Callable[[int, Exception, float], None] | None = None,
) -> T:
    last_error: Exception | None = None
    delay = base_delay_seconds

    for attempt in range(1, attempts + 1):
        try:
            return await operation()
        except Exception as exc:
            last_error = exc
            if attempt >= attempts or not should_retry(exc):
                raise
            if on_retry is not None:
                on_retry(attempt, exc, delay)
            await asyncio.sleep(delay)
            delay = min(delay * 2, max_delay_seconds)

    assert last_error is not None
    raise last_error