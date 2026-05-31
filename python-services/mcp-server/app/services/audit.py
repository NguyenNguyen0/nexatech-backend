from __future__ import annotations

from app.core.http import ServiceClient, ServiceDefinition
from app.core.settings import SETTINGS
from app.services.base import BaseServiceApi


AUDIT_PARAM_KEYS = {
    "action",
    "entityType",
    "entityId",
    "actor",
    "sourceService",
    "from",
    "to",
    "page",
    "size",
    "sortBy",
    "sortDir",
}


class AuditServiceApi(BaseServiceApi):
    async def search_audit_logs(self, params: dict[str, object] | None = None) -> object:
        return await self.client.request("GET", "/audit", params=_clean_search_params(params))

    async def get_audit_entry(self, entry_id: str) -> object:
        return await self.client.request("GET", f"/audit/{entry_id}")

    async def trace_by_lookup_id(self, lookup_id: str) -> object:
        return await self.client.request("GET", f"/audit/trace/{lookup_id}")

    async def get_audit_facets(self) -> object:
        return await self.client.request("GET", "/audit/facets")


def _clean_search_params(params: dict[str, object] | None) -> dict[str, object]:
    if not params:
        return {}

    cleaned: dict[str, object] = {}
    for key, value in params.items():
        if key not in AUDIT_PARAM_KEYS or value is None or value == "":
            continue
        cleaned[key] = value
    return cleaned


AUDIT_API = AuditServiceApi(
    ServiceClient(
        ServiceDefinition(
            name="audit-service",
            base_url=SETTINGS.audit_service_base_url,
            health_path=SETTINGS.audit_service_health_path,
            timeout_seconds=SETTINGS.service_request_timeout_seconds,
            retry_attempts=SETTINGS.service_retry_attempts,
            retry_backoff_seconds=SETTINGS.service_retry_backoff_seconds,
            retry_max_backoff_seconds=SETTINGS.service_retry_max_backoff_seconds,
        )
    )
)
