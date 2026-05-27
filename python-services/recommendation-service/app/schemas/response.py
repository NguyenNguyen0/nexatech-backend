from dataclasses import dataclass


@dataclass
class RecommendationItem:
    product_id: str
    score: float
