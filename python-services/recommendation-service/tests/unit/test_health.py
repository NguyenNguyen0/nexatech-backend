from fastapi.testclient import TestClient
from fastapi import FastAPI

from app.api.routers.health import router as health_router


def _build_test_app() -> FastAPI:
    app = FastAPI()
    app.state.settings = type(
        "Settings",
        (),
        {"service_name": "recommendation-service", "service_version": "0.1.0"},
    )()
    app.state.readiness_checks = {
        "mongo": "ok",
        "redis": "ok",
        "kafka": "ok",
        "model_store": "not_configured",
    }
    app.include_router(health_router)
    return app


def test_health_endpoint() -> None:
    client = TestClient(_build_test_app())
    response = client.get("/health")
    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "UP"
