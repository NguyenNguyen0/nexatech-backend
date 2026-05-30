from __future__ import annotations

import json
import logging
import re
from typing import Any

from app.core.settings import SETTINGS
from app.graph.tool_registry import TOOL_DESCRIPTORS, ToolPlan
from app.mcp_client import MCP_CLIENT
from app.prompts.default import load_default_system_prompt


logger = logging.getLogger(__name__)


def _extract_json_object(raw_text: str) -> dict[str, Any] | None:
    text = raw_text.strip()
    if not text:
        return None

    try:
        parsed = json.loads(text)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{.*\}", text, flags=re.DOTALL)
    if not match:
        return None

    try:
        parsed = json.loads(match.group(0))
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        return None

    return None


async def _build_tool_catalog(available_tools: list[str]) -> str:
    lines: list[str] = []
    for descriptor in TOOL_DESCRIPTORS:
        if descriptor.tool_name in available_tools:
            lines.append(
                f"- {descriptor.tool_name}: keywords={', '.join(descriptor.keywords)}, "
                f"required_role={descriptor.required_role}, default_args={descriptor.tool_args}"
            )
    return "\n".join(lines)


def _planner_system_prompt() -> str:
    base_prompt = load_default_system_prompt()
    return (
        f"{base_prompt}\n\n"
        "[Planner mode] Ban dang o che do tool planner. "
        "Chi duoc phep chon dung 1 tool phu hop nhat tu danh sach duoc cung cap. "
        "Bat buoc tra ve DUY NHAT mot JSON object hop le.\n\n"
        'Dinh dang bat buoc: {"tool_name": string|null, "tool_args": object, "required_role": string, "rationale": string}.\n'
        'Neu khong co tool phu hop thi tra {"tool_name": null}.'
    )


async def plan_tool_call_with_llm(user_text: str) -> ToolPlan | None:
    if not user_text.strip() or not SETTINGS.groq_api_key:
        return None

    available_tools = await MCP_CLIENT.list_available_tools()
    if not available_tools:
        logger.error("No available tools from MCP server for planner.")
        return None

    try:
        from langchain_core.messages import HumanMessage, SystemMessage
        from langchain_groq import ChatGroq
    except Exception as exc:  # pragma: no cover
        logger.warning("LLM planner dependencies are not available: %s", exc)
        return None

    llm = ChatGroq(
        model=SETTINGS.groq_model,
        api_key=SETTINGS.groq_api_key,
        temperature=0,
    )

    system_prompt = f"{_planner_system_prompt()}\n\nTool catalog:\n{await _build_tool_catalog(available_tools)}"

    try:
        response = await llm.ainvoke(
            [
                SystemMessage(content=system_prompt),
                HumanMessage(content=user_text),
            ]
        )
    except Exception as exc:  # pragma: no cover
        logger.warning("LLM planner call failed: %s", exc)
        return None

    parsed = _extract_json_object(getattr(response, "content", "") or "")
    if not parsed:
        return None

    tool_name = parsed.get("tool_name")
    if not isinstance(tool_name, str) or not tool_name:
        return None

    descriptor = next(
        (
            item
            for item in TOOL_DESCRIPTORS
            if item.tool_name == tool_name and tool_name in available_tools
        ),
        None,
    )
    if descriptor is None:
        return None

    tool_args = parsed.get("tool_args")
    if not isinstance(tool_args, dict):
        tool_args = dict(descriptor.tool_args)

    required_role = parsed.get("required_role")
    if not isinstance(required_role, str) or not required_role:
        required_role = descriptor.required_role

    rationale = parsed.get("rationale")
    if not isinstance(rationale, str):
        rationale = descriptor.rationale

    return ToolPlan(
        tool_name=tool_name,
        tool_args=tool_args,
        required_role=required_role,
        rationale=rationale,
    )


async def synthesize_agent_response(state: dict[str, Any]) -> str:
    if not SETTINGS.groq_api_key:
        logger.error("LLM response synthesis is unavailable: GROQ_API_KEY is not configured.")
        raise RuntimeError("LLM response synthesis unavailable: GROQ_API_KEY is not configured")

    try:
        from langchain_core.messages import HumanMessage, SystemMessage
        from langchain_groq import ChatGroq
    except Exception as exc:  # pragma: no cover
        logger.exception("LLM response synthesis dependencies are unavailable.")
        raise RuntimeError(
            f"LLM response synthesis dependencies unavailable: {exc.__class__.__name__}"
        ) from exc

    user_text = state.get("input_text") or ""
    response_context = {
        "user_text": user_text,
        "final_status": state.get("final_status"),
        "available_tools": state.get("available_tools") or [],
        "selected_tool": state.get("selected_tool"),
        "selected_tool_args": state.get("selected_tool_args") or {},
        "tool_results": state.get("tool_results") or [],
        "tool_errors": state.get("tool_errors") or [],
        "approval_result": state.get("approval_result"),
        "request_sensitivity": state.get("request_sensitivity"),
        "answer_context": state.get("answer_context") or {},
    }

    llm = ChatGroq(
        model=SETTINGS.groq_model,
        api_key=SETTINGS.groq_api_key,
        temperature=0,
    )
    system_prompt = (
        f"{load_default_system_prompt()}\n\n"
        "[Response mode] Hãy tự tạo câu trả lời cuối cùng cho quản trị viên dựa trên context JSON. "
        "Không dùng template cố định. Không bịa dữ liệu. Nếu context chứa lỗi hoặc thiếu tool, "
        "nói rõ giới hạn dựa trên chính context đó. Nếu có tool_results, tóm tắt dữ liệu thật từ tool_results."
    )

    try:
        response = await llm.ainvoke(
            [
                SystemMessage(content=system_prompt),
                HumanMessage(
                    content=json.dumps(
                        response_context,
                        ensure_ascii=False,
                        indent=2,
                        default=str,
                    )
                ),
            ]
        )
    except Exception as exc:  # pragma: no cover
        logger.exception("LLM response synthesis call failed.")
        raise RuntimeError(
            f"LLM response synthesis failed: {exc.__class__.__name__}"
        ) from exc

    content = getattr(response, "content", "") or ""
    if not isinstance(content, str) or not content.strip():
        logger.error("LLM response synthesis returned an empty response.")
        raise RuntimeError("LLM response synthesis returned an empty response")
    return content.strip()
