package com.nexatech.user.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.user.dto.keycloak.KeyCloakError;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ErrorNormalizer {
    private final ObjectMapper objectMapper;
    private final Map<String, ErrorCode> errorCodeMap;

    public ErrorNormalizer() {
        objectMapper = new ObjectMapper();
        errorCodeMap = new HashMap<>();

        errorCodeMap.put("User exists with same username", ErrorCode.USER_EXISTED);
        errorCodeMap.put("User exists with same email", ErrorCode.EMAIL_EXISTED);
        errorCodeMap.put("User name is missing", ErrorCode.USERNAME_IS_MISSING);
        errorCodeMap.put("Token is not active", ErrorCode.UNAUTHENTICATED);
    }

    public AppException handleKeyCloakException(FeignException exception) {
        try {
            log.warn("Cannot complete request to Keycloak: status={}, body={}",
                    exception.status(), exception.contentUTF8());
            var response = objectMapper.readValue(exception.contentUTF8(), KeyCloakError.class);

            String message = null;
            if (Objects.nonNull(response.getErrorMessage())) {
                message = response.getErrorMessage();
            } else if (Objects.nonNull(response.getErrorDescription())) {
                message = response.getErrorDescription();
            }

            if (Objects.nonNull(message) && Objects.nonNull(errorCodeMap.get(message))) {
                return new AppException(errorCodeMap.get(message));
            }
        } catch (JsonProcessingException e) {
            log.error("Cannot deserialize Keycloak error content: {}", e.getMessage());
        }

        return new AppException(ErrorCode.KEYCLOAK_ERROR);
    }
}
