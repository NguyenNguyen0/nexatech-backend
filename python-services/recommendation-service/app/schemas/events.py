from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any


DEFAULT_BEHAVIOR_WEIGHTS = {
    "VIEW": 1.0,
    "SEARCH_CLICK": 1.5,
    "CART_ADD": 3.0,
    "PURCHASE": 5.0,
    "RATE": 3.0,
}


def _to_utc_datetime(value: str | None) -> datetime:
    if not value:
        return datetime.now(UTC)
    normalized = value.replace("Z", "+00:00")
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def _to_float(value: Any, default: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


@dataclass
class UserBehaviorEvent:
    event_id: str
    user_id: str
    product_id: str
    event_type: str
    weight: float
    timestamp: datetime
    category_ids: list[str]
    brand_id: str | None
    price: float | None

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> UserBehaviorEvent:
        user_id = str(payload.get("userId") or payload.get("user_id") or "").strip()
        product_id = str(payload.get("productId") or payload.get("product_id") or "").strip()
        event_type = str(payload.get("eventType") or payload.get("event_type") or "").strip().upper()

        if not user_id or not product_id or not event_type:
            raise ValueError("Invalid behavior event payload")

        rating = _to_float(payload.get("rating"), default=0.0)
        explicit_weight = payload.get("weight")
        if explicit_weight is not None:
            weight = _to_float(explicit_weight, default=0.0)
        elif event_type == "RATE" and rating > 0:
            weight = max(1.0, min(5.0, rating))
        else:
            weight = DEFAULT_BEHAVIOR_WEIGHTS.get(event_type, 0.0)

        if weight <= 0:
            raise ValueError("Behavior event weight must be positive")

        event_id = str(payload.get("eventId") or payload.get("event_id") or "").strip()
        if not event_id:
            fingerprint_data = {
                "userId": user_id,
                "productId": product_id,
                "eventType": event_type,
                "timestamp": payload.get("timestamp"),
                "weight": weight,
            }
            event_id = hashlib.sha256(json.dumps(fingerprint_data, sort_keys=True).encode("utf-8")).hexdigest()

        raw_category_ids = payload.get("categoryIds") or payload.get("category_ids") or []
        category_ids = [str(item) for item in raw_category_ids if item is not None]

        brand_id_raw = payload.get("brandId") or payload.get("brand_id")
        brand_id = str(brand_id_raw) if brand_id_raw is not None else None

        price_raw = payload.get("price")
        price = _to_float(price_raw, default=0.0) if price_raw is not None else None

        return cls(
            event_id=event_id,
            user_id=user_id,
            product_id=product_id,
            event_type=event_type,
            weight=weight,
            timestamp=_to_utc_datetime(payload.get("timestamp")),
            category_ids=category_ids,
            brand_id=brand_id,
            price=price,
        )
