from fastapi import APIRouter, HTTPException, Query, Request

router = APIRouter(prefix="/recommend", tags=["recommendation"])


@router.get("/user/{user_id}")
async def recommend_for_user(request: Request, user_id: str, top_k: int = Query(default=10, ge=1, le=100)) -> dict:
    pipeline = getattr(request.app.state, "training_pipeline", None)
    if pipeline is None:
        raise HTTPException(status_code=503, detail="Recommendation pipeline unavailable")
    return await pipeline.recommend_for_user(user_id, top_k)


@router.get("/similar/{product_id}")
async def similar_products(request: Request, product_id: str, top_k: int = Query(default=10, ge=1, le=100)) -> dict:
    pipeline = getattr(request.app.state, "training_pipeline", None)
    if pipeline is None:
        raise HTTPException(status_code=503, detail="Recommendation pipeline unavailable")
    return await pipeline.similar_products(product_id, top_k)


@router.get("/popular")
async def popular_products(request: Request, top_k: int = Query(default=10, ge=1, le=100)) -> dict:
    pipeline = getattr(request.app.state, "training_pipeline", None)
    if pipeline is None:
        raise HTTPException(status_code=503, detail="Recommendation pipeline unavailable")
    return await pipeline.popular_products(top_k)
