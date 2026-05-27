import logging
from contextvars import ContextVar


request_id_ctx_var: ContextVar[str] = ContextVar("request_id", default="-")


class RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_ctx_var.get()
        return True


def set_request_id(request_id: str) -> None:
    request_id_ctx_var.set(request_id)


def configure_logging(level: str = "INFO") -> None:
    logging.getLogger().handlers.clear()
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s [%(name)s] [req=%(request_id)s] %(message)s",
    )

    request_id_filter = RequestIdFilter()
    for handler in logging.getLogger().handlers:
        handler.addFilter(request_id_filter)
