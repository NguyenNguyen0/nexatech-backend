from __future__ import annotations

import asyncio
import sys
from pathlib import Path
from typing import Any

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.graph.edges import route_after_interrupt
from app.hitl.timeout_registry import (
    cancel_timeout,
    pending_timeout_ids,
    schedule_timeout,
)

"""Tests for Phase 7 — HITL decision routing and timeout registry."""


# ── route_after_interrupt ───────────────────────────────────────────────────


def test_route_after_interrupt_approved_goes_to_execute_tool() -> None:
    state: dict[str, Any] = {"approval_result": "approved"}
    assert route_after_interrupt(state) == "execute_tool"


def test_route_after_interrupt_edited_goes_to_execute_tool() -> None:
    state: dict[str, Any] = {"approval_result": "edited"}
    assert route_after_interrupt(state) == "execute_tool"


def test_route_after_interrupt_rejected_goes_to_format_response() -> None:
    state: dict[str, Any] = {"approval_result": "rejected"}
    assert route_after_interrupt(state) == "format_response"


def test_route_after_interrupt_timeout_goes_to_format_response() -> None:
    state: dict[str, Any] = {"approval_result": "timeout"}
    assert route_after_interrupt(state) == "format_response"


def test_route_after_interrupt_denied_goes_to_format_response() -> None:
    state: dict[str, Any] = {"approval_result": "denied"}
    assert route_after_interrupt(state) == "format_response"


def test_route_after_interrupt_none_defaults_to_execute_tool() -> None:
    # None approval_result defaults to "approved" (backward compat)
    state: dict[str, Any] = {"approval_result": None}
    assert route_after_interrupt(state) == "execute_tool"


# ── build_preview ───────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_build_preview_emits_expires_at_and_timeout_seconds(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.core.settings import SETTINGS
    from app.graph.nodes import build_preview

    monkeypatch.setattr(SETTINGS, "approval_timeout_seconds", 30)

    state: dict[str, Any] = {
        "selected_tool": "create_payment",
        "selected_tool_args": {"order_id": "ORD-001"},
        "request_sensitivity": "medium",
    }
    result = await build_preview(state)

    assert result["timeout_seconds"] == 30
    assert result["expires_at"].endswith("Z")
    pending = result["pending_approval"]
    assert pending["timeout_seconds"] == 30
    assert pending["expires_at"].endswith("Z")
    assert pending["tool_name"] == "create_payment"


# ── interrupt_caller payload ────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_interrupt_caller_emits_action_requests_and_review_configs(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """interrupt_caller should emit actionRequests/reviewConfigs compliant payload."""
    from app.core.settings import SETTINGS
    from app.graph.nodes import interrupt_caller

    monkeypatch.setattr(SETTINGS, "approval_timeout_seconds", 30)

    captured_payload: dict[str, Any] = {}

    def fake_interrupt(payload: dict[str, Any]) -> str:
        captured_payload.update(payload)
        return "approved"

    monkeypatch.setattr("app.graph.nodes.interrupt", fake_interrupt)

    state: dict[str, Any] = {
        "selected_tool": "create_payment",
        "selected_tool_args": {"order_id": "ORD-001"},
        "pending_approval": {"tool_name": "create_payment", "tool_args": {}},
        "approval_request_id": "req-001",
        "timeout_seconds": 30,
        "expires_at": "2026-06-01T00:00:00Z",
    }
    result = await interrupt_caller(state)

    assert result["approval_result"] == "approved"
    assert "actionRequests" in captured_payload
    assert "reviewConfigs" in captured_payload
    action = captured_payload["actionRequests"][0]
    assert action["action"] == "create_payment"
    assert action["id"] == "req-001"
    review = captured_payload["reviewConfigs"][0]
    assert review["timeoutSeconds"] == 30
    assert "approve" in review["allowedDecisions"]
    assert "reject" in review["allowedDecisions"]
    assert "edit" in review["allowedDecisions"]
    # backward-compat fields
    assert captured_payload["type"] == "approval_request"
    assert "expires_at" in captured_payload


