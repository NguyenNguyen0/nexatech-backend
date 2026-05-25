from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    service_name: str = "recommendation-service"
    service_version: str = "0.1.0"
    port: int = 8095
    log_level: str = "INFO"
    request_id_header: str = "X-Request-Id"

    eureka_enabled: bool = True
    eureka_server_url: str = "http://localhost:8761/eureka/"
    eureka_instance_hostname: str = "127.0.0.1"

    mongo_uri: str = "mongodb://nexatech:nexatech123@localhost:27017/recommendation?authSource=admin"
    mongo_database: str = "recommendation"

    redis_url: str = "redis://localhost:6379/0"

    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_behavior_topic: str = "user-behavior-events"
    kafka_behavior_consumer_group: str = "rec-behavior-collector"
    kafka_product_topic: str = "product.events"
    kafka_product_consumer_group: str = "rec-product-catalog-sync"

    product_service_base_url: str = "http://localhost:8090"
    product_service_catalog_path: str = "/api/v1/internal/products/all"
    product_service_api_key_header: str = "X-Internal-Api-Key"
    product_service_api_key: str = "change-me"

    cf_alpha: float = 0.6
    recommendation_model_refresh_seconds: int = 120
    recommendation_cache_ttl_seconds: int = 60
    training_cron_hour: int = 2
    training_cron_minute: int = 0


@lru_cache
def get_settings() -> Settings:
    return Settings()
