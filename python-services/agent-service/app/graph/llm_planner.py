from __future__ import annotations

import json
import logging
import re
from typing import Any

from app.core.settings import SETTINGS
from app.graph.tool_registry import TOOL_DESCRIPTORS, ToolPlan


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


def _build_tool_catalog() -> str:
    lines: list[str] = []
    for descriptor in TOOL_DESCRIPTORS:
        lines.append(
            f"- {descriptor.tool_name}: keywords={', '.join(descriptor.keywords)}, "
            f"required_role={descriptor.required_role}, default_args={descriptor.tool_args}"
        )
    return "\n".join(lines)


async def plan_tool_call_with_llm(user_text: str) -> ToolPlan | None:
    if not user_text.strip() or not SETTINGS.groq_api_key:
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

    system_prompt = (
        "You are a strict tool planner for NexaTech admin agent. "
        "Select exactly one tool from the provided catalog. "
        "Return ONLY a JSON object with keys: tool_name, tool_args, required_role, rationale. "
        "If there is no suitable tool, return {\"tool_name\": null}.\n\n"
        f"Tool catalog:\n{_build_tool_catalog()}"
    )

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

    descriptor = next((item for item in TOOL_DESCRIPTORS if item.tool_name == tool_name), None)
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