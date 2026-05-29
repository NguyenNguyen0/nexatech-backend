package com.nexatech.inventory.exception;

import com.nexatech.inventory.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String SERVICE_PREFIX = "INV";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("ResourceNotFoundException: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("BadRequestException: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.warn("UnauthorizedException: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        log.warn("ForbiddenException: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        String tag = buildTag(HttpStatus.BAD_REQUEST);
        log.warn("[{}] {} {} correlationId={} userId={} - Validation failed",
            tag,
            request.getMethod(),
            request.getRequestURI(),
            correlationId,
            resolveUserId(request));
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> ErrorResponse.FieldError.builder()
                        .field(e.getField())
                        .message(e.getDefaultMessage())
                        .build())
                .toList();

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
            .tag(tag)
                .message("Validation failed")
                .errors(fieldErrors)
            .correlationId(correlationId)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex,
                                                           HttpServletRequest request) {
        log.warn("MissingServletRequestPartException: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Missing multipart part 'file'. Please upload CSV as form-data key=file.",
                request);
    }

    @ExceptionHandler({MultipartException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleMultipart(Exception ex, HttpServletRequest request) {
        log.warn("Multipart upload error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Invalid multipart upload payload. Please check form-data and file size.",
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                    HttpServletRequest request) {
        log.warn("HttpMediaTypeNotSupportedException: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported content type. Use multipart/form-data for CSV import.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        String tag = buildTag(HttpStatus.INTERNAL_SERVER_ERROR);
        log.error("[{}] {} {} correlationId={} userId={} - {}",
                tag,
                request.getMethod(),
                request.getRequestURI(),
                correlationId,
                resolveUserId(request),
                ex.getMessage(),
                ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        String tag = buildTag(status);
        if (status.is4xxClientError()) {
            log.warn("[{}] {} {} correlationId={} userId={} - {}",
                    tag,
                    request.getMethod(),
                    request.getRequestURI(),
                    correlationId,
                    resolveUserId(request),
                    message);
        }

        ErrorResponse response = ErrorResponse.builder()
                .status(status.value())
                .tag(tag)
                .message(message)
                .correlationId(correlationId)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(response);
    }

    private String buildTag(HttpStatus status) {
        return SERVICE_PREFIX + "-" + status.value();
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String fromMdc = MDC.get("correlationId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = request.getHeader("X-Correlation-Id");
        return (fromHeader != null && !fromHeader.isBlank()) ? fromHeader : "unknown";
    }

    private String resolveUserId(HttpServletRequest request) {
        String fromMdc = MDC.get("userId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = request.getHeader("X-User-Id");
        return (fromHeader != null && !fromHeader.isBlank()) ? fromHeader : "anonymous";
    }
}
