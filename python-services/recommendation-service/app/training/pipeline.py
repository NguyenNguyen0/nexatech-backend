from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

import joblib

from app.repositories.mongo_repo import MongoRepository
from app.repositories.redis_repo import RedisRepository
from app.training.collaborative import CollaborativeFilter
from app.training.content_based import ContentBasedFilter


def _normalize_score_map(scores: dict[str, float]) -> dict[str, float]:
    if not scores:
        return {}
    values = list(scores.values())
    min_value = min(values)
    max_value = max(values)
    if max_value == min_value:
        return {key: 1.0 for key in scores}
    return {key: (value - min_value) / (max_value - min_value) for key, value in scores.items()}


@dataclass
class TrainingSettings:
    alpha: float = 0.6
    model_refresh_seconds: int = 120
    cache_ttl_seconds: int = 60
    models_dir: str = "models"


class TrainingPipeline:
    def __init__(
        self,
        mongo_repo: MongoRepository,
        redis_repo: RedisRepository,
        settings: TrainingSettings,
    ) -> None:
        self._mongo_repo = mongo_repo
        self._redis_repo = redis_repo
        self._settings = settings
        self._cb = ContentBasedFilter()
        self._cf = CollaborativeFilter()
        self._active_products: set[str] = set()
        self._last_trained_at = 0.0
        self._train_lock = asyncio.Lock()
        self._current_model_meta: dict | None = None
        self._models_dir = Path(self._settings.models_dir)
        self._models_dir.mkdir(parents=True, exist_ok=True)

    async def _ensure_fresh_models(self) -> None:
        now = time.time()
        if now - self._last_trained_at < self._settings.model_refresh_seconds:
            return

        async with self._train_lock:
            now = time.time()
            if now - self._last_trained_at < self._settings.model_refresh_seconds:
                return
            await self.run()

    async def run(self, trigger: str = "manual") -> dict:
        products = await self._mongo_repo.list_active_products()
        interactions = await self._mongo_repo.list_interactions()
        data_window = await self._mongo_repo.get_interaction_data_window()

        next_cb = ContentBasedFilter()
        next_cf = CollaborativeFilter()

        next_cb.train(products)
        next_cf.train(interactions)
        next_active_products = {str(product.get("_id")) for product in products if product.get("_id")}

        model_version = datetime.now(UTC).strftime("model-%Y%m%d%H%M%S")
        trained_at = datetime.now(UTC).isoformat()

        model_meta = {
            "version": model_version,
            "trainedAt": trained_at,
            "trigger": trigger,
            "dataWindow": data_window,
            "stats": {
                "activeProductCount": len(next_active_products),
                "interactionCount": len(interactions),
            },
            "algorithm": {
                "type": "hybrid",
                "alpha": self._settings.alpha,
                "contentBasedReady": next_cb.is_ready,
                "collaborativeReady": next_cf.is_ready,
            },
        }

        artifact_path = self._persist_artifacts(
            model_version=model_version,
            model_meta=model_meta,
            content_based=next_cb,
            collaborative=next_cf,
            active_products=next_active_products,
        )
        model_meta["artifactPath"] = str(artifact_path)

        self._cb = next_cb
        self._cf = next_cf
        self._active_products = next_active_products
        self._current_model_meta = model_meta
        self._last_trained_at = time.time()

        await self._mongo_repo.upsert_model_meta(model_meta)
        return model_meta

    async def run_scheduled_training(self) -> dict:
        async with self._train_lock:
            return await self.run(trigger="scheduled")

    def _persist_artifacts(
        self,
        model_version: str,
        model_meta: dict,
        content_based: ContentBasedFilter,
        collaborative: CollaborativeFilter,
        active_products: set[str],
    ) -> Path:
        artifact = {
            "version": model_version,
            "meta": model_meta,
            "activeProducts": sorted(active_products),
            "contentBased": content_based.export_state(),
            "collaborative": collaborative.export_state(),
        }
        artifact_path = self._models_dir / f"{model_version}.joblib"
        joblib.dump(artifact, artifact_path)
        return artifact_path

    async def recommend_for_user(self, user_id: str, top_k: int) -> dict:
        cache_key = f"rec:user:{user_id}:{top_k}"
        cached = await self._redis_repo.get_json(cache_key)
        if cached is not None:
            return cached

        await self._ensure_fresh_models()

        seen = await self._mongo_repo.list_user_seen_products(user_id)
        cf_raw = self._cf.recommend_for_user(user_id, top_k=top_k * 3, seen_items=seen)
        cb_raw = self._cb.score_for_user_profile(seen)

        cf_normalized = _normalize_score_map(cf_raw)
        cb_normalized = _normalize_score_map(cb_raw)

        if cf_normalized:
            merged_ids = set(cf_normalized) | set(cb_normalized)
            blended = {
                product_id: (self._settings.alpha * cf_normalized.get(product_id, 0.0))
                + ((1.0 - self._settings.alpha) * cb_normalized.get(product_id, 0.0))
                for product_id in merged_ids
            }
            source = "hybrid"
        elif cb_normalized:
            blended = cb_normalized
            source = "content-based-fallback"
        else:
            popular = await self.popular_products(top_k)
            response = {
                "userId": user_id,
                "topK": top_k,
                "source": "popular-fallback",
                "recommendations": popular["popular"],
            }
            await self._redis_repo.set_json(cache_key, response, ttl_seconds=self._settings.cache_ttl_seconds)
            return response

        ranked = [
            {"productId": product_id, "score": round(score, 6)}
            for product_id, score in sorted(blended.items(), key=lambda item: item[1], reverse=True)
            if product_id not in seen and product_id in self._active_products and score > 0
        ][:top_k]

        response = {
            "userId": user_id,
            "topK": top_k,
            "source": source,
            "recommendations": ranked,
        }
        await self._redis_repo.set_json(cache_key, response, ttl_seconds=self._settings.cache_ttl_seconds)
        return response

    async def similar_products(self, product_id: str, top_k: int) -> dict:
        cache_key = f"rec:similar:{product_id}:{top_k}"
        cached = await self._redis_repo.get_json(cache_key)
        if cached is not None:
            return cached

        await self._ensure_fresh_models()

        cf_scores = self._cf.similar_items(product_id, top_k=top_k * 3)
        cb_scores = dict(self._cb.similar_products(product_id, top_k=top_k * 3))

        cf_normalized = _normalize_score_map(cf_scores)
        cb_normalized = _normalize_score_map(cb_scores)

        merged_ids = set(cf_normalized) | set(cb_normalized)
        blended = {
            candidate_id: (self._settings.alpha * cf_normalized.get(candidate_id, 0.0))
            + ((1.0 - self._settings.alpha) * cb_normalized.get(candidate_id, 0.0))
            for candidate_id in merged_ids
        }

        similar = [
            {"productId": candidate_id, "score": round(score, 6)}
            for candidate_id, score in sorted(blended.items(), key=lambda item: item[1], reverse=True)
            if candidate_id in self._active_products and score > 0 and candidate_id != product_id
        ][:top_k]

        response = {
            "productId": product_id,
            "topK": top_k,
            "source": "hybrid" if cf_normalized else "content-based",
            "similar": similar,
        }
        await self._redis_repo.set_json(cache_key, response, ttl_seconds=self._settings.cache_ttl_seconds)
        return response

    async def popular_products(self, top_k: int) -> dict:
        cache_key = f"rec:popular:{top_k}"
        cached = await self._redis_repo.get_json(cache_key)
        if cached is not None:
            return cached

        await self._ensure_fresh_models()

        popular_rows = await self._mongo_repo.get_top_popular_products(top_k * 3)
        popular = [
            {"productId": row["productId"], "score": round(float(row["score"]), 6)}
            for row in popular_rows
            if row["productId"] in self._active_products
        ][:top_k]

        response = {
            "topK": top_k,
            "source": "popular",
            "popular": popular,
        }
        await self._redis_repo.set_json(cache_key, response, ttl_seconds=self._settings.cache_ttl_seconds)
        return response
