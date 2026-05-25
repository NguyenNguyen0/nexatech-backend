from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request, status

from app.schemas.behavior import BehaviorEventRequest


router = APIRouter(prefix="/behaviors", tags=["behavior"])


@router.post("", status_code=status.HTTP_202_ACCEPTED)
async def ingest_behavior(request: Request, payload: BehaviorEventRequest) -> dict:
    user_id = (request.headers.get("X-User-Id") or "").strip()
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing X-User-Id")

    redis_repo = getattr(request.app.state, "redis_repo", None)
    if redis_repo is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Redis unavailable")

    behavior_producer = getattr(request.app.state, "behavior_producer", None)
    if behavior_producer is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Behavior producer unavailable")

    dedup_ttl = int(getattr(request.app.state.settings, "behavior_dedup_ttl_seconds", 24 * 60 * 60))
    dedup_key = f"behavior:dedup:{payload.event_id}"
    inserted = await redis_repo.client.set(dedup_key, "1", ex=max(dedup_ttl, 1), nx=True)
    if not inserted:
        return {"status": "duplicate", "eventId": payload.event_id}

    event = {
        "eventId": payload.event_id,
        "userId": user_id,
        "productId": payload.product_id,
        "eventType": payload.event_type.value,
        "weight": payload.resolve_weight(),
        "rating": payload.rating or 0,
        "timestamp": payload.timestamp.isoformat(),
        "categoryIds": payload.category_ids,
        "brandId": payload.brand_id,
        "price": payload.price,
    }

    try:
        await behavior_producer.send_behavior(event)
    except Exception:
        await redis_repo.client.delete(dedup_key)
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Kafka unavailable")

    return {"status": "accepted", "eventId": payload.event_id}
