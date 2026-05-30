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

    news_service_base_url: str = "http://localhost:8092"
    inventory_service_base_url: str = "http://localhost:8085"
    order_service_base_url: str = "http://localhost:8084"
    payment_service_base_url: str = "http://localhost:8086"
    audit_service_base_url: str = "http://localhost:8096"
    promotion_service_base_url: str = "http://localhost:8093"

    news_service_health_path: str = "/actuator/health"
    inventory_service_health_path: str = "/actuator/health"
    order_service_health_path: str = "/actuator/health"
    payment_service_health_path: str = "/actuator/health"
    audit_service_health_path: str = "/actuator/health"
    promotion_service_health_path: str = "/health"

    service_request_timeout_seconds: float = Field(default=5.0, gt=0)
    service_retry_attempts: int = Field(default=3, ge=1, le=10)
    service_retry_backoff_seconds: float = Field(default=0.25, gt=0)
    service_retry_max_backoff_seconds: float = Field(default=2.0, gt=0)

    mcp_default_user_id: str = "00000000-0000-0000-0000-000000000001"
    mcp_default_user_email: str = "dev@nexatech.local"


SETTINGS = Settings()