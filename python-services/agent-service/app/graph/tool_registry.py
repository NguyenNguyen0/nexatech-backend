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
        tool_name="list_inventory",
        keywords=(
            "inventory",
            "stock",
            "tồn kho",
            "ton kho",
            "kho hàng",
            "kho hang",
        ),
        tool_args={"params": {}},
        rationale="Use inventory listing when the request asks to browse or search stock items.",
    ),
    ToolDescriptor(
        tool_name="get_low_stock_items",
        keywords=(
            "low stock",
            "sắp hết hàng",
            "sap het hang",
            "thiếu hàng",
            "thieu hang",
            "dưới ngưỡng",
            "duoi nguong",
        ),
        rationale="Use low-stock lookup when the request asks for products below reorder level.",
    ),
    ToolDescriptor(
        tool_name="get_inventory",
        keywords=("inventory product", "stock product", "tồn kho sản phẩm", "ton kho san pham"),
        rationale="Use single inventory lookup when the request provides a product id.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="get_inventory_movements",
        keywords=("inventory movements", "stock movements", "lịch sử kho", "lich su kho"),
        rationale="Use inventory movement lookup when the request provides a product id.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="check_inventory",
        keywords=("check inventory", "kiểm tra tồn kho", "kiem tra ton kho"),
        rationale="Use stock availability check when the request provides product ids and quantities.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="update_stock",
        keywords=("update stock", "cập nhật tồn kho", "cap nhat ton kho"),
        rationale="Use stock update when the request provides product id and new quantity.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="reserve_stock",
        keywords=("reserve stock", "giữ hàng", "giu hang", "đặt giữ tồn kho"),
        rationale="Use stock reservation when the request provides product ids and quantities.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="release_stock",
        keywords=("release stock", "nhả hàng", "nha hang", "hoàn tồn kho", "hoan ton kho"),
        rationale="Use stock release when the request provides product ids and quantities.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="bulk_import_inventory",
        keywords=("bulk import inventory", "import tồn kho", "nhập hàng loạt"),
        rationale="Use bulk inventory import when the request provides multiple stock updates.",
        heuristic_enabled=False,
    ),
    ToolDescriptor(
        tool_name="update_warehouse_info",
        keywords=("update warehouse", "cập nhật kho", "cap nhat kho"),
        rationale="Use warehouse update when the request provides product id, location, and reorder level.",
        heuristic_enabled=False,
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
