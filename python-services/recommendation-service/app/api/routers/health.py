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
    is_ready = all(v == "ok" for v in checks.values())
    return {
        "ready": is_ready,
        "checks": checks,
    }
