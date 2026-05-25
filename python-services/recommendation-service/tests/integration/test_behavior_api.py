from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.routers.behavior import router as behavior_router


class FakeRedisClient:
    def __init__(self):
        self._store = {}

    async def set(self, key, value, ex=None, nx=False):
        if nx and key in self._store:
            return False
        self._store[key] = value
        return True

    async def delete(self, key):
        self._store.pop(key, None)


class FakeRedisRepo:
    def __init__(self):
        self.client = FakeRedisClient()


class FakeBehaviorProducer:
    def __init__(self):
        self.events = []

    async def send_behavior(self, event: dict):
        self.events.append(event)


def _build_app() -> FastAPI:
    app = FastAPI()
    app.state.redis_repo = FakeRedisRepo()
    app.state.behavior_producer = FakeBehaviorProducer()

    class Settings:
        behavior_dedup_ttl_seconds = 86400

    app.state.settings = Settings()
    app.include_router(behavior_router)
    return app


def test_ingest_behavior_accepts_and_publishes_event():
    app = _build_app()
    client = TestClient(app)

    response = client.post(
        "/behaviors",
        headers={"X-User-Id": "u-1"},
        json={
            "eventId": "evt-1",
            "productId": "p-1",
            "eventType": "VIEW",
            "categoryIds": ["c1"],
            "brandId": "b1",
            "price": 100,
        },
    )

    assert response.status_code == 202
    assert response.json()["status"] == "accepted"
    assert len(app.state.behavior_producer.events) == 1
    assert app.state.behavior_producer.events[0]["userId"] == "u-1"


def test_ingest_behavior_returns_duplicate_for_same_event_id():
    app = _build_app()
    client = TestClient(app)
    payload = {"eventId": "evt-dup", "productId": "p-1", "eventType": "VIEW"}

    first = client.post("/behaviors", headers={"X-User-Id": "u-1"}, json=payload)
    second = client.post("/behaviors", headers={"X-User-Id": "u-1"}, json=payload)

    assert first.status_code == 202
    assert second.status_code == 202
    assert second.json()["status"] == "duplicate"
    assert len(app.state.behavior_producer.events) == 1


def test_ingest_behavior_requires_user_header():
    app = _build_app()
    client = TestClient(app)
    response = client.post("/behaviors", json={"productId": "p-1", "eventType": "VIEW"})

    assert response.status_code == 401
