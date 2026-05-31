from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any
from urllib.parse import urljoin

import httpx

from app.core.logging import authorization_ctx, correlation_id_ctx
from app.core.logging import user_email_ctx, user_id_ctx, user_role_ctx
from app.core.resilience import RETRYABLE_STATUS_CODES, retry_async


logger = logging.getLogger(__name__)


@dataclass(slots=True)
class ServiceDefinition:
    name: str
    base_url: str
    health_path: str = "/actuator/health"
    timeout_seconds: float = 5.0
    retry_attempts: int = 3
    retry_backoff_seconds: float = 0.25
    retry_max_backoff_seconds: float = 2.0


@dataclass(slots=True)
class ServiceCallResult:
    ok: bool
    service: str
    status_code: int | None = None
    tag: str | None = None
    message: str | None = None
    correlation_id: str | None = None
    data: Any = None

    def as_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "ok": self.ok,
            "service": self.service,
            "statusCode": self.status_code,
            "tag": self.tag,
            "message": self.message,
            "correlationId": self.correlation_id,
        }
        if self.ok:
            payload["result"] = self.data
        else:
            payload["error"] = {
                "service": self.service,
                "statusCode": self.status_code,
                "tag": self.tag or "MCP-DOWNSTREAM",
                "message": self.message or f"{self.service} unavailable",
                "correlationId": self.correlation_id,
            }
        return payload


class ServiceClient:
    def __init__(self, definition: ServiceDefinition, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self.definition = definition
        self.transport = transport

    def _build_url(self, path: str) -> str:
        base = self.definition.base_url.rstrip("/") + "/"
        clean_path = path.lstrip("/")
        return urljoin(base, clean_path)

    def _current_correlation_id(self) -> str:
        existing = correlation_id_ctx.get()
        return existing if existing and existing != "-" else "-"

    def _headers(
        self,
        user_id: str | None = None,
        user_email: str | None = None,
        user_role: str | None = None,
    ) -> dict[str, str]:
        headers = {"X-Correlation-Id": self._current_correlation_id()}
        effective_user_id = user_id if user_id is not None else user_id_ctx.get()
        effective_user_email = user_email if user_email is not None else user_email_ctx.get()
        effective_user_role = user_role if user_role is not None else user_role_ctx.get()

        if effective_user_id and effective_user_id != "-":
            headers["X-User-Id"] = effective_user_id
        if effective_user_role and effective_user_role != "-":
            headers["X-User-Role"] = effective_user_role
        if effective_user_email and effective_user_email != "-":
            headers["X-User-Email"] = effective_user_email
        authorization = authorization_ctx.get()
        if authorization and authorization != "-":
            headers["Authorization"] = authorization

        return headers

    def _should_retry(self, exc: Exception) -> bool:
        if isinstance(exc, httpx.RequestError):
            return True
        if isinstance(exc, httpx.HTTPStatusError):
            return exc.response.status_code in RETRYABLE_STATUS_CODES
        return False

    def _log_retry(self, attempt: int, exc: Exception, delay: float) -> None:
        logger.warning(
            "[%s] retry attempt=%s delay=%.2fs correlationId=%s error=%s",
            self.definition.name,
            attempt,
            delay,
            self._current_correlation_id(),
            exc.__class__.__name__,
        )

    def _normalize_result(self, payload: Any) -> Any:
        if isinstance(payload, dict) and "result" in payload:
            return payload["result"]
        if isinstance(payload, dict) and payload.get("success") is True and "data" in payload:
            return payload["data"]
        return payload

    async def request(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json: Any | None = None,
        headers: dict[str, str] | None = None,
        user_id: str | None = None,
        user_email: str | None = None,
        user_role: str | None = None,
    ) -> Any:
        url = self._build_url(path)
        request_headers = {
            **self._headers(user_id=user_id, user_email=user_email, user_role=user_role),
            **(headers or {}),
        }
        timeout = httpx.Timeout(self.definition.timeout_seconds)
        clean_params = {key: value for key, value in (params or {}).items() if value is not None}

        async def call() -> Any:
            logger.info(
                "[%s] %s %s correlationId=%s",
                self.definition.name,
                method,
                path,
                request_headers["X-Correlation-Id"],
            )
            async with httpx.AsyncClient(timeout=timeout, transport=self.transport) as client:
                response = await client.request(
                    method,
                    url,
                    params=clean_params,
                    json=json,
                    headers=request_headers,
                )

            if response.status_code in RETRYABLE_STATUS_CODES:
                response.raise_for_status()

            try:
                payload = response.json()
            except ValueError:
                payload = {"message": response.text}

            if response.is_error:
                response.raise_for_status()

            return self._normalize_result(payload)

        try:
            return await retry_async(
                call,
                attempts=self.definition.retry_attempts,
                base_delay_seconds=self.definition.retry_backoff_seconds,
                max_delay_seconds=self.definition.retry_max_backoff_seconds,
                should_retry=self._should_retry,
                on_retry=self._log_retry,
            )
        except Exception as exc:
            correlation_id = request_headers["X-Correlation-Id"]
            if isinstance(exc, httpx.HTTPStatusError):
                response = exc.response
                try:
                    payload = response.json()
                except ValueError:
                    payload = {"message": response.text}

                message = str(payload.get("message") or response.reason_phrase) if isinstance(payload, dict) else response.reason_phrase
                tag = payload.get("tag") if isinstance(payload, dict) else None
                correlation_id = payload.get("correlationId") or correlation_id if isinstance(payload, dict) else correlation_id
                logger.warning(
                    "[%s] %s %s status=%s correlationId=%s - %s",
                    tag or self.definition.name,
                    method,
                    url,
                    response.status_code,
                    correlation_id,
                    message,
                )
                return ServiceCallResult(
                    ok=False,
                    service=self.definition.name,
                    status_code=response.status_code,
                    tag=tag,
                    message=message,
                    correlation_id=correlation_id,
                ).as_dict()

            logger.warning(
                "[%s] %s %s correlationId=%s - %s",
                self.definition.name,
                method,
                url,
                correlation_id,
                exc.__class__.__name__,
            )
            return ServiceCallResult(
                ok=False,
                service=self.definition.name,
                status_code=503,
                tag="MCP-DOWNSTREAM",
                message=f"{self.definition.name} unavailable: {exc.__class__.__name__}",
                correlation_id=correlation_id,
            ).as_dict()

    async def health(self) -> dict[str, Any]:
        result = await self.request("GET", self.definition.health_path)
        if isinstance(result, dict) and result.get("ok") is False:
            return result
        return {
            "ok": True,
            "service": self.definition.name,
            "healthPath": self.definition.health_path,
            "result": result,
        }
