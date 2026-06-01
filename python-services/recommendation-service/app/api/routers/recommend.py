import logging

from fastapi import APIRouter, HTTPException, Query, Request

logger = logging.getLogger("recommendation-service.recommend-api")

router = APIRouter(prefix="/recommend", tags=["recommendation"])


async def _enrich_items_if_available(request: Request, items: list[dict]) -> list[dict]:
    enricher = getattr(request.app.state, "recommendation_enricher", None)
    if enricher is None:
        return items
    return await enricher.enrich_ranked_items(items)


@router.get("/user/{user_id}")
async def recommend_for_user(
    request: Request,
    user_id: str,
    top_k: int = Query(default=10, ge=1, le=100),
    enrich: bool = Query(default=True),
) -> dict:
    pipeline = getattr(request.app.state, "training_pipeline", None)
    if pipeline is None:
        raise HTTPException(status_code=503, detail="Recommendation pipeline unavailable")
    try:
        payload = await pipeline.recommend_for_user(user_id, top_k)
    except Exception:
        logger.exception(
            "User recommendation failed; falling back to popular recommendations user_id=%s top_k=%s",
            user_id,
            top_k,
        )
        payload = await pipeline.popular_products(top_k)
        payload = {
            "userId": user_id,
            "topK": top_k,
            "source": "popular-fallback",
            "recommendations": payload.get("popular") or [],
        }
    if enrich:
        payload["recommendations"] = await _enrich_items_if_available(request, payload.get("recommendations") or [])
    return payload


@router.get("/similar/{product_id}")
async def similar_products(
    request: Request,
    product_id: str,
    top_k: int = Query(default=10, ge=1, le=100),
    enrich: bool = Query(default=True),
) -> dict:
    pipeline = getattr(request.app.state, "training_pipeline", None)
    if pipeline is None:
        raise HTTPException(status_code=503, detail="Recommendation pipeline unavailable")
    payload = await pipeline.similar_products(product_id, top_k)
    if enrich:
        payload["similar"] = await _enrich_items_if_available(request, payload.get("similar") or [])
    return payload


@router.get("/popular")
async def popular_products(
    request: Request,
    top_k: int = Query(default=10, ge=1, le=100),
    enrich: bool = Query(default=True),
) -> dict:
    pipeline = getattr(request.app.state, "training_pipeline", None)
    if pipeline is None:
        raise HTTPException(status_code=503, detail="Recommendation pipeline unavailable")
    payload = await pipeline.popular_products(top_k)
    if enrich:
        payload["popular"] = await _enrich_items_if_available(request, payload.get("popular") or [])
    return payload
