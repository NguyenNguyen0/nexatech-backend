from __future__ import annotations

import json
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from types import SimpleNamespace

import pytest
import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.graph.agent import compile_graph
from app.server import READINESS_CHECKS, AgentRunRequest, app, get_conversation_thread, run_agent_stream


@pytest.fixture(autouse=True)
def isolate_runtime_dependencies_for_tests(monkeypatch: pytest.MonkeyPatch):
    previous_eureka_enabled = SETTINGS.eureka_enabled
    previous_agent_db_url = SETTINGS.agent_db_url
    previous_conversation_store_enabled = SETTINGS.conversation_store_enabled
    previous_langsmith_tracing = SETTINGS.langsmith_tracing
    SETTINGS.eureka_enabled = False
    SETTINGS.agent_db_url = None
    SETTINGS.conversation_store_enabled = False
    SETTINGS.langsmith_tracing = False
    monkeypatch.setenv("LANGSMITH_TRACING", "false")
    monkeypatch.setenv("LANGCHAIN_TRACING_V2", "false")
    READINESS_CHECKS["eureka"] = "disabled"
    READINESS_CHECKS["checkpointer"] = "disabled"
    READINESS_CHECKS["conversation_store"] = "disabled"

    async def fake_available_tools(_: str, **__: object) -> list[str]:
        return [
            "list_promotions",
            "list_products",
            "get_audit_events",
        ]

    monkeypatch.setattr("app.server._log_connected_mcp_tools", fake_available_tools)
    monkeypatch.setattr("app.server.attach_copilotkit_endpoint", lambda *_, **__: False)

    @asynccontextmanager
    async def noop_lifespan(_app):
        _app.state.compiled_graph = compile_graph()
        _app.state.conversation_store = None
        _app.state.active_stream_runs = {}
        yield

    previous_lifespan_context = app.router.lifespan_context
    app.router.lifespan_context = noop_lifespan
    app.state.compiled_graph = compile_graph()
    app.state.conversation_store = None
    app.state.active_stream_runs = {}
    yield
    app.router.lifespan_context = previous_lifespan_context
    SETTINGS.eureka_enabled = previous_eureka_enabled
    SETTINGS.agent_db_url = previous_agent_db_url
    SETTINGS.conversation_store_enabled = previous_conversation_store_enabled
    SETTINGS.langsmith_tracing = previous_langsmith_tracing


