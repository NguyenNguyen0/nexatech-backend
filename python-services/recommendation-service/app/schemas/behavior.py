from __future__ import annotations

from datetime import UTC, datetime
from enum import Enum
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field
from pydantic import field_validator


class EventType(str, Enum):
    VIEW = "VIEW"
    SEARCH_CLICK = "SEARCH_CLICK"
    CART_ADD = "CART_ADD"
    PURCHASE = "PURCHASE"
    RATE = "RATE"


DEFAULT_BEHAVIOR_WEIGHTS = {
    EventType.VIEW: 1.0,
    EventType.SEARCH_CLICK: 1.5,
    EventType.CART_ADD: 3.0,
    EventType.PURCHASE: 5.0,
}


class BehaviorEventRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    event_id: str = Field(default_factory=lambda: str(uuid4()), alias="eventId")
    product_id: str = Field(alias="productId", min_length=1)
    event_type: EventType = Field(alias="eventType")
    rating: float | None = Field(default=None, ge=1, le=5)
    category_ids: list[str] = Field(default_factory=list, alias="categoryIds")
    brand_id: str | None = Field(default=None, alias="brandId")
    price: float | None = None
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))

    @field_validator("timestamp", mode="before")
    @classmethod
    def normalize_timestamp(cls, value):
        if value is None:
            return datetime.now(UTC)
        if isinstance(value, datetime):
            if value.tzinfo is None:
                return value.replace(tzinfo=UTC)
            return value.astimezone(UTC)
        return value

    def resolve_weight(self) -> float:
        if self.event_type == EventType.RATE:
            return max(1.0, min(5.0, self.rating or 1.0))
        return DEFAULT_BEHAVIOR_WEIGHTS[self.event_type]
