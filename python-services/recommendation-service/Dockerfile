FROM python:3.12-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_LINK_MODE=copy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        curl \
        libopenblas-dev \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY . .

FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    OPENBLAS_NUM_THREADS=1 \
    MODELS_DIR=/models

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl libopenblas0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/.venv /app/.venv
COPY --from=builder /app /app

RUN mkdir -p /models && chown -R app:app /app /models

USER app

EXPOSE 8095

HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD ["/app/.venv/bin/python", "-c", "import urllib.request,sys;\nresp=urllib.request.urlopen('http://127.0.0.1:8095/health', timeout=3);\nsys.exit(0 if resp.status==200 else 1)"]

VOLUME ["/models"]

CMD ["/app/.venv/bin/python", "main.py"]