@pytest.mark.asyncio
async def test_run_agent_executes_low_sensitivity_flow(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_invoke_graph(
        graph_input: object, *, thread_id: str
    ) -> dict[str, object]:
        assert isinstance(graph_input, dict)
        assert graph_input["available_tools"] == [
            "list_promotions",
            "list_products",
            "get_audit_events",
        ]
        return {
            "threadId": thread_id,
            "status": "completed",
            "output": {"selected_tool": "list_promotions"},
            "interrupts": [],
            "state": {"values": {}, "next": [], "interrupts": []},
        }

    monkeypatch.setattr("app.server._invoke_graph", fake_invoke_graph)

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.post(
            "/api/v1/agent/runs",
            json={"message": "Cho tôi xem các chương trình khuyến mãi hiện tại"},
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "completed"
    assert payload["output"]["selected_tool"] == "list_promotions"
    assert payload["interrupts"] == []


@pytest.mark.asyncio
async def test_resume_agent_requires_checkpointer() -> None:
    READINESS_CHECKS["checkpointer"] = "disabled"
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.post(
            "/api/v1/agent/runs/resume",
            json={"threadId": "demo-thread", "decision": "approved"},
        )

    assert response.status_code == 503
    assert response.json()["message"] == "Resume requires the LangGraph checkpointer"


@pytest.mark.asyncio
async def test_run_agent_accepts_gateway_style_path_and_merges_header_roles(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    async def fake_invoke_graph(
        graph_input: object, *, thread_id: str
    ) -> dict[str, object]:
        assert isinstance(graph_input, dict)
        captured["caller"] = graph_input.get("caller")
        return {
            "threadId": thread_id,
            "status": "completed",
            "output": {"selected_tool": "list_promotions"},
            "interrupts": [],
            "state": {"values": {}, "next": [], "interrupts": []},
        }

    monkeypatch.setattr("app.server._invoke_graph", fake_invoke_graph)

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.post(
            "/runs",
            json={
                "message": "liet ke khuyen mai",
                "caller": {"roles": ["operator"]},
            },
            headers={
                "X-User-Id": "user-123",
                "X-User-Role": "manager,operator",
                "X-User-Email": "admin@nexatech.local",
            },
        )

    assert response.status_code == 200
    assert captured["caller"] == {
        "roles": ["operator", "manager"],
        "userId": "user-123",
        "email": "admin@nexatech.local",
    }


@pytest.mark.asyncio
async def test_stream_endpoint_emits_stable_events(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_invoke_graph(
        graph_input: object, *, thread_id: str
    ) -> dict[str, object]:
        return {
            "threadId": thread_id,
            "status": "completed",
            "output": {
                "selected_tool": "list_promotions",
                "response_text": "Da xu ly yeu cau.",
                "tool_errors": [],
            },
            "interrupts": [],
            "state": {
                "values": {
                    "selected_tool": "list_promotions",
                    "response_text": "Da xu ly yeu cau.",
                    "tool_errors": [],
                },
                "next": [],
                "interrupts": [],
            },
        }

    monkeypatch.setattr("app.server._invoke_graph", fake_invoke_graph)

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        async with client.stream(
            "POST",
            "/api/v1/agent/runs/stream",
            json={
                "message": "xin chao",
                "runId": "run-test-1",
                "threadId": "thread-test-1",
            },
            headers={"X-User-Id": "user-123", "X-User-Role": "operator"},
        ) as response:
            payload = (await response.aread()).decode("utf-8")

    assert response.status_code == 200
    assert "event: run.started" in payload
    assert "event: tool.started" in payload
    assert "event: tool.completed" in payload
    assert "event: message.chunk" in payload
    assert "event: run.completed" in payload


@pytest.mark.asyncio
async def test_stream_endpoint_lets_graph_handle_empty_tool_catalog(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class _FakeStore:
        def __init__(self) -> None:
            self.events: dict[str, list[dict[str, object]]] = {}

        async def append_event(
            self,
            *,
            thread_id: str,
            event_type: str,
            payload: dict[str, object],
            correlation_id: str | None,
            user_id: str | None,
            user_role: str | None,
            user_email: str | None,
        ) -> None:
            self.events.setdefault(thread_id, []).append(
                {
                    "eventType": event_type,
                    "payload": payload,
                    "correlationId": correlation_id,
                    "userId": user_id,
                    "userRole": user_role,
                    "userEmail": user_email,
                }
            )

        async def get_thread(self, *, thread_id: str, limit: int = 100):
            events = self.events.get(thread_id)
            if events is None:
                return None
            return {"threadId": thread_id, "events": events[-limit:]}

        async def close(self):
            return None

    async def no_available_tools(_: str, **__: object) -> list[str]:
        return []

    async def fake_invoke_graph(
        graph_input: object, *, thread_id: str
    ) -> dict[str, object]:
        assert isinstance(graph_input, dict)
        assert graph_input["available_tools"] == []
        return {
            "threadId": thread_id,
            "status": "completed",
            "output": {
                "response_text": "LLM synthesized response",
                "tool_errors": [],
            },
            "interrupts": [],
            "state": {
                "values": {
                    "response_text": "LLM synthesized response",
                    "tool_errors": [],
                },
                "next": [],
                "interrupts": [],
            },
        }

    monkeypatch.setattr("app.server._log_connected_mcp_tools", no_available_tools)
    monkeypatch.setattr("app.server._invoke_graph", fake_invoke_graph)
    app.state.conversation_store = _FakeStore()
    raw_request = SimpleNamespace(
        headers={
            "X-User-Id": "user-123",
            "X-User-Role": "operator",
        },
        state=SimpleNamespace(correlation_id="corr-123"),
    )

    response = await run_agent_stream(
        AgentRunRequest(
            message="liet ke tool",
            runId="run-no-tools",
            threadId="thread-no-tools",
        ),
        raw_request,
    )
    payload = "".join([chunk async for chunk in response.body_iterator])
    conversation = await get_conversation_thread("thread-no-tools", limit=100)

    assert "event: run.completed" in payload
    assert "LLM synthesized response" in payload
    events = conversation["events"]
    assert [event["eventType"] for event in events] == ["user_message", "agent_result"]


@pytest.mark.asyncio
async def test_stream_tool_events_share_a_stable_call_id(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_invoke_graph(
        graph_input: object, *, thread_id: str
    ) -> dict[str, object]:
        return {
            "threadId": thread_id,
            "status": "completed",
            "output": {"selected_tool": "list_products", "tool_errors": []},
            "interrupts": [],
            "state": {
                "values": {
                    "selected_tool": "list_products",
                    "tool_errors": [],
                    "response_text": "Hiện tại có 803 sản phẩm.",
                    "last_tool_call": {
                        "id": "call-abc-123",
                        "name": "list_products",
                        "status": "completed",
                        "result": {"ok": True, "data": {"total": 803}},
                    },
                },
                "next": [],
                "interrupts": [],
            },
        }

    monkeypatch.setattr("app.server._invoke_graph", fake_invoke_graph)

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        async with client.stream(
            "POST",
            "/api/v1/agent/runs/stream",
            json={"message": "liệt kê sản phẩm", "runId": "run-call-id-test"},
            headers={"X-User-Id": "user-123", "X-User-Role": "operator"},
        ) as response:
            payload = (await response.aread()).decode("utf-8")

    frames = [block for block in payload.split("\n\n") if block.strip()]

    def frame_data(event_name: str) -> dict[str, object]:
        for frame in frames:
            if f"event: {event_name}" in frame:
                json_line = frame.split("data: ", 1)[1]
                return json.loads(json_line)["data"]
        raise AssertionError(f"no frame found for event {event_name}")

    started_call_id = frame_data("tool.started")["callId"]
    completed_data = frame_data("tool.completed")
    assert completed_data["callId"] == started_call_id
    assert completed_data["result"] == {"ok": True, "data": {"total": 803}}


@pytest.mark.asyncio
async def test_interrupt_route_requires_checkpointer() -> None:
    READINESS_CHECKS["checkpointer"] = "disabled"
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        response = await client.post(
            "/api/v1/agent/runs/interrupts",
            json={"threadId": "demo-thread"},
        )

    assert response.status_code == 503
    assert (
        response.json()["message"]
        == "Interrupt state requires the LangGraph checkpointer"
    )


@pytest.mark.asyncio
async def test_list_conversations_requires_user_header_when_query_missing() -> None:
    class _FakeStore:
        async def list_threads_by_user(self, *, user_id: str, limit: int = 20):
            return []

        async def close(self):
            return None

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://testserver",
    ) as client:
        app.state.conversation_store = _FakeStore()
        response = await client.get("/api/v1/agent/conversations")

    assert response.status_code == 400
    assert response.json()["message"] == "X-User-Id header is required"
