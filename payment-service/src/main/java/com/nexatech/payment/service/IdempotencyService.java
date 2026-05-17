package com.nexatech.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.payment.enums.IdempotencyStatus;
import com.nexatech.payment.exception.ConflictException;
import com.nexatech.payment.model.PaymentIdempotencyKey;
import com.nexatech.payment.repository.PaymentIdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Đảm bảo tính idempotency cho Payment API.
 *
 * Flow:
 *  1. Client gửi request với header Idempotency-Key
 *  2. Nếu key chưa tồn tại → insert PROCESSING, cho phép xử lý
 *  3. Nếu key COMPLETED    → trả cached response
 *  4. Nếu key PROCESSING   → trả 409 (đang xử lý, tránh duplicate)
 *  5. Sau khi xử lý xong   → gọi markCompleted()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final PaymentIdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Kiểm tra nếu key đã được xử lý thành công → trả về cached response.
     * Nếu PROCESSING → ném 409.
     * Nếu chưa tồn tại → trả empty.
     */
    public Optional<String> checkAndGetCached(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).map(record -> {
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Idempotent hit (COMPLETED): key={}", idempotencyKey);
                return record.getResponsePayload();
            } else {
                // PROCESSING — đang xử lý, từ chối duplicate
                throw new ConflictException("Request đang được xử lý. Vui lòng đợi trước khi retry.");
            }
        });
    }

    /**
     * Ghi key với trạng thái PROCESSING trước khi xử lý.
     * Dùng REQUIRES_NEW để commit ngay lập tức — tránh race condition.
     * Nếu DataIntegrityViolation (key đã tồn tại) → ném 409.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(String idempotencyKey, UUID orderId) {
        try {
            idempotencyKeyRepository.save(PaymentIdempotencyKey.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(orderId)
                    .status(IdempotencyStatus.PROCESSING)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Idempotency key conflict: request đang được xử lý hoặc đã hoàn thành.");
        }
    }

    /**
     * Cập nhật trạng thái COMPLETED với response đã serialize.
     */
    @Transactional
    public void markCompleted(String idempotencyKey, UUID paymentId, Object responsePayload) {
        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            try {
                record.setPaymentId(paymentId);
                record.setResponsePayload(objectMapper.writeValueAsString(responsePayload));
                record.setStatus(IdempotencyStatus.COMPLETED);
                idempotencyKeyRepository.save(record);
            } catch (Exception e) {
                log.error("Không thể serialize idempotency response: key={} error={}", idempotencyKey, e.getMessage());
            }
        });
    }

    /**
     * Đánh dấu FAILED — cho phép client retry với cùng key.
     */
    @Transactional
    public void markFailed(String idempotencyKey) {
        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            idempotencyKeyRepository.save(record);
        });
    }
}
