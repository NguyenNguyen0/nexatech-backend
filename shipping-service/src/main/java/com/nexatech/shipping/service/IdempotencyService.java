package com.nexatech.shipping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.shipping.entity.ShipmentIdempotencyKey;
import com.nexatech.shipping.enums.IdempotencyStatus;
import com.nexatech.shipping.exception.ConflictException;
import com.nexatech.shipping.repository.ShipmentIdempotencyKeyRepository;
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

    private final ShipmentIdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public Optional<String> checkAndGetCached(String idempotencyKey) {
        Optional<ShipmentIdempotencyKey> recordOptional = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOptional.isEmpty()) {
            return Optional.empty();
        }

        ShipmentIdempotencyKey record = recordOptional.get();
        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            log.info("Shipping idempotent hit: key={}", idempotencyKey);
            return Optional.ofNullable(record.getResponsePayload());
        }
        if (record.getStatus() == IdempotencyStatus.FAILED) {
            return Optional.empty();
        }

        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new ConflictException("Request is being processed. Please wait before retrying.");
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(String idempotencyKey, String orderId) {
        Optional<ShipmentIdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            ShipmentIdempotencyKey record = existing.get();
            if (record.getStatus() == IdempotencyStatus.FAILED) {
                record.setOrderId(orderId);
                record.setShipmentId(null);
                record.setResponsePayload(null);
                record.setStatus(IdempotencyStatus.PROCESSING);
                idempotencyKeyRepository.save(record);
                return;
            }
            throw new ConflictException("Idempotency key conflict: request is processing or already completed.");
        }

        try {
            idempotencyKeyRepository.save(ShipmentIdempotencyKey.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(orderId)
                    .status(IdempotencyStatus.PROCESSING)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Idempotency key conflict: request is processing or already completed.");
        }
    }

    @Transactional
    public void markCompleted(String idempotencyKey, UUID shipmentId, Object responsePayload) {
        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            try {
                record.setShipmentId(shipmentId);
                record.setResponsePayload(objectMapper.writeValueAsString(responsePayload));
                record.setStatus(IdempotencyStatus.COMPLETED);
                idempotencyKeyRepository.save(record);
            } catch (Exception e) {
                log.error("Unable to serialize shipping idempotency response: key={} error={}",
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
