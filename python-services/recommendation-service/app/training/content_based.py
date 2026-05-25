from __future__ import annotations

from collections.abc import Iterable

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from app.features.builders import build_content_text


class ContentBasedFilter:
    def __init__(self) -> None:
        self._vectorizer = TfidfVectorizer(max_features=5000, ngram_range=(1, 2), min_df=1)
        self._product_ids: list[str] = []
        self._product_index: dict[str, int] = {}
        self._matrix = None
        self._trained = False

    @property
    def is_ready(self) -> bool:
        return self._trained and self._matrix is not None and len(self._product_ids) > 0

    def train(self, products: list[dict]) -> None:
        active_products = [p for p in products if bool(p.get("active", True))]
        if not active_products:
            self._trained = False
            self._product_ids = []
            self._product_index = {}
            self._matrix = None
            return

        self._product_ids = [str(p.get("_id") or p.get("id") or p.get("productId")) for p in active_products]
        self._product_index = {pid: idx for idx, pid in enumerate(self._product_ids)}
        corpus = [build_content_text(product) or "unknown product" for product in active_products]

        self._matrix = self._vectorizer.fit_transform(corpus)
        self._trained = True

    def export_state(self) -> dict:
        return {
            "product_ids": list(self._product_ids),
            "product_index": dict(self._product_index),
            "matrix": self._matrix,
            "vectorizer": self._vectorizer,
            "trained": self._trained,
        }

    def import_state(self, state: dict) -> None:
        self._product_ids = list(state.get("product_ids") or [])
        self._product_index = dict(state.get("product_index") or {})
        self._matrix = state.get("matrix")
        self._vectorizer = state.get("vectorizer") or self._vectorizer
        self._trained = bool(state.get("trained"))

    def similar_products(self, product_id: str, top_k: int = 10) -> list[tuple[str, float]]:
        if not self.is_ready:
            return []

        idx = self._product_index.get(product_id)
        if idx is None:
            return []

        query_vector = self._matrix[idx]
        scores = cosine_similarity(query_vector, self._matrix).ravel()
        scores[idx] = -1.0
        top_indices = np.argsort(scores)[::-1][:top_k]

        return [
            (self._product_ids[i], float(scores[i]))
            for i in top_indices
            if scores[i] > 0
        ]

    def score_for_user_profile(self, interacted_product_ids: Iterable[str]) -> dict[str, float]:
        if not self.is_ready:
            return {}

        indices = [self._product_index[pid] for pid in interacted_product_ids if pid in self._product_index]
        if not indices:
            return {}

        profile_vector = np.asarray(self._matrix[indices].mean(axis=0))
        scores = cosine_similarity(profile_vector, self._matrix).ravel()

        for index in indices:
            scores[index] = -1.0

        return {
            self._product_ids[i]: float(score)
            for i, score in enumerate(scores)
            if score > 0
        }
