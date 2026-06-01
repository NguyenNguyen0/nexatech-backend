package com.nexatech.news.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {

    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1002, "You do not have permission", HttpStatus.FORBIDDEN),

    NEWS_NOT_FOUND(3100, "News not found", HttpStatus.NOT_FOUND),
    NEWS_SLUG_EXISTED(3101, "News slug already exists", HttpStatus.BAD_REQUEST),
    NEWS_TITLE_REQUIRED(3102, "News title is required", HttpStatus.BAD_REQUEST),
    NEWS_SLUG_REQUIRED(3104, "News slug is required", HttpStatus.BAD_REQUEST),
    IS_PUBLIC_REQUIRED(3105, "News visibility is required", HttpStatus.BAD_REQUEST),

    IMAGE_NOT_FOUND(3200, "Image not found", HttpStatus.NOT_FOUND),
    IMAGE_UPLOAD_FAILED(3201, "Failed to upload image", HttpStatus.INTERNAL_SERVER_ERROR),
    IMAGE_DELETE_FAILED(3202, "Failed to delete image", HttpStatus.INTERNAL_SERVER_ERROR);

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
