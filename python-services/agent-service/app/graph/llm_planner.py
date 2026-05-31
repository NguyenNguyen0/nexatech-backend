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


_AGENTIC_NO_VERBOSE_LEAK_RULES = """
Quy tac bat buoc de tranh verbose thinking leak:
- Khi user yeu cau tac vu cu the va co tool phu hop, chon tool ngay.
- Khong giai thich truoc, khong viet ke hoach, khong liet ke available tools.
- Khong noi "toi se thuc hien", "truoc tien toi can", "de lam viec nay".
- Planner mode chi tra ve JSON hop le, khong markdown, khong prose, khong code fence.
- Response mode chi tra loi sau khi co context/tool result; khong mo phong viec goi tool.
"""


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


def _parse_function_call_args(raw_args: str) -> dict[str, Any]:
    if not raw_args.strip():
        return {}

    parsed: dict[str, Any] = {}
    for key, value in re.findall(
        r"([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(\"[^\"]*\"|'[^']*'|[^,\]\)]+)",
        raw_args,
    ):
        clean_value = value.strip().strip("\"'")
        clean_key = {
            "category_slug": "slug",
            "product_slug": "slug",
            "product_id": "product_id",
            "review_id": "review_id",
        }.get(key, key)
        parsed[clean_key] = clean_value
    return parsed


def _extract_tool_call_from_verbose_text(
    raw_text: str,
    available_tools: list[str],
) -> dict[str, Any] | None:
    text = raw_text.strip()
    if not text:
        return None

    for match in re.finditer(r"\b([a-zA-Z_][a-zA-Z0-9_]*)\s*\(([^)]*)\)", text):
        tool_name = match.group(1)
        if tool_name in available_tools:
            return {
                "tool_name": tool_name,
                "tool_args": _parse_function_call_args(match.group(2)),
                "required_role": "operator",
                "rationale": "parsed_from_verbose_tool_call",
            }

    for descriptor in TOOL_DESCRIPTORS:
        if descriptor.tool_name in available_tools and descriptor.tool_name in text:
            return {
                "tool_name": descriptor.tool_name,
                "tool_args": dict(descriptor.tool_args),
                "required_role": descriptor.required_role,
                "rationale": "parsed_from_verbose_tool_name",
            }

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
        f"{_AGENTIC_NO_VERBOSE_LEAK_RULES}\n\n"
        "[Planner mode] Ban dang o che do tool planner. "
        "Chi duoc phep chon dung 1 tool phu hop nhat tu danh sach duoc cung cap. "
        "Neu user yeu cau hanh dong doc/tra cuu/liet ke/thong ke/co tool phu hop, "
        "hay chon tool ngay thay vi tra loi bang loi.\n"
        "Bat buoc tra ve DUY NHAT mot JSON object hop le.\n\n"
        'Dinh dang bat buoc: {"tool_name": string|null, "tool_args": object, "required_role": string, "rationale": string}.\n'
        'Neu khong co tool phu hop thi tra {"tool_name": null, "tool_args": {}, "required_role": "operator", "rationale": "no_matching_tool"}.\n\n'
        "Vi du dung:\n"
        'User: "liet ke san pham danh muc hera-pc"\n'
        'Assistant: {"tool_name":"list_products_by_category_slug","tool_args":{"slug":"hera-pc"},"required_role":"operator","rationale":"category product listing"}\n'
        'User: "xem review cua san pham abc"\n'
        'Assistant: {"tool_name":"list_reviews_by_product","tool_args":{"product_id":"abc"},"required_role":"operator","rationale":"product reviews"}\n\n'
        "Vi du sai, tuyet doi khong lam:\n"
        'Assistant: "De liet ke san pham, truoc tien toi can kiem tra tool..."\n'
        'Assistant: "Toi se goi tool list_products_by_category_slug..."\n'
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
        temperature=SETTINGS.groq_temperature,
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

    response_content = getattr(response, "content", "") or ""
    parsed = _extract_json_object(response_content)
    if not parsed:
        parsed = _extract_tool_call_from_verbose_text(response_content, available_tools)
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
        temperature=SETTINGS.groq_temperature,
    )
    system_prompt = (
        f"{load_default_system_prompt()}\n\n"
        f"{_AGENTIC_NO_VERBOSE_LEAK_RULES}\n\n"
        "[Response mode] Hãy tự tạo câu trả lời cuối cùng cho quản trị viên dựa trên context JSON. "
        "Không dùng template cố định. Không bịa dữ liệu. Nếu context chứa lỗi hoặc thiếu tool, "
        "nói rõ giới hạn dựa trên chính context đó. Nếu có tool_results, tóm tắt dữ liệu thật từ tool_results. "
        "Nếu context không có tool_results và request là tác vụ cần dữ liệu, không được nói rằng đang/ sẽ gọi tool; "
        "chỉ nêu rằng hệ thống chưa chọn hoặc chưa thực thi được tool phù hợp."
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
