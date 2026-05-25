# AGENTS.md - Project Guidelines

**Project**: FastAPI + UV + Python 3.11+

## 🎯 Core Principles

### 1. Asynchronous First (Mandatory)
- The entire codebase must be **asynchronous** by default.
- **Never** use synchronous libraries unless there is no async alternative available.
- Always use:
  - `httpx` (async) for HTTP requests
  - `asyncio` for concurrency and task management
  - `asyncpg` for PostgreSQL
  - `aiokafka` for Kafka
  - `redis.asyncio` for Redis
  - `aiofiles` for file operations
  - `SQLAlchemy` with async driver (if using ORM)

### 2. Dependency Management - UV Only
- **Never use `pip`**.
- Use `uv` exclusively for all dependency operations.
- Clearly separate production and development dependencies in `pyproject.toml`:

```toml
[project]
dependencies = [
    "fastapi>=0.115.0",
    "uvicorn[standard]>=0.30.0",
    "pydantic-settings>=2.0.0",
    # production dependencies
]