from __future__ import annotations

import re
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any

from app.graph.tool_registry import ToolDescriptor, ToolPlan


_ID_VALUE = r"([a-zA-Z0-9][a-zA-Z0-9_-]{1,})"


@dataclass(frozen=True)
class KeywordToolMatcher:
    descriptors: Sequence[ToolDescriptor]

    def match(
        self,
        text: str,
        available_tools: list[str] | tuple[str, ...] | set[str] | None = None,
    ) -> ToolPlan | None:
        normalized_text = _normalize(text)
        if not normalized_text:
            return None

        available = _normalize_available_tools(available_tools)
        if _mentions_unavailable_tool(normalized_text, self.descriptors, available):
            return None
        direct_plan = self._plan_direct_tool_invocation(normalized_text, available)
        if direct_plan is not None:
            return direct_plan

        structured_plan = (
            self._plan_meta_request(normalized_text, available)
            or self._plan_payment_request(normalized_text, available)
            or self._plan_order_request(normalized_text, available)
            or self._plan_inventory_request(normalized_text, available)
            or self._plan_product_request(normalized_text, available)
            or self._plan_review_request(normalized_text, available)
        )
        if structured_plan is not None:
            return structured_plan

        for descriptor in self.descriptors:
            if not descriptor.heuristic_enabled:
                continue
            if not _is_available(descriptor.tool_name, available):
                continue
            if any(keyword in normalized_text for keyword in descriptor.keywords):
                return ToolPlan(
                    tool_name=descriptor.tool_name,
                    tool_args=dict(descriptor.tool_args),
                    required_role=descriptor.required_role,
                    rationale=descriptor.rationale,
                )

        return None

    def _descriptor(self, tool_name: str) -> ToolDescriptor | None:
        return next(
            (descriptor for descriptor in self.descriptors if descriptor.tool_name == tool_name),
            None,
        )

    def _plan(
        self,
        tool_name: str,
        tool_args: dict[str, Any],
        rationale: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        if not _is_available(tool_name, available):
            return None
        descriptor = self._descriptor(tool_name)
        if descriptor is None:
            return None
        return ToolPlan(
            tool_name=tool_name,
            tool_args=tool_args,
            required_role=descriptor.required_role,
            rationale=rationale,
        )

    def _plan_direct_tool_invocation(
        self,
        normalized_text: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        all_tool_names = {descriptor.tool_name for descriptor in self.descriptors}
        matched_descriptor: ToolDescriptor | None = None
        for descriptor in self.descriptors:
            if not _is_available(descriptor.tool_name, available):
                continue
            if _contains_tool_name(normalized_text, descriptor.tool_name):
                matched_descriptor = descriptor
                break

        if matched_descriptor is None:
            return None

        tool_name = matched_descriptor.tool_name
        tool_args: dict[str, Any] = dict(matched_descriptor.tool_args)
        tool_args.update(_extract_arguments_for_tool(normalized_text, tool_name, all_tool_names))

        return ToolPlan(
            tool_name=tool_name,
            tool_args=tool_args,
            required_role=matched_descriptor.required_role,
            rationale=f"direct tool invocation: {tool_name}",
        )

    def _plan_meta_request(
        self,
        normalized_text: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        if "skill" in normalized_text or "kỹ năng" in normalized_text or "ky nang" in normalized_text:
            domain = _extract_domain(normalized_text)
            skill_name = _extract_skill_name(normalized_text)
            if domain and skill_name == domain:
                skill_name = _extract_skill_name_after_domain(normalized_text, domain)
            if domain:
                args: dict[str, Any] = {"domain": domain}
                if skill_name:
                    args["skill_name"] = skill_name
                return self._plan("get_skill", args, "skill lookup", available)
            return self._plan("list_skills", {}, "skill listing", available)

        if any(term in normalized_text for term in ("mcp domain", "mcp domains", "mcp nào", "mcp nao")):
            domain = _extract_domain(normalized_text)
            if domain:
                return self._plan(
                    "get_mcp_domain",
                    {"domain": domain},
                    "mcp domain lookup",
                    available,
                )
            return self._plan("list_mcp_domains", {}, "mcp domain listing", available)

        if any(term in normalized_text for term in ("route", "routing", "định tuyến", "dinh tuyen")):
            return self._plan(
                "route_to_mcp_domain",
                {"intent": normalized_text},
                "mcp domain routing",
                available,
            )

        return None

    def _plan_payment_request(
        self,
        normalized_text: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        payment_id = _extract_id(
            normalized_text,
            (
                "payment id",
                "mã thanh toán",
                "ma thanh toan",
                "payment",
                "thanh toán",
                "thanh toan",
            ),
        )
        if payment_id and any(term in normalized_text for term in ("refund", "hoàn tiền", "hoan tien")):
            return self._plan(
                "refund_payment",
                {"payment_id": payment_id},
                "refund payment by id",
                available,
            )
        if payment_id:
            return self._plan(
                "get_payment",
                {"payment_id": payment_id},
                "payment detail by id",
                available,
            )

        order_id = _extract_id(
            normalized_text,
            (
                "order id",
                "mã đơn hàng",
                "ma don hang",
                "đơn hàng",
                "don hang",
                "order",
            ),
        )
        if order_id and any(term in normalized_text for term in ("payment", "thanh toán", "thanh toan")):
            return self._plan(
                "get_payment_by_order",
                {"order_id": order_id},
                "payment lookup by order id",
                available,
            )
        return None

    def _plan_order_request(
        self,
        normalized_text: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        order_id = _extract_id(
            normalized_text,
            (
                "order id",
                "mã đơn hàng",
                "ma don hang",
                "đơn hàng",
                "don hang",
                "order",
            ),
        )
        if not order_id:
            return None

        if any(term in normalized_text for term in ("cancel", "hủy", "huy")):
            return self._plan(
                "cancel_order",
                {"order_id": order_id},
                "order cancellation by id",
                available,
            )
        if any(term in normalized_text for term in ("continue payment", "tiếp tục thanh toán", "tiep tuc thanh toan")):
            return self._plan(
                "continue_order_payment",
                {"order_id": order_id},
                "continue order payment by id",
                available,
            )
        if any(term in normalized_text for term in ("trace", "truy vết", "truy vet")):
            return self._plan(
                "get_order_trace",
                {"correlation_id": order_id},
                "order trace lookup",
                available,
            )
        return self._plan(
            "get_order",
            {"order_id": order_id},
            "order detail by id",
            available,
        )

    def _plan_inventory_request(
        self,
        normalized_text: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        product_id = _extract_id(
            normalized_text,
            (
                "product id",
                "mã sản phẩm",
                "ma san pham",
                "sản phẩm",
                "san pham",
                "product",
            ),
        )
        if not product_id:
            return None

        if any(term in normalized_text for term in ("movement", "movements", "lịch sử kho", "lich su kho")):
            return self._plan(
                "get_inventory_movements",
                {"product_id": product_id, "params": {}},
                "inventory movement lookup by product id",
                available,
            )
        if any(term in normalized_text for term in ("inventory", "stock", "tồn kho", "ton kho", "kho")):
            return self._plan(
                "get_inventory",
                {"product_id": product_id},
                "inventory lookup by product id",
                available,
            )
        return None

    def _plan_product_request(
        self,
        normalized_text: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        has_product_intent = any(
            term in normalized_text
            for term in ("product", "products", "sản phẩm", "san pham")
        )
        if not has_product_intent:
            return None

        category_slug = _extract_slug_after_any(
            normalized_text,
            (
                "category slug",
                "danh mục",
                "danh muc",
                "category",
            ),
        )
        if category_slug:
            return self._plan(
                "list_products_by_category_slug",
                {"slug": category_slug},
                "category product listing by slug",
                available,
            )

        category_id = _extract_id(
            normalized_text,
            (
                "category id",
                "mã danh mục",
                "ma danh muc",
            ),
        )
        if category_id:
            return self._plan(
                "list_products_by_category",
                {"category_id": category_id, "params": {}},
                "category product listing by id",
                available,
            )

        product_slug = _extract_slug_after_any(
            normalized_text,
            (
                "product slug",
                "slug sản phẩm",
                "slug san pham",
            ),
        )
        if product_slug:
            return self._plan(
                "get_product_by_slug",
                {"slug": product_slug},
                "product detail by slug",
                available,
            )

        product_id = _extract_id(
            normalized_text,
            (
                "product id",
                "mã sản phẩm",
                "ma san pham",
            ),
        )
        if product_id:
            return self._plan(
                "get_product",
                {"product_id": product_id},
                "product detail by id",
                available,
            )

        search_keyword = _extract_slug_after_any(
            normalized_text,
            (
                "tìm sản phẩm",
                "tim san pham",
                "search products",
                "search product",
            ),
        )
        if search_keyword:
            return self._plan(
                "search_products",
                {"keyword": search_keyword, "params": {}},
                "product keyword search",
                available,
            )

        return None

    def _plan_review_request(
        self,
        normalized_text: str,
        available: set[str] | None,
    ) -> ToolPlan | None:
        has_review_intent = any(
            term in normalized_text
            for term in ("review", "reviews", "đánh giá", "danh gia")
        )
        if not has_review_intent:
            return None

        product_slug = _extract_slug_after_any(
            normalized_text,
            (
                "product slug",
                "slug sản phẩm",
                "slug san pham",
            ),
        )
        if product_slug:
            return self._plan(
                "list_reviews_by_product_slug",
                {"slug": product_slug},
                "product reviews by slug",
                available,
            )

        product_id = _extract_id(
            normalized_text,
            (
                "product id",
                "mã sản phẩm",
                "ma san pham",
                "sản phẩm",
                "san pham",
                "product",
            ),
        )
        if product_id:
            return self._plan(
                "list_reviews_by_product",
                {"product_id": product_id, "params": {}},
                "product reviews by product id",
                available,
            )

        return None


def _normalize_available_tools(
    available_tools: list[str] | tuple[str, ...] | set[str] | None,
) -> set[str] | None:
    if available_tools is None:
        return None
    return {tool for tool in available_tools if tool}


def _is_available(tool_name: str, available: set[str] | None) -> bool:
    return available is None or tool_name in available


def _contains_tool_name(text: str, tool_name: str) -> bool:
    return bool(
        re.search(
            rf"(?<![a-zA-Z0-9_]){re.escape(tool_name)}(?![a-zA-Z0-9_])",
            text,
        )
    )


def _mentions_unavailable_tool(
    text: str,
    descriptors: Sequence[ToolDescriptor],
    available: set[str] | None,
) -> bool:
    if available is None:
        return False
    return any(
        _contains_tool_name(text, descriptor.tool_name)
        and descriptor.tool_name not in available
        for descriptor in descriptors
    )


def _normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text.casefold()).strip()


def _extract_slug_after_any(text: str, markers: tuple[str, ...]) -> str | None:
    for marker in markers:
        pattern = rf"{re.escape(marker)}\s+{_ID_VALUE}"
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            return match.group(1)
    return None


def _extract_id(text: str, markers: tuple[str, ...]) -> str | None:
    return _extract_slug_after_any(text, markers)


def _extract_domain(text: str) -> str | None:
    domains = (
        "inventory",
        "product",
        "order",
        "payment",
        "promotion",
        "news",
        "audit",
        "orchestrator",
        "shared",
    )
    for domain in domains:
        if re.search(rf"\b{re.escape(domain)}\b", text):
            return domain
    vietnamese_domains = {
        "kho": "inventory",
        "sản phẩm": "product",
        "san pham": "product",
        "đơn hàng": "order",
        "don hang": "order",
        "thanh toán": "payment",
        "thanh toan": "payment",
        "khuyến mãi": "promotion",
        "khuyen mai": "promotion",
        "tin tức": "news",
        "tin tuc": "news",
        "nhật ký": "audit",
        "nhat ky": "audit",
    }
    for keyword, domain in vietnamese_domains.items():
        if keyword in text:
            return domain
    return None


def _extract_skill_name(text: str) -> str | None:
    match = re.search(rf"(?:skill|kỹ năng|ky nang)\s+{_ID_VALUE}", text)
    if not match:
        return None
    candidate = match.group(1)
    if candidate in {"skill", "kỹ", "ky", "nang", "năng"}:
        return None
    return candidate


def _extract_skill_name_after_domain(text: str, domain: str) -> str | None:
    match = re.search(rf"\b{re.escape(domain)}\b\s+{_ID_VALUE}", text)
    if not match:
        return None
    candidate = match.group(1)
    if candidate in {"skill", "index"}:
        return None
    return candidate


def _extract_arguments_for_tool(
    normalized_text: str,
    tool_name: str,
    all_tool_names: set[str],
) -> dict[str, Any]:
    args: dict[str, Any] = {}

    slug_match = re.search(
        rf"(?:danh\s*mục|danh\s*muc|category|slug|mục|muc|cho|of|in)\s+{_ID_VALUE}",
        normalized_text,
        flags=re.IGNORECASE,
    )
    candidate = slug_match.group(1) if slug_match else None
    if candidate:
        stop_words = all_tool_names | {"tool", "slug", "category", "danh", "muc", "mục"}
        if candidate not in stop_words:
            if "slug" in tool_name or "category" in tool_name:
                args["slug"] = candidate
            elif "payment" in tool_name:
                args["payment_id"] = candidate
            elif "order" in tool_name:
                args["order_id"] = candidate
            elif "product" in tool_name or "inventory" in tool_name:
                args["product_id"] = candidate

    for key, value in re.findall(
        r"\b(order_id|payment_id|product_id|category_id|domain|skill_name|slug)\s*[:=]\s*"
        + _ID_VALUE,
        normalized_text,
    ):
        normalized_key = {
            "order_id": "order_id",
            "payment_id": "payment_id",
            "product_id": "product_id",
            "category_id": "category_id",
            "domain": "domain",
            "skill_name": "skill_name",
            "slug": "slug",
        }[key]
        args[normalized_key] = value

    if tool_name == "get_skill" and "domain" not in args:
        domain = _extract_domain(normalized_text)
        if domain:
            args["domain"] = domain
    if tool_name == "route_to_mcp_domain" and "intent" not in args:
        args["intent"] = normalized_text

    return args
