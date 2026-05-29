package com.nexatech.identity.exception;

import java.util.Map;
import java.util.Objects;

import jakarta.validation.ConstraintViolation;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.identity.dto.base.ApiResponse;

import feign.FeignException;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String MIN_ATTRIBUTE = "min";
    private static final String MAX_ATTRIBUTE = "max";
    private static final String SERVICE_PREFIX = "IDN";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<?>> handlingHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Malformed JSON body: {}", exception.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_JSON_BODY;
        return ResponseEntity.status(errorCode.getStatusCode())
            .body(withContext(ApiResponse.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                .build(), errorCode));
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<?>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<?> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());
        apiResponse.setTag(toTag(errorCode));
        apiResponse.setCorrelationId(resolveCorrelationId());
        log.warn("[{}] AppException correlationId={} userId={} - {}",
                apiResponse.getTag(),
                apiResponse.getCorrelationId(),
                resolveUserId(),
                errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    ResponseEntity<ApiResponse<?>> handlingAccessDeniedException(AccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(errorCode.getStatusCode())
            .body(withContext(ApiResponse.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                .build(), errorCode));
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<?>> handlingValidation(MethodArgumentNotValidException exception) {
        ObjectError first = exception.getBindingResult().getAllErrors().isEmpty()
                ? null
                : exception.getBindingResult().getAllErrors().getFirst();
        if (first == null) {
            return ResponseEntity.badRequest()
                    .body(withContext(ApiResponse.builder()
                            .code(ErrorCode.INVALID_KEY.getCode())
                            .message(ErrorCode.INVALID_KEY.getMessage())
                        .build(), ErrorCode.INVALID_KEY));
        }

        String enumKey = first.getDefaultMessage();
        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        Map<String, Object> attributes = null;
        try {
            errorCode = ErrorCode.valueOf(enumKey);
        } catch (IllegalArgumentException e) {
            log.debug("Validation message is not an ErrorCode name: {}", enumKey);
        }
        if (first instanceof FieldError) {
            try {
                ConstraintViolation<?> unwrapped = first.unwrap(ConstraintViolation.class);
                attributes = unwrapped.getConstraintDescriptor().getAttributes();
            } catch (Exception ignored) {
            }
        }

        ApiResponse<?> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(
                Objects.nonNull(attributes)
                        ? mapAttribute(errorCode.getMessage(), attributes)
                        : errorCode.getMessage());
        apiResponse.setTag(toTag(errorCode));
        apiResponse.setCorrelationId(resolveCorrelationId());
        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<?>> handlingGenericException(Exception exception) {
        String correlationId = resolveCorrelationId();
        log.error("[{}] GenericException correlationId={} userId={} - {}",
            toTag(ErrorCode.UNCATEGORIZED_EXCEPTION),
            correlationId,
            resolveUserId(),
            exception.getMessage(),
            exception);
        if (exception instanceof IllegalStateException
                && exception.getMessage() != null
                && exception.getMessage().startsWith("Keycloak expected HTTP")) {
            return ResponseEntity.badRequest()
                .body(withContext(ApiResponse.builder()
                            .code(ErrorCode.KEYCLOAK_ERROR.getCode())
                            .message(exception.getMessage())
                    .build(), ErrorCode.KEYCLOAK_ERROR));
        }

        ApiResponse<?> apiResponse = new ApiResponse<>();

        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
        apiResponse.setTag(toTag(ErrorCode.UNCATEGORIZED_EXCEPTION));
        apiResponse.setCorrelationId(correlationId);

        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(FeignException.class)
    ResponseEntity<ApiResponse<?>> handlingFeignException(FeignException exception) {
        int status = exception.status();
        log.warn("Feign/Keycloak error: httpStatus={} message={}", status, exception.getMessage());

        String dynamicMessage = extractKeycloakErrorMessage(exception);

        ErrorCode errorCode = switch (status) {
            case 401 -> ErrorCode.INVALID_CREDENTIALS;
            case 403 -> ErrorCode.UNAUTHORIZED;
            default -> ErrorCode.KEYCLOAK_ERROR;
        };

        HttpStatus responseStatus = status >= 500 ? HttpStatus.BAD_GATEWAY : (HttpStatus) errorCode.getStatusCode();

        return ResponseEntity.status(responseStatus)
                .body(withContext(ApiResponse.builder()
                        .code(errorCode.getCode())
                        .message(dynamicMessage != null ? dynamicMessage : errorCode.getMessage())
                        .build(), errorCode));
    }

    private ApiResponse<?> withContext(ApiResponse<?> response, ErrorCode errorCode) {
        response.setTag(toTag(errorCode));
        response.setCorrelationId(resolveCorrelationId());
        return response;
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

    private String extractKeycloakErrorMessage(FeignException exception) {
        try {
            String content = exception.contentUTF8();
            if (content != null && !content.isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(content);
                if (node.isArray() && !node.isEmpty()) {
                    node = node.get(0);
                }
                if (node.has("errorMessage")) {
                    return node.get("errorMessage").asText();
                } else if (node.has("error")) {
                    return node.get("error").asText();
                }
            }
        } catch (Exception e) {
            log.trace("Could not parse Keycloak error body: ", e);
        }
        return null;
    }

    private String mapAttribute(String message, Map<String, Object> attributes) {
        String result = message;
        if (attributes.containsKey(MIN_ATTRIBUTE) && attributes.get(MIN_ATTRIBUTE) != null) {
            result = result.replace("{" + MIN_ATTRIBUTE + "}", String.valueOf(attributes.get(MIN_ATTRIBUTE)));
        }
        if (attributes.containsKey(MAX_ATTRIBUTE) && attributes.get(MAX_ATTRIBUTE) != null) {
            result = result.replace("{" + MAX_ATTRIBUTE + "}", String.valueOf(attributes.get(MAX_ATTRIBUTE)));
        }
        return result;
    }
}