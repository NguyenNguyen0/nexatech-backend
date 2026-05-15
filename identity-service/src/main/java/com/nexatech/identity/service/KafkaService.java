package com.nexatech.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.identity.dto.request.UserClientRequest;
import com.nexatech.identity.dto.event.ForgotPasswordEvent;
import com.nexatech.identity.exception.AppException;
import com.nexatech.identity.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topics.user-registration}")
    private String userRegistrationTopic;

    @Value("${spring.kafka.topics.user-events:user.events}")
    private String userEventsTopic;

    public void sendUserRegistered(UserClientRequest payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            SendResult<String, String> result = kafkaTemplate.send(userRegistrationTopic, payload.getId(), json).get();
            log.info("Published user registration event topic={} partition={} offset={}",
                    userRegistrationTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize user registration event", ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        } catch (Exception ex) {
            log.error("Failed to publish user registration event", ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    public void sendForgotPasswordEvent(ForgotPasswordEvent payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            SendResult<String, String> result = kafkaTemplate.send(userEventsTopic, payload.getUserId(), json).get();
            log.info("Published forgot password event topic={} partition={} offset={}",
                    userEventsTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize forgot password event", ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        } catch (Exception ex) {
            log.error("Failed to publish forgot password event", ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }
}
