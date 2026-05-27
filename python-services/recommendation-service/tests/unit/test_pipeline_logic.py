import pytest

from app.training.pipeline import TrainingPipeline
from app.training.pipeline import TrainingSettings
from app.training.pipeline import _normalize_score_map


class FakeMongoRepo:
    async def list_active_products(self):
        return []

    async def list_interactions(self):
        return []

    async def get_interaction_data_window(self):
        return {"start": None, "end": None, "interactionCount": 0}

    async def upsert_model_meta(self, _meta):
        return None

    async def list_user_seen_products(self, user_id: str):
        if user_id == "user-has-history":
            return {"p-seen"}
        return set()

    async def get_top_popular_products(self, _top_k: int):
        return [
            {"productId": "p-popular-1", "score": 12.0},
            {"productId": "p-popular-2", "score": 8.0},
        ]


class FakeRedisRepo:
    def __init__(self):
        self.store = {}

    async def get_json(self, key: str):
        return self.store.get(key)

    async def set_json(self, key: str, value, ttl_seconds: int):
        assert ttl_seconds > 0
        self.store[key] = value


class FakeCF:
    def __init__(self, scores=None):
        self._scores = scores or {}

    def recommend_for_user(self, _user_id: str, top_k: int, seen_items: set[str] | None = None):
        seen = seen_items or set()
        results = {k: v for k, v in self._scores.items() if k not in seen}
        return dict(list(results.items())[:top_k])


class FakeCB:
    def __init__(self, scores=None):
        self._scores = scores or {}

    def score_for_user_profile(self, _seen):
        return self._scores


@pytest.mark.parametrize(
    "scores,expected",
    [
        ({}, {}),
        ({"a": 5.0}, {"a": 1.0}),
        ({"a": 10.0, "b": 20.0}, {"a": 0.0, "b": 1.0}),
    ],
)
def test_normalize_score_map(scores, expected):
    assert _normalize_score_map(scores) == expected


@pytest.mark.asyncio
async def test_recommend_for_user_hybrid_blending_with_seen_filtering():
    pipeline = TrainingPipeline(
        mongo_repo=FakeMongoRepo(),
        redis_repo=FakeRedisRepo(),
        settings=TrainingSettings(alpha=0.6, model_refresh_seconds=3600, cache_ttl_seconds=30, models_dir="models"),
    )

    pipeline._active_products = {"p-a", "p-b", "p-c", "p-d"}
    pipeline._cf = FakeCF({"p-a": 10.0, "p-b": 4.0, "p-seen": 20.0})
    pipeline._cb = FakeCB({"p-a": 0.1, "p-c": 0.8, "p-d": 0.2, "p-seen": 0.9})

    async def _noop_refresh():
        return None

    pipeline._ensure_fresh_models = _noop_refresh

    response = await pipeline.recommend_for_user("user-has-history", top_k=3)

    assert response["source"] == "hybrid"
    product_ids = [item["productId"] for item in response["recommendations"]]
    assert "p-seen" not in product_ids
    assert product_ids[0] == "p-a"


@pytest.mark.asyncio
async def test_recommend_for_user_popular_fallback_when_cf_and_cb_empty():
    pipeline = TrainingPipeline(
        mongo_repo=FakeMongoRepo(),
        redis_repo=FakeRedisRepo(),
        settings=TrainingSettings(alpha=0.6, model_refresh_seconds=3600, cache_ttl_seconds=30, models_dir="models"),
    )

    pipeline._active_products = {"p-popular-1", "p-popular-2"}
    pipeline._cf = FakeCF({})
    pipeline._cb = FakeCB({})

    async def _noop_refresh():
        return None

    pipeline._ensure_fresh_models = _noop_refresh

    response = await pipeline.recommend_for_user("new-user", top_k=2)

    assert response["source"] == "popular-fallback"
    assert len(response["recommendations"]) == 2
    assert response["recommendations"][0]["productId"] == "p-popular-1"
