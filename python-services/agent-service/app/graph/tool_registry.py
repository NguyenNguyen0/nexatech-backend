from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class ToolPlan:
    tool_name: str
    tool_args: dict[str, Any] = field(default_factory=dict)
    required_role: str = "operator"
    rationale: str = ""


@dataclass(frozen=True)
class ToolDescriptor:
    tool_name: str
    keywords: tuple[str, ...]
    required_role: str = "operator"
    rationale: str = ""
    tool_args: dict[str, Any] = field(default_factory=dict)
    heuristic_enabled: bool = True


TOOL_DESCRIPTORS: tuple[ToolDescriptor, ...] = (
    ToolDescriptor(
        tool_name="list_promotions",
        keywords=(
            "promotion",
            "promotions",
            "khuyến mãi",
            "khuyến mại",
            "khuyen mai",
            "ưu đãi",
            "uu dai",
            "voucher",
        ),
        rationale="Use promotion listing when the request asks for current campaigns or promotions.",
    ),
    ToolDescriptor(
        tool_name="list_flash_sales",
        keywords=("flash sale", "flash-sales", "giảm sốc", "giam soc"),
        rationale="Use flash-sale listing when the request is about flash sale campaigns.",
    ),
    ToolDescriptor(
        tool_name="list_all_news",
        keywords=(
            "all news",
            "admin news",
            "tất cả tin tức",
            "tat ca tin tuc",
            "toàn bộ tin tức",
            "toan bo tin tuc",
            "bản nháp",
            "ban nhap",
            "draft",
        ),
        tool_args={"page": 0, "size": 20},
        rationale="Use all-news listing when the request asks for the complete/admin news catalog.",
    ),
    ToolDescriptor(
        tool_name="get_news_by_slug",
        keywords=("news slug", "admin news slug", "chi tiết tin theo slug"),
        rationale="Use admin news detail when the request provides a news slug and may include non-public news.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="get_public_news_by_slug",
        keywords=("public news slug", "tin công khai theo slug", "tin cong khai theo slug"),
        rationale="Use public news detail when the request provides a public news slug.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="get_news_images",
        keywords=("news images", "ảnh tin tức", "anh tin tuc", "hình tin tức", "hinh tin tuc"),
        rationale="Use news image listing when the request provides a news id.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="get_news_image",
        keywords=("news image", "ảnh tin", "anh tin", "image id"),
        rationale="Use single news image lookup when the request provides an image id.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="list_news",
        keywords=("news", "tin tức", "tin tuc", "article", "bài viết", "bai viet"),
        tool_args={"page": 0, "size": 10},
        rationale="Use news listing when the request asks for articles or public news.",
    ),
    ToolDescriptor(
        tool_name="search_audit_logs",
        keywords=("audit", "log", "logs", "nhật ký", "nhat ky", "trace"),
        required_role="manager",
        tool_args={"params": {}},
        rationale="Use audit log search when the request asks for audit history or traces.",
    ),
)


def extract_latest_user_text(state: dict[str, Any]) -> str:
    messages = state.get("messages") or []
    if not messages:
        return ""

    last_message = messages[-1]
    if isinstance(last_message, str):
        return last_message.strip()
    if isinstance(last_message, dict):
        content = last_message.get("content")
        if isinstance(content, str):
            return content.strip()
        if isinstance(content, list):
            return " ".join(str(item) for item in content if item).strip()

    content = getattr(last_message, "content", "")
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        return " ".join(str(item) for item in content if item).strip()
    return str(content).strip()


def _normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text.casefold()).strip()


def plan_tool_call(user_text: str) -> ToolPlan | None:
    normalized_text = _normalize(user_text)
    if not normalized_text:
        return None

    for descriptor in TOOL_DESCRIPTORS:
        if not descriptor.heuristic_enabled:
            continue
        if any(keyword in normalized_text for keyword in descriptor.keywords):
            return ToolPlan(
                tool_name=descriptor.tool_name,
                tool_args=dict(descriptor.tool_args),
                required_role=descriptor.required_role,
                rationale=descriptor.rationale,
            )

    return None
