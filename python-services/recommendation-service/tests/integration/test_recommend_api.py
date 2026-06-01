from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.routers.recommend import router as recommend_router


class FakeTrainingPipeline:
    async def recommend_for_user(self, user_id: str, top_k: int):
        return {
            "userId": user_id,
            "topK": top_k,
            "source": "hybrid",
            "recommendations": [
                {"productId": "p1", "score": 0.9},
                {"productId": "p2", "score": 0.7},
            ],
        }

    async def similar_products(self, product_id: str, top_k: int):
        return {
            "productId": product_id,
            "topK": top_k,
            "source": "content-based",
            "similar": [
                {"productId": "p10", "score": 0.8},
                {"productId": "p11", "score": 0.6},
            ],
        }

    async def popular_products(self, top_k: int):
        return {
            "topK": top_k,
            "source": "popular",
            "popular": [
                {"productId": "p100", "score": 12.0},
                {"productId": "p101", "score": 10.0},
            ],
        }


class FailingUserRecommendationPipeline(FakeTrainingPipeline):
    async def recommend_for_user(self, user_id: str, top_k: int):
        raise RuntimeError("model failed")


class FakeRecommendationEnricher:
    async def enrich_ranked_items(self, items):
        return [
            {
                **item,
                "product": {
                    "id": item["productId"],
                    "name": f"Product {item['productId']}",
                    "slug": f"slug-{item['productId']}",
                },
            }
            for item in items
        ]


def _build_test_app() -> FastAPI:
    app = FastAPI()
    app.state.training_pipeline = FakeTrainingPipeline()
    app.state.recommendation_enricher = FakeRecommendationEnricher()
    app.include_router(recommend_router)
    return app


def _build_test_app_with_pipeline(pipeline) -> FastAPI:
    app = FastAPI()
    app.state.training_pipeline = pipeline
    app.state.recommendation_enricher = FakeRecommendationEnricher()
    app.include_router(recommend_router)
    return app


def test_recommend_user_endpoint_returns_ranked_payload():
    client = TestClient(_build_test_app())
    response = client.get("/recommend/user/u-1?top_k=2")
    assert response.status_code == 200
    payload = response.json()
    assert payload["userId"] == "u-1"
    assert payload["source"] == "hybrid"
    assert len(payload["recommendations"]) == 2
    assert payload["recommendations"][0]["product"]["id"] == "p1"


def test_recommend_user_endpoint_falls_back_to_popular_when_user_pipeline_fails():
    client = TestClient(_build_test_app_with_pipeline(FailingUserRecommendationPipeline()))
    response = client.get("/recommend/user/u-1?top_k=2")
    assert response.status_code == 200
    payload = response.json()
    assert payload["userId"] == "u-1"
    assert payload["source"] == "popular-fallback"
    assert len(payload["recommendations"]) == 2
    assert payload["recommendations"][0]["product"]["id"] == "p100"


def test_recommend_similar_endpoint_returns_ranked_payload():
    client = TestClient(_build_test_app())
    response = client.get("/recommend/similar/p-root?top_k=2")
    assert response.status_code == 200
    payload = response.json()
    assert payload["productId"] == "p-root"
    assert payload["source"] == "content-based"
    assert len(payload["similar"]) == 2
    assert payload["similar"][0]["product"]["id"] == "p10"


def test_recommend_popular_endpoint_returns_ranked_payload():
    client = TestClient(_build_test_app())
    response = client.get("/recommend/popular?top_k=2")
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "popular"
    assert len(payload["popular"]) == 2
    assert payload["popular"][0]["product"]["id"] == "p100"
