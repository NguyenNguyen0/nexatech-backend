from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request, status

from app.schemas.behavior import BehaviorBatchRequest
from app.schemas.behavior import BehaviorEventRequest


router = APIRouter(prefix="/behaviors", tags=["behavior"])


def _build_kafka_event(user_id: str, payload: BehaviorEventRequest) -> dict:
    return {
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

    event = _build_kafka_event(user_id, payload)

    try:
        await behavior_producer.send_behavior(event)
    except Exception:
        await redis_repo.client.delete(dedup_key)
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Kafka unavailable")

    return {"status": "accepted", "eventId": payload.event_id}


@router.post("/batch", status_code=status.HTTP_202_ACCEPTED)
async def ingest_behavior_batch(request: Request, payload: BehaviorBatchRequest) -> dict:
    user_id = (request.headers.get("X-User-Id") or "").strip()
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing X-User-Id")

    redis_repo = getattr(request.app.state, "redis_repo", None)
    if redis_repo is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Redis unavailable")

    behavior_producer = getattr(request.app.state, "behavior_producer", None)
    if behavior_producer is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Behavior producer unavailable")

    settings = request.app.state.settings
    max_batch_size = max(int(getattr(settings, "behavior_batch_max_size", 100)), 1)
    if len(payload.events) > max_batch_size:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Batch size exceeds limit={max_batch_size}",
        )

    dedup_ttl = int(getattr(settings, "behavior_dedup_ttl_seconds", 24 * 60 * 60))
    accepted: list[str] = []
    duplicate: list[str] = []
    failed: list[dict] = []

    for event_payload in payload.events:
        dedup_key = f"behavior:dedup:{event_payload.event_id}"
        inserted = await redis_repo.client.set(dedup_key, "1", ex=max(dedup_ttl, 1), nx=True)
        if not inserted:
            duplicate.append(event_payload.event_id)
            continue

        try:
            await behavior_producer.send_behavior(_build_kafka_event(user_id, event_payload))
            accepted.append(event_payload.event_id)
        except Exception:
            await redis_repo.client.delete(dedup_key)
            failed.append({"eventId": event_payload.event_id, "reason": "kafka_unavailable"})

    return {
        "status": "accepted" if not failed else "partial",
        "summary": {
            "total": len(payload.events),
            "accepted": len(accepted),
            "duplicate": len(duplicate),
            "failed": len(failed),
        },
        "acceptedEventIds": accepted,
        "duplicateEventIds": duplicate,
        "failedEvents": failed,
    }
