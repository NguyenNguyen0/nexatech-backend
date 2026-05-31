from __future__ import annotations

import socket

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


def _resolve_hostname() -> str:
    try:
        return socket.gethostbyname(socket.gethostname())
    except OSError:
        return "127.0.0.1"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    service_name: str = "agent-service"
    port: int = 8097
    log_level: str = "INFO"

    eureka_enabled: bool = True
    eureka_server_url: str = "http://localhost:8761/eureka/"
    eureka_instance_hostname: str = Field(default_factory=_resolve_hostname)

    mcp_server_url: str = "http://localhost:8094"
    request_timeout_seconds: float = Field(default=8.0, gt=0)

    langsmith_tracing: bool = True
    langsmith_endpoint: str = "https://api.smith.langchain.com"
    langsmith_project: str = "nexatech-agent"
    langsmith_api_key: str | None = None

    groq_api_key: str | None = None
    groq_model: str = "llama-3.3-70b-versatile"
    groq_temperature: float = Field(default=0.1, ge=0, le=2)

    agui_agent_name: str = "nexatech_agent"
    agui_agent_description: str = (
        "NexaTech admin agent — news, analytics, audit, promotions"
    )

    agent_db_url: str | None = None
    conversation_store_enabled: bool = True
    mongo_uri: str | None = (
        "mongodb://nexatech:nexatech123@localhost:27017/?authSource=admin"
    )
    mongo_database: str = "nexatech_agent"
    mongo_conversation_collection: str = "conversation_sessions"
    conversation_history_max_events: int = Field(default=500, ge=50, le=10000)

    approval_timeout_seconds: int = Field(default=600, ge=60)
    escalation_timeout_seconds: int = Field(default=900, ge=60)


SETTINGS = Settings()
