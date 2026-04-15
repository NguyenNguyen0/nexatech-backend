package com.nexatech.identity.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserKeycloakConsumer {

    ObjectMapper objectMapper;
    UserService userService;

    @KafkaListener(
            topics = "${spring.kafka.topics.user-registration}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(String json) {
        try {
            UserCreationParam param = objectMapper.readValue(json, UserCreationParam.class);
            if (param.getCredentials() == null || param.getCredentials().isEmpty()) {
                return;
            }
            String userId = userService.createUser(param);
            log.info("Keycloak user created from admin provision event userId={}", userId);
        } catch (Exception ex) {
            log.error("Failed to process admin Keycloak provision event", ex);
            throw new RuntimeException(ex);
        }
    }
}
