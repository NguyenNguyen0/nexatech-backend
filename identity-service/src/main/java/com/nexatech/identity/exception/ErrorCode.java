package com.nexatech.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1001, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1002, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_KEY(1003, "Invalid key", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(1004, "Email is required", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(1005, "Invalid email format", HttpStatus.BAD_REQUEST),
    PASSWORD_TOO_SHORT(1006, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1007, "Password is required", HttpStatus.BAD_REQUEST),
    REFRESH_TOKEN_REQUIRED(1008, "Refresh token is required", HttpStatus.BAD_REQUEST),
    TOKEN_REQUIRED(1009, "Token is required", HttpStatus.BAD_REQUEST),
    ROLE_NAME_REQUIRED(1010, "Role name is required", HttpStatus.BAD_REQUEST),
    GROUP_NAME_REQUIRED(1011, "Group name is required", HttpStatus.BAD_REQUEST),
    CREDENTIAL_TYPE_REQUIRED(1012, "Credential type is required", HttpStatus.BAD_REQUEST),
    CREDENTIAL_VALUE_REQUIRED(1013, "Credential value is required", HttpStatus.BAD_REQUEST),
    CREDENTIALS_REQUIRED(1014, "At least one credential is required", HttpStatus.BAD_REQUEST),
    RECEIVER_NAME_REQUIRED(1015, "Receiver name is required", HttpStatus.BAD_REQUEST),
    PHONE_REQUIRED(1016, "Phone is required", HttpStatus.BAD_REQUEST),
    PROVINCE_REQUIRED(1017, "Province is required", HttpStatus.BAD_REQUEST),
    DETAIL_REQUIRED(1018, "Address detail is required", HttpStatus.BAD_REQUEST),
    ADDRESS_TYPE_REQUIRED(1019, "Address type is required", HttpStatus.BAD_REQUEST),
    ROLE_REF_NAME_REQUIRED(1020, "Role reference name is required", HttpStatus.BAD_REQUEST),
    USERNAME_TOO_LONG(1021, "Username must be at most {max} characters", HttpStatus.BAD_REQUEST),
    PERMISSION_NAME_REQUIRED(1022, "Permission name is required", HttpStatus.BAD_REQUEST),
    PERMISSION_GROUP_REQUIRED(1023, "Permission group is required", HttpStatus.BAD_REQUEST),
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
