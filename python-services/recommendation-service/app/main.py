import uuid

from fastapi import FastAPI
from fastapi import Request

from app.api.routers.health import router as health_router
from app.api.routers.recommend import router as recommend_router
from app.core.config import get_settings
from app.core.lifecycle import lifespan
from app.core.logging import set_request_id


app = FastAPI(
    title="Recommendation Service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.middleware("http")
async def request_context_middleware(request: Request, call_next):
    settings = getattr(request.app.state, "settings", get_settings())
    header_name = settings.request_id_header
    request_id = request.headers.get(header_name) or str(uuid.uuid4())
    set_request_id(request_id)

    response = await call_next(request)
    response.headers[header_name] = request_id
    return response

app.include_router(health_router)
app.include_router(recommend_router)
