from fastapi import APIRouter, Request

router = APIRouter(tags=["health"])


@router.get("/health")
async def health(request: Request) -> dict:
    settings = request.app.state.settings
    return {
        "status": "UP",
        "service": settings.service_name,
        "version": settings.service_version,
    }


@router.get("/ready")
async def ready(request: Request) -> dict:
    checks = request.app.state.readiness_checks

    mongo_repo = getattr(request.app.state, "mongo_repo", None)
    redis_repo = getattr(request.app.state, "redis_repo", None)
    kafka_client = getattr(request.app.state, "kafka_client", None)

    if mongo_repo is not None:
        try:
            checks["mongo"] = "ok" if await mongo_repo.ping() else "error: ping_failed"
        except Exception as exc:
            checks["mongo"] = f"error: {exc.__class__.__name__}"

    if redis_repo is not None:
        try:
            checks["redis"] = "ok" if await redis_repo.ping() else "error: ping_failed"
        except Exception as exc:
            checks["redis"] = f"error: {exc.__class__.__name__}"

    if kafka_client is not None:
        try:
            checks["kafka"] = "ok" if await kafka_client.ping() else "error: ping_failed"
        except Exception as exc:
            checks["kafka"] = f"error: {exc.__class__.__name__}"

    required_checks = ("mongo", "redis", "kafka")
    is_ready = all(checks.get(name) == "ok" for name in required_checks)
    return {
        "ready": is_ready,
        "requiredChecks": list(required_checks),
        "checks": checks,
    }
