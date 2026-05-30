FROM python:3.12-slim AS build
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 UV_LINK_MODE=copy
WORKDIR /app
RUN pip install --no-cache-dir uv
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY . .

FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=8097
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app /app
RUN chown -R app:app /app
USER app
EXPOSE 8097
CMD ["/app/.venv/bin/python", "main.py"]
