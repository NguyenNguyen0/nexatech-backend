from __future__ import annotations

import logging
from contextvars import ContextVar


correlation_id_ctx: ContextVar[str] = ContextVar("correlation_id", default="-")
user_id_ctx: ContextVar[str] = ContextVar("user_id", default="-")
user_role_ctx: ContextVar[str] = ContextVar("user_role", default="-")
user_email_ctx: ContextVar[str] = ContextVar("user_email", default="-")


class CorrelationIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = correlation_id_ctx.get()
        record.user_id = user_id_ctx.get()
        record.user_role = user_role_ctx.get()
        return True


def configure_logging(service_name: str, log_level: str) -> None:
    level = getattr(logging, log_level.upper(), logging.INFO)
    logging.basicConfig(
        level=level,
        format=(
            "%(asctime)s %(levelname)s [%(name)s] "
            "[correlationId=%(correlation_id)s userId=%(user_id)s userRole=%(user_role)s] %(message)s"
        ),
    )

    root_logger = logging.getLogger()
    root_logger.setLevel(level)
    for handler in root_logger.handlers:
        handler.addFilter(CorrelationIdFilter())

    logging.getLogger(service_name)