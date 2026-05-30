from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel, Field


class ApprovalRequest(BaseModel):
    tool_name: str
    tool_args: dict[str, Any] = Field(default_factory=dict)
    sensitivity: str = "medium"
    requested_by: str
    requested_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    timeout_seconds: int = 600


async def wait_for_approval(
    decision_future: asyncio.Future[str], timeout_seconds: int
) -> str:
    try:
        return await asyncio.wait_for(decision_future, timeout=timeout_seconds)
    except asyncio.TimeoutError:
        return "timeout"
