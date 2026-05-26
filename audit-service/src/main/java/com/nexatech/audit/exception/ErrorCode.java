package com.nexatech.audit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1001, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1002, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_KEY(1003, "Invalid key", HttpStatus.BAD_REQUEST),
    INVALID_JSON_BODY(1004, "Invalid or malformed JSON", HttpStatus.BAD_REQUEST),
    INVALID_PAGE_PARAMETER(1005, "Invalid page parameter", HttpStatus.BAD_REQUEST),
    INVALID_SORT_PARAMETER(1006, "Invalid sort parameter", HttpStatus.BAD_REQUEST),

    AUDIT_LOG_NOT_FOUND(3001, "Audit log not found", HttpStatus.NOT_FOUND),
    INVALID_AUDIT_ACTION(3002, "Invalid audit action", HttpStatus.BAD_REQUEST),
    INVALID_AUDIT_ENTITY_TYPE(3003, "Invalid audit entity type", HttpStatus.BAD_REQUEST),
    INVALID_AUDIT_PAYLOAD(3004, "Invalid audit event payload", HttpStatus.BAD_REQUEST),
    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
