package com.nexatech.audit.exception;

import java.util.Map;
import java.util.Objects;

import jakarta.validation.ConstraintViolation;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.nexatech.audit.dto.base.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String MIN_ATTRIBUTE = "min";
    private static final String MAX_ATTRIBUTE = "max";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<?>> handlingHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Malformed JSON body: {}", exception.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_JSON_BODY;
        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ApiResponse.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build());
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<?>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<?> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    ResponseEntity<ApiResponse<?>> handlingAccessDeniedException(AccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ApiResponse.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<?>> handlingValidation(MethodArgumentNotValidException exception) {
        ObjectError first = exception.getBindingResult().getAllErrors().isEmpty()
                ? null
                : exception.getBindingResult().getAllErrors().getFirst();
        if (first == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .code(ErrorCode.INVALID_KEY.getCode())
                            .message(ErrorCode.INVALID_KEY.getMessage())
                            .build());
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
        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<?>> handlingGenericException(Exception exception) {
        log.error("Exception: ", exception);
        ApiResponse<?> apiResponse = new ApiResponse<>();

        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());

        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode()).body(apiResponse);
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
