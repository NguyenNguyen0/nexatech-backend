import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    service_name: str = os.getenv("SERVICE_NAME", "recommendation-service")
    service_version: str = os.getenv("SERVICE_VERSION", "0.1.0")
    port: int = int(os.getenv("PORT", "8095"))

    eureka_enabled: bool = os.getenv("EUREKA_ENABLED", "true").lower() == "true"
    eureka_server_url: str = os.getenv("EUREKA_SERVER_URL", "http://localhost:8761/eureka/")
    eureka_instance_hostname: str = os.getenv("EUREKA_INSTANCE_HOSTNAME", "127.0.0.1")

    product_service_api_key: str = os.getenv("PRODUCT_SERVICE_API_KEY", "")


def get_settings() -> Settings:
    return Settings()