@pytest.mark.asyncio
async def test_interrupt_caller_normalizes_approve_string(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.core.settings import SETTINGS
    from app.graph.nodes import interrupt_caller

    monkeypatch.setattr(SETTINGS, "approval_timeout_seconds", 30)
    monkeypatch.setattr("app.graph.nodes.interrupt", lambda _: "approve")

    state: dict[str, Any] = {
        "selected_tool": "create_payment",
        "selected_tool_args": {},
        "pending_approval": {},
    }
    result = await interrupt_caller(state)
    assert result["approval_result"] == "approved"


@pytest.mark.asyncio
async def test_interrupt_caller_normalizes_reject_string(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.core.settings import SETTINGS
    from app.graph.nodes import interrupt_caller

    monkeypatch.setattr(SETTINGS, "approval_timeout_seconds", 30)
    monkeypatch.setattr("app.graph.nodes.interrupt", lambda _: "reject")

    state: dict[str, Any] = {
        "selected_tool": "create_payment",
        "selected_tool_args": {},
        "pending_approval": {},
    }
    result = await interrupt_caller(state)
    assert result["approval_result"] == "rejected"


@pytest.mark.asyncio
async def test_interrupt_caller_edit_decision_updates_tool_args(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Edit decision should update selected_tool_args with new args from decision dict."""
    from app.core.settings import SETTINGS
    from app.graph.nodes import interrupt_caller

    monkeypatch.setattr(SETTINGS, "approval_timeout_seconds", 30)
    new_args = {"order_id": "ORD-999", "amount": 500}
    monkeypatch.setattr(
        "app.graph.nodes.interrupt",
        lambda _: {"decision": "edit", "args": new_args},
    )

    state: dict[str, Any] = {
        "selected_tool": "create_payment",
        "selected_tool_args": {"order_id": "ORD-001"},
        "pending_approval": {},
    }
    result = await interrupt_caller(state)
    assert result["approval_result"] == "edited"
    assert result["selected_tool_args"] == new_args


@pytest.mark.asyncio
async def test_interrupt_caller_timeout_decision(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.core.settings import SETTINGS
    from app.graph.nodes import interrupt_caller

    monkeypatch.setattr(SETTINGS, "approval_timeout_seconds", 30)
    monkeypatch.setattr(
        "app.graph.nodes.interrupt",
        lambda _: {"decision": "timeout"},
    )

    state: dict[str, Any] = {
        "selected_tool": "create_payment",
        "selected_tool_args": {},
        "pending_approval": {},
    }
    result = await interrupt_caller(state)
    assert result["approval_result"] == "timeout"
    assert "selected_tool_args" not in result


# ── execute_tool tool_call_history ─────────────────────────────────────────


@pytest.mark.asyncio
async def test_execute_tool_emits_tool_call_history_on_success(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.graph.nodes import execute_tool

    async def fake_invoke_tool(
        tool_name: str,
        tool_args: dict,
        **__: object,
    ) -> dict:
        return {"ok": True, "tool": tool_name, "data": {}, "raw": {}}

    monkeypatch.setattr("app.graph.nodes.MCP_CLIENT.invoke_tool", fake_invoke_tool)

    state: dict[str, Any] = {
        "selected_tool": "list_promotions",
        "selected_tool_args": {},
        "tool_results": [],
        "tool_errors": [],
        "tool_call_history": [],
    }
    result = await execute_tool(state)

    assert result["final_status"] == "executed"
    assert len(result["tool_call_history"]) == 1
    call = result["last_tool_call"]
    assert call["name"] == "list_promotions"
    assert call["status"] == "completed"
    assert call["started_at"] is not None
    assert call["completed_at"] is not None
    assert call["result"]["ok"] is True
    assert call["error"] is None


@pytest.mark.asyncio
async def test_execute_tool_emits_error_call_on_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.graph.nodes import execute_tool

    async def fake_invoke_tool(
        tool_name: str,
        tool_args: dict,
        **__: object,
    ) -> dict:
        return {"ok": False, "statusCode": 500, "tag": "ERR", "message": "fail"}

    monkeypatch.setattr("app.graph.nodes.MCP_CLIENT.invoke_tool", fake_invoke_tool)

    state: dict[str, Any] = {
        "selected_tool": "create_payment",
        "selected_tool_args": {},
        "tool_results": [],
        "tool_errors": [],
        "tool_call_history": [],
    }
    result = await execute_tool(state)

    assert result["final_status"] == "tool_error"
    assert len(result["tool_call_history"]) == 1
    call = result["last_tool_call"]
    assert call["status"] == "error"
    assert call["error"]["ok"] is False


# ── timeout_registry ────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_schedule_timeout_creates_pending_task() -> None:
    class FakeGraph:
        async def ainvoke(self, *args, **kwargs):  # pragma: no cover
            pass

    thread_id = "test-thread-schedule"
    try:
        schedule_timeout(thread_id, graph=FakeGraph(), timeout_seconds=300)
        assert thread_id in pending_timeout_ids()
    finally:
        cancel_timeout(thread_id)


@pytest.mark.asyncio
async def test_cancel_timeout_removes_task() -> None:
    class FakeGraph:
        async def ainvoke(self, *args, **kwargs):  # pragma: no cover
            pass

    thread_id = "test-thread-cancel"
    schedule_timeout(thread_id, graph=FakeGraph(), timeout_seconds=300)
    cancelled = cancel_timeout(thread_id)
    assert cancelled is True
    assert thread_id not in pending_timeout_ids()


@pytest.mark.asyncio
async def test_cancel_timeout_returns_false_when_no_task() -> None:
    result = cancel_timeout("nonexistent-thread")
    assert result is False


@pytest.mark.asyncio
async def test_schedule_timeout_replaces_existing_task() -> None:
    class FakeGraph:
        async def ainvoke(self, *args, **kwargs):  # pragma: no cover
            pass

    thread_id = "test-thread-replace"
    try:
        schedule_timeout(thread_id, graph=FakeGraph(), timeout_seconds=300)
        schedule_timeout(thread_id, graph=FakeGraph(), timeout_seconds=300)
        # Should still only be one entry
        assert pending_timeout_ids().count(thread_id) == 1
    finally:
        cancel_timeout(thread_id)


@pytest.mark.asyncio
async def test_auto_timeout_fires_after_delay() -> None:
    """Auto-timeout task resumes graph with timeout decision after delay."""
    resumed_with: list[Any] = []

    class FakeGraph:
        async def ainvoke(self, command: Any, **kwargs) -> None:
            resumed_with.append(command)

    thread_id = "test-thread-auto-timeout"
    schedule_timeout(thread_id, graph=FakeGraph(), timeout_seconds=0)
    # Yield control so the task can execute
    await asyncio.sleep(0.05)
    assert len(resumed_with) == 1
    from langgraph.types import Command
    cmd = resumed_with[0]
    assert isinstance(cmd, Command)
    assert cmd.resume == {"decision": "timeout"}
