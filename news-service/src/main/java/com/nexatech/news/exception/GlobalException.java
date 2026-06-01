package com.nexatech.news.exception;

import com.nexatech.news.query.model.ApiResponse;
import com.nexatech.news.enums.ErrorCode;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalException {

    private static final String SERVICE_PREFIX = "NWS";

    @ExceptionHandler(value = AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ApiResponse.<Void>builder()
                        .code(errorCode.getCode())
                        .tag(toTag(errorCode))
                        .message(errorCode.getMessage())
                        .correlationId(resolveCorrelationId())
                        .build());
    }

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
        String correlationId = resolveCorrelationId();
        log.error("[{}] RuntimeException correlationId={} userId={} - {}",
            toTag(ErrorCode.UNCATEGORIZED_EXCEPTION),
            correlationId,
            resolveUserId(),
            exception.getMessage(),
            exception);
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
            .tag(toTag(errorCode))
                .message(errorCode.getMessage())
            .correlationId(correlationId)
                .build();

        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<Void>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        String correlationId = resolveCorrelationId();
        log.warn("[{}] AppException correlationId={} userId={} - {}",
            toTag(errorCode),
            correlationId,
            resolveUserId(),
            errorCode.getMessage());

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
            .tag(toTag(errorCode))
                .message(errorCode.getMessage())
            .correlationId(correlationId)
                .build();

        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {
        var fieldError = exception.getFieldError();
        String enumKey = fieldError != null ? fieldError.getDefaultMessage() : null;

        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        if (enumKey != null) {
            try {
                errorCode = ErrorCode.valueOf(enumKey);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown error code: {}", enumKey);
            }
        }

        String correlationId = resolveCorrelationId();
        log.warn("[{}] ValidationFailed correlationId={} userId={} - {}",
                toTag(errorCode),
                correlationId,
                resolveUserId(),
                errorCode.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder()
                        .code(errorCode.getCode())
                        .tag(toTag(errorCode))
                        .message(errorCode.getMessage())
                        .correlationId(correlationId)
                        .build());
    }

    private String toTag(ErrorCode errorCode) {
        return SERVICE_PREFIX + "-" + errorCode.getCode();
    }

    private String resolveCorrelationId() {
        String correlationId = MDC.get("correlationId");
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : "unknown";
    }

    private String resolveUserId() {
        String userId = MDC.get("userId");
        return (userId != null && !userId.isBlank()) ? userId : "anonymous";
    }
}
