package com.nexatech.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1001, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1002, "You do not have permission", HttpStatus.FORBIDDEN),
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    USER_EMAIL_EXISTS(2002, "Email already exists", HttpStatus.BAD_REQUEST),
    INVALID_UUID(2003, "Invalid id format", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(2004, "Role not found", HttpStatus.BAD_REQUEST),
    ROLE_NAME_EXISTS(2005, "Role name already exists", HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_FOUND(2010, "Permission not found", HttpStatus.NOT_FOUND),
    PERMISSION_NAME_EXISTS(2011, "Permission name already exists", HttpStatus.BAD_REQUEST),
    ADDRESS_NOT_FOUND(2020, "Address not found", HttpStatus.NOT_FOUND),
    KEYCLOAK_ERROR(2021, "Keycloak error", HttpStatus.BAD_REQUEST),
    USER_EXISTED(2022, "User already exists", HttpStatus.BAD_REQUEST),
    EMAIL_EXISTED(2022, "Email already exists", HttpStatus.BAD_REQUEST),
    USERNAME_IS_MISSING(2023, "Username is missing", HttpStatus.BAD_REQUEST),
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
