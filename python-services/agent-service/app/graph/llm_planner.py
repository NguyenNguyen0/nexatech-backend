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
STRICT ANTI-VERBOSE RULES (must follow without exception):
- Khong giai thich truoc.
- DUY NHAT mot JSON object.
- NEVER explain your reasoning before acting. NEVER list available tools.
- NEVER say "tôi sẽ", "trước tiên", "để làm việc này", "tôi cần kiểm tra", "xin hãy xác nhận".
- NEVER write prose, plans, or thinking steps visible to the user.
- PLANNER MODE: output ONLY a single valid JSON object — no markdown, no code fences, no text outside JSON.
- RESPONSE MODE: output ONLY the final answer based on tool_results — never simulate or narrate tool calls.
- If unsure, pick the closest tool and return JSON immediately.
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
    """Build a compact catalog string for the LLM planner.

    Format: one line per tool — "tool_name: short description"
    Only tools actually registered on the MCP server are included.
    Keywords and default_args are intentionally omitted to keep token count low.
    """
    lines: list[str] = []
    for descriptor in TOOL_DESCRIPTORS:
        if descriptor.tool_name in available_tools:
            lines.append(f"{descriptor.tool_name}: {descriptor.llm_hint()}")
    return "\n".join(lines)


def _planner_system_prompt() -> str:
    return (
        f"{_AGENTIC_NO_VERBOSE_LEAK_RULES}\n\n"
        "You are a JSON-only tool planner. Your sole output is one JSON object.\n"
        "Required format: "
        '{"tool_name": string|null, "tool_args": object, "required_role": string, "rationale": string}\n'
        "If no tool fits: "
        '{"tool_name": null, "tool_args": {}, "required_role": "operator", "rationale": "no_matching_tool"}\n\n'
        "CORRECT examples:\n"
        'User: "liệt kê sản phẩm danh mục hera-pc"\n'
        'Output: {"tool_name":"list_products_by_category_slug","tool_args":{"slug":"hera-pc"},"required_role":"operator","rationale":"category product listing"}\n'
        'User: "xem review sản phẩm abc-123"\n'
        'Output: {"tool_name":"list_reviews_by_product","tool_args":{"product_id":"abc-123"},"required_role":"operator","rationale":"product reviews"}\n\n'
        "WRONG — never do this:\n"
        'Output: "Để liệt kê sản phẩm, trước tiên tôi cần kiểm tra tool..."\n'
        'Output: "Tôi sẽ gọi tool list_products_by_category_slug để..."\n'
        'Output: "Xin hãy xác nhận trước khi tôi tiếp tục."\n'
    )


async def plan_tool_call_with_llm(
    user_text: str,
    *,
    available_tools: list[str] | None = None,
) -> ToolPlan | None:
    if not user_text.strip() or not SETTINGS.groq_api_key:
        return None

    if available_tools is None:
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
        temperature=0,  # deterministic output for planner — JSON only, no creative drift
    )

    system_prompt = f"{_planner_system_prompt()}\n\nAvailable tools:\n{await _build_tool_catalog(available_tools)}"

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




def _truncate_data(data: Any, max_list_size: int = 5, max_string_len: int = 200) -> Any:
    """Recursively truncate large lists and strings to keep context size small and avoid LLM rate limits."""
    if isinstance(data, dict):
        return {k: _truncate_data(v, max_list_size, max_string_len) for k, v in data.items()}
    if isinstance(data, list):
        if len(data) > max_list_size:
            truncated = [_truncate_data(item, max_list_size, max_string_len) for item in data[:max_list_size]]
            truncated.append(f"... (truncated {len(data) - max_list_size} items)")
            return truncated
        return [_truncate_data(item, max_list_size, max_string_len) for item in data]
    if isinstance(data, str):
        if len(data) > max_string_len:
            return data[:max_string_len] + "..."
    return data


async def synthesize_agent_response(state: dict[str, Any]) -> str:
    if not SETTINGS.groq_api_key:
        logger.error("LLM response synthesis is unavailable: GROQ_API_KEY is not configured.")
        raise RuntimeError("LLM response synthesis unavailable: GROQ_API_KEY is not configured")

    # Hard guard: never invoke LLM synthesis when there are no tool results
    # for a state that expected a tool execution — the LLM would hallucinate data.
    final_status = state.get("final_status")
    tool_results = state.get("tool_results") or []
    tool_errors = state.get("tool_errors") or []
    selected_tool = state.get("selected_tool")

    DATA_NEEDED_STATUSES = {"executed", "tool_error"}
    if final_status in DATA_NEEDED_STATUSES and not tool_results and not tool_errors:
        logger.error(
            "Synthesis guard: final_status=%s but tool_results and tool_errors are both empty — refusing to hallucinate. tool=%s",
            final_status,
            selected_tool,
        )
        return "Hệ thống đã chọn tool nhưng chưa nhận được kết quả từ MCP server. Vui lòng thử lại."

    if final_status == "no_matching_tool":
        available = state.get("available_tools") or []
        if available:
            return f"Không tìm thấy tool phù hợp cho yêu cầu này. Các tool hiện có: {', '.join(available)}."
        return "Không tìm thấy tool phù hợp cho yêu cầu này."

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
        "available_tools": (
            state.get("available_tools") or []
            if state.get("final_status") == "no_matching_tool"
            else []
        ),
        "selected_tool": state.get("selected_tool"),
        "selected_tool_args": state.get("selected_tool_args") or {},
        "tool_results": _truncate_data(state.get("tool_results") or []),
        "tool_errors": state.get("tool_errors") or [],
        "approval_result": state.get("approval_result"),
        "request_sensitivity": state.get("request_sensitivity"),
        "answer_context": _truncate_data(state.get("answer_context") or {}),
    }

    llm = ChatGroq(
        model=SETTINGS.groq_model,
        api_key=SETTINGS.groq_api_key,
        temperature=SETTINGS.groq_temperature,
    )
    system_prompt = (
        f"{load_default_system_prompt()}\n\n"
        f"{_AGENTIC_NO_VERBOSE_LEAK_RULES}\n\n"
        "[Response mode] Tạo câu trả lời cuối cùng cho quản trị viên từ context JSON dưới đây.\n"
        "TUYỆT ĐỐI KHÔNG: viết 'tôi sẽ gọi tool', 'đang kiểm tra', 'trước tiên cần', "
        "'xin xác nhận', hoặc bất kỳ mô tả nào về quá trình nội bộ.\n"
        "Nếu tool_results có dữ liệu → tóm tắt dữ liệu thực từ tool_results.\n"
        "Nếu final_status là 'no_matching_tool' → thông báo ngắn gọn rằng không tìm thấy tool phù hợp.\n"
        "Nếu final_status là 'tool_error' → thông báo lỗi từ answer_context.\n"
        "Không bịa dữ liệu. Không suy đoán. Không dùng template cố định."
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
