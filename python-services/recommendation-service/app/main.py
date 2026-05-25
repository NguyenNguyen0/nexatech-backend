from fastapi import FastAPI

from app.api.routers.health import router as health_router
from app.api.routers.recommend import router as recommend_router
from app.core.lifecycle import lifespan


app = FastAPI(
    title="Recommendation Service",
    version="0.1.0",
    lifespan=lifespan,
)

app.include_router(health_router)
app.include_router(recommend_router)
