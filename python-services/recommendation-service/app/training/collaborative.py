from __future__ import annotations

import logging

import implicit
import numpy as np
from scipy.sparse import csr_matrix


logger = logging.getLogger("recommendation-service.collaborative")


class CollaborativeFilter:
    def __init__(self, factors: int = 64, iterations: int = 20, regularization: float = 0.1) -> None:
        self._model = implicit.als.AlternatingLeastSquares(
            factors=factors,
            iterations=iterations,
            regularization=regularization,
        )
        self._user_index: dict[str, int] = {}
        self._item_index: dict[str, int] = {}
        self._index_user: list[str] = []
        self._index_item: list[str] = []
        self._user_item_matrix = None
        self._ready = False

    @property
    def is_ready(self) -> bool:
        return self._ready

    def train(self, interactions: list[dict]) -> None:
        if not interactions:
            self._ready = False
            return

        users = sorted({str(item.get("userId")) for item in interactions if item.get("userId")})
        items = sorted({str(item.get("productId")) for item in interactions if item.get("productId")})
        if not users or not items:
            self._ready = False
            return

        self._user_index = {user_id: idx for idx, user_id in enumerate(users)}
        self._item_index = {item_id: idx for idx, item_id in enumerate(items)}
        self._index_user = users
        self._index_item = items

        rows: list[int] = []
        cols: list[int] = []
        data: list[float] = []

        for interaction in interactions:
            user_id = str(interaction.get("userId") or "")
            product_id = str(interaction.get("productId") or "")
            score = float(interaction.get("score") or 0)
            if score <= 0 or user_id not in self._user_index or product_id not in self._item_index:
                continue

            rows.append(self._user_index[user_id])
            cols.append(self._item_index[product_id])
            data.append(score)

        if not data:
            self._ready = False
            return

        self._user_item_matrix = csr_matrix((data, (rows, cols)), shape=(len(users), len(items)))

        try:
            self._model.fit(self._user_item_matrix.T)
            self._ready = True
        except Exception as exc:  # pragma: no cover
            logger.warning("Collaborative model training failed: %s", exc)
            self._ready = False

    def recommend_for_user(self, user_id: str, top_k: int, seen_items: set[str] | None = None) -> dict[str, float]:
        if not self._ready or self._user_item_matrix is None:
            return {}

        user_idx = self._user_index.get(user_id)
        if user_idx is None:
            return {}

        seen_items = seen_items or set()
        user_vector = self._user_item_matrix[user_idx]

        item_indices, scores = self._model.recommend(
            user_idx,
            user_vector,
            N=max(top_k * 3, top_k),
            filter_already_liked_items=True,
        )

        recommendations: dict[str, float] = {}
        for item_idx, score in zip(item_indices, scores):
            product_id = self._index_item[int(item_idx)]
            if product_id in seen_items:
                continue
            recommendations[product_id] = float(score)
            if len(recommendations) >= top_k:
                break
        return recommendations

    def similar_items(self, product_id: str, top_k: int) -> dict[str, float]:
        if not self._ready:
            return {}

        item_idx = self._item_index.get(product_id)
        if item_idx is None:
            return {}

        similar_item_indices, scores = self._model.similar_items(item_idx, N=max(top_k + 1, 2))
        similar_map: dict[str, float] = {}
        for idx, score in zip(similar_item_indices, scores):
            idx = int(idx)
            pid = self._index_item[idx]
            if pid == product_id:
                continue
            if score <= 0:
                continue
            similar_map[pid] = float(score)
            if len(similar_map) >= top_k:
                break
        return similar_map
