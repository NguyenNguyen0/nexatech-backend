from fastapi import APIRouter, Query

router = APIRouter(prefix="/recommend", tags=["recommendation"])


@router.get("/user/{user_id}")
async def recommend_for_user(user_id: str, top_k: int = Query(default=10, ge=1, le=100)) -> dict:
    return {
        "userId": user_id,
        "topK": top_k,
        "source": "hybrid-placeholder",
        "recommendations": [],
    }


@router.get("/similar/{product_id}")
async def similar_products(product_id: str, top_k: int = Query(default=10, ge=1, le=100)) -> dict:
    return {
        "productId": product_id,
        "topK": top_k,
        "similar": [],
    }


@router.get("/popular")
async def popular_products(top_k: int = Query(default=10, ge=1, le=100)) -> dict:
    return {
        "topK": top_k,
        "popular": [],
    }
