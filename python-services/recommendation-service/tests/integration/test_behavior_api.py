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
        self.fail_ids = set()

    async def send_behavior(self, event: dict):
        if event.get("eventId") in self.fail_ids:
            raise RuntimeError("producer failed")
        self.events.append(event)


def _build_app() -> FastAPI:
    app = FastAPI()
    app.state.redis_repo = FakeRedisRepo()
    app.state.behavior_producer = FakeBehaviorProducer()

    class Settings:
        behavior_dedup_ttl_seconds = 86400
        behavior_batch_max_size = 100

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


def test_ingest_behavior_batch_returns_summary():
    app = _build_app()
    client = TestClient(app)

    payload = {
        "events": [
            {"eventId": "evt-b1", "productId": "p-1", "eventType": "VIEW"},
            {"eventId": "evt-b2", "productId": "p-2", "eventType": "CART_ADD"},
        ]
    }
    response = client.post("/behaviors/batch", headers={"X-User-Id": "u-1"}, json=payload)

    assert response.status_code == 202
    body = response.json()
    assert body["status"] == "accepted"
    assert body["summary"]["accepted"] == 2
    assert body["summary"]["duplicate"] == 0
    assert body["summary"]["failed"] == 0


def test_ingest_behavior_batch_handles_duplicate_and_failure():
    app = _build_app()
    app.state.behavior_producer.fail_ids.add("evt-fail")
    client = TestClient(app)

    first = client.post(
        "/behaviors",
        headers={"X-User-Id": "u-1"},
        json={"eventId": "evt-dup-batch", "productId": "p-1", "eventType": "VIEW"},
    )
    assert first.status_code == 202

    batch_payload = {
        "events": [
            {"eventId": "evt-dup-batch", "productId": "p-1", "eventType": "VIEW"},
            {"eventId": "evt-fail", "productId": "p-2", "eventType": "VIEW"},
            {"eventId": "evt-ok", "productId": "p-3", "eventType": "VIEW"},
        ]
    }
    response = client.post("/behaviors/batch", headers={"X-User-Id": "u-1"}, json=batch_payload)

    assert response.status_code == 202
    body = response.json()
    assert body["status"] == "partial"
    assert body["summary"]["duplicate"] == 1
    assert body["summary"]["failed"] == 1
    assert body["summary"]["accepted"] == 1


def test_ingest_behavior_batch_rejects_oversize_payload():
    app = _build_app()
    app.state.settings.behavior_batch_max_size = 1
    client = TestClient(app)

    payload = {
        "events": [
            {"eventId": "evt-1", "productId": "p-1", "eventType": "VIEW"},
            {"eventId": "evt-2", "productId": "p-2", "eventType": "VIEW"},
        ]
    }
    response = client.post("/behaviors/batch", headers={"X-User-Id": "u-1"}, json=payload)

    assert response.status_code == 400
