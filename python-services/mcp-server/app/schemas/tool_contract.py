from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ToolError(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    code: str = Field(description="Stable machine-readable error code or tag.")
    message: str = Field(description="Human-readable error message.")
    rollback_needed: bool = Field(default=False, alias="rollbackNeeded")
    retry_allowed: bool = Field(default=True, alias="retryAllowed")
    details: dict[str, Any] = Field(default_factory=dict)


class ToolResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    success: bool
    data: Any = None
    error: ToolError | None = None
    requires_approval: bool = Field(default=False, alias="requiresApproval")
    idempotency_key: str | None = Field(default=None, alias="idempotencyKey")


def tool_result(
    payload: Any,
    *,
    requires_approval: bool = False,
    idempotency_key: str | None = None,
    rollback_needed: bool = False,
    retry_allowed: bool = True,
) -> dict[str, Any]:
    """Convert downstream service payloads into the MCP tool contract.

    The returned dictionary intentionally includes both the new contract
    (`success`, `data`, `error`) and the existing agent-friendly `ok` field so
    current callers can migrate gradually.
    """
    if isinstance(payload, dict) and payload.get("ok") is False:
        raw_error = payload.get("error")
        details = raw_error if isinstance(raw_error, dict) else payload
        code = (
            payload.get("tag")
            or details.get("tag")
            or details.get("code")
            or "MCP-TOOL-ERROR"
        )
        message = (
            payload.get("message")
            or details.get("message")
            or "MCP tool call failed"
        )
        result = ToolResult(
            success=False,
            error=ToolError(
                code=str(code),
                message=str(message),
                rollbackNeeded=rollback_needed,
                retryAllowed=retry_allowed,
                details=details,
            ),
            requiresApproval=requires_approval,
            idempotencyKey=idempotency_key,
        ).model_dump(by_alias=True)
        result.update(
            {
                "ok": False,
                "service": payload.get("service") or details.get("service"),
                "statusCode": payload.get("statusCode") or details.get("statusCode"),
                "tag": payload.get("tag") or details.get("tag") or str(code),
                "message": str(message),
                "correlationId": payload.get("correlationId")
                or details.get("correlationId"),
            }
        )
        return result

    result = ToolResult(
        success=True,
        data=payload,
        requiresApproval=requires_approval,
        idempotencyKey=idempotency_key,
    ).model_dump(by_alias=True)
    result["ok"] = True
    return result


def validation_error(message: str, *, code: str = "MCP-VALIDATION") -> dict[str, Any]:
    result = ToolResult(
        success=False,
        error=ToolError(code=code, message=message, retryAllowed=False),
    ).model_dump(by_alias=True)
    result.update({"ok": False, "tag": code, "message": message, "statusCode": 400})
    return result
