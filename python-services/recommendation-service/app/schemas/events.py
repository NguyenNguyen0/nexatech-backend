from dataclasses import dataclass


@dataclass
class UserBehaviorEvent:
    user_id: str
    product_id: str
    event_type: str
    weight: float
