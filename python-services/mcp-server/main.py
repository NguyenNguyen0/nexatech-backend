from app.server import app


if __name__ == "__main__":
    import uvicorn

    from app.core.settings import SETTINGS

    uvicorn.run(
        "app.server:app",
        host="0.0.0.0",
        port=SETTINGS.port,
        reload=False,
    )
