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

    service_name: str = "mcp-server"
    port: int = 8094
    log_level: str = "INFO"
    request_timeout_seconds: float = Field(default=5.0, gt=0)

    eureka_enabled: bool = True
    eureka_server_url: str = "http://localhost:8761/eureka/"
    eureka_instance_hostname: str = Field(default_factory=_resolve_hostname)

    mcp_default_user_id: str = "00000000-0000-0000-0000-000000000001"
    mcp_default_user_email: str = "dev@nexatech.local"


SETTINGS = Settings()