package com.nexatech.payment.model;

import com.nexatech.payment.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Đảm bảo API tạo payment là idempotent — client có thể retry an toàn với cùng Idempotency-Key header.
 * Nếu key đã tồn tại và COMPLETED → trả về response đã cache.
 * Nếu key đang PROCESSING → trả 409 Conflict.
 */
@Entity
@Table(name = "payment_idempotency_keys")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_id")
    private UUID paymentId;

    /** SHA-256 hash của request body, dùng để phát hiện request body thay đổi */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IdempotencyStatus status = IdempotencyStatus.PROCESSING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
