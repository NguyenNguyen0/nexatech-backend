package com.nexatech.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.enums.IdempotencyStatus;
import com.nexatech.order.exception.ConflictException;
import com.nexatech.order.model.OrderIdempotencyKey;
import com.nexatech.order.repository.OrderIdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final OrderIdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public Optional<String> checkAndGetCached(String idempotencyKey, String operation) {
        Optional<OrderIdempotencyKey> recordOptional = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOptional.isEmpty()) {
            return Optional.empty();
        }

        OrderIdempotencyKey record = recordOptional.get();
        if (!record.getOperation().equals(operation)) {
            throw new ConflictException("Idempotency key was used for a different operation.");
        }
        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            log.info("Order idempotent hit: key={} operation={}", idempotencyKey, operation);
            return Optional.ofNullable(record.getResponsePayload());
        }
        if (record.getStatus() == IdempotencyStatus.FAILED) {
            return Optional.empty();
        }
        throw new ConflictException("Request is being processed. Please wait before retrying.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(String idempotencyKey, String operation) {
        Optional<OrderIdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            OrderIdempotencyKey record = existing.get();
            if (!record.getOperation().equals(operation)) {
                throw new ConflictException("Idempotency key was used for a different operation.");
            }
            if (record.getStatus() == IdempotencyStatus.FAILED) {
                record.setOrderId(null);
                record.setResponsePayload(null);
                record.setStatus(IdempotencyStatus.PROCESSING);
                idempotencyKeyRepository.save(record);
                return;
            }
            throw new ConflictException("Idempotency key conflict: request is processing or already completed.");
        }

        try {
            idempotencyKeyRepository.save(OrderIdempotencyKey.builder()
                    .idempotencyKey(idempotencyKey)
                    .operation(operation)
                    .status(IdempotencyStatus.PROCESSING)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Idempotency key conflict: request is processing or already completed.");
        }
    }

    @Transactional
    public void markCompleted(String idempotencyKey, UUID orderId, Object responsePayload) {
        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            try {
                record.setOrderId(orderId);
                record.setResponsePayload(objectMapper.writeValueAsString(responsePayload));
                record.setStatus(IdempotencyStatus.COMPLETED);
                idempotencyKeyRepository.save(record);
            } catch (Exception e) {
                log.error("Unable to serialize order idempotency response: key={} error={}",
                        idempotencyKey, e.getMessage());
            }
        });
    }

    @Transactional
    public void markFailed(String idempotencyKey) {
        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            idempotencyKeyRepository.save(record);
        });
    }
}
