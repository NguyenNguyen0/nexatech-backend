package com.nexatech.payment.service;

import com.nexatech.payment.dto.CreatePaymentRequest;
import com.nexatech.payment.dto.PaymentResponse;
import com.nexatech.payment.enums.PaymentMethod;
import com.nexatech.payment.enums.PaymentStatus;
import com.nexatech.payment.event.PaymentEvent;
import com.nexatech.payment.exception.BadRequestException;
import com.nexatech.payment.exception.ResourceNotFoundException;
import com.nexatech.payment.mapper.PaymentMapper;
import com.nexatech.payment.model.Payment;
import com.nexatech.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OutboxEventService outboxEventService;
    private final AuditLogService auditLogService;

    @Transactional
    public PaymentResponse createPayment(UUID customerId, CreatePaymentRequest request) {
        if (paymentRepository.findByOrderId(request.getOrderId()).isPresent()) {
            throw new BadRequestException("Đơn hàng " + request.getOrderId() + " đã có giao dịch thanh toán");
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(customerId)
                .amount(request.getAmount())
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .build();

        Payment saved = paymentRepository.save(payment);

        savePaymentEventToOutbox(saved, "PAYMENT_INITIATED");
        auditLogService.log("PAYMENT_CREATED", "Payment", saved.getId().toString(),
                "status=PENDING", customerId != null ? customerId.toString() : "guest");
        log.info("Tạo thanh toán thành công: {} | đơn hàng: {}", saved.getId(), saved.getOrderId());

        return paymentMapper.toResponse(saved);
    }

    public PaymentResponse getPayment(UUID paymentId) {
        return paymentMapper.toResponse(findById(paymentId));
    }

    public PaymentResponse getPaymentByOrder(UUID orderId) {
        return paymentMapper.toResponse(
                paymentRepository.findByOrderId(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy thanh toán cho đơn hàng: " + orderId))
        );
    }

    public Page<PaymentResponse> getMyPayments(UUID customerId, PaymentStatus status, Pageable pageable) {
        if (status != null) {
            return paymentRepository.findByCustomerIdAndStatus(customerId, status, pageable)
                    .map(paymentMapper::toResponse);
        }
        return paymentRepository.findByCustomerId(customerId, pageable)
                .map(paymentMapper::toResponse);
    }

    public Page<PaymentResponse> getAllPayments(PaymentStatus status, Pageable pageable) {
        if (status != null) {
            return paymentRepository.findByStatus(status, pageable).map(paymentMapper::toResponse);
        }
        return paymentRepository.findAll(pageable).map(paymentMapper::toResponse);
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId) {
        Payment payment = findById(paymentId);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException(
                    "Chỉ có thể hoàn tiền giao dịch ở trạng thái COMPLETED, hiện tại: " + payment.getStatus()
            );
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);

        savePaymentEventToOutbox(saved, "PAYMENT_REFUNDED");
        auditLogService.log("PAYMENT_REFUNDED", "Payment", paymentId.toString(),
                PaymentStatus.COMPLETED.name(), PaymentStatus.REFUNDED.name(), "admin", null);
        log.info("Hoàn tiền thành công: {} | đơn hàng: {}", paymentId, saved.getOrderId());

        return paymentMapper.toResponse(saved);
    }

    @Transactional
    public void confirmPayment(UUID orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getMethod() == PaymentMethod.COD) {
                log.debug("Skip non-COD confirmation for COD payment, orderId={}", orderId);
                return;
            }
            if (payment.getStatus() == PaymentStatus.PENDING
                    || payment.getStatus() == PaymentStatus.PROCESSING) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(Instant.now());
                Payment saved = paymentRepository.save(payment);
                savePaymentEventToOutbox(saved, "PAYMENT_COMPLETED");
                auditLogService.log("PAYMENT_CONFIRMED", "Payment", payment.getId().toString(),
                        "status=COMPLETED", "system");
                log.info("Xác nhận thanh toán thành công cho đơn hàng: {}", orderId);
            }
        });
    }

    @Transactional
    public void confirmCodPayment(UUID orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getMethod() == com.nexatech.payment.enums.PaymentMethod.COD
                    && payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(Instant.now());
                Payment saved = paymentRepository.save(payment);
                savePaymentEventToOutbox(saved, "PAYMENT_COMPLETED");
                auditLogService.log("PAYMENT_CONFIRMED_COD", "Payment", payment.getId().toString(),
                        "status=COMPLETED", "system");
                log.info("Xác nhận thanh toán COD thành công khi giao hàng, orderId={}", orderId);
            }
        });
    }

    /**
     * Hoàn tiền khi đơn hàng bị huỷ (ORDER_CANCELLED event).
     * Chỉ hoàn tiền nếu phương thức thanh toán không phải COD và đã thanh toán thành công.
     */
    @Transactional
    public void refundOnOrderCancellation(UUID orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getMethod() == PaymentMethod.COD) {
                log.debug("COD payment không cần hoàn tiền khi hủy đơn, orderId={}", orderId);
                return;
            }
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                payment.setStatus(PaymentStatus.REFUNDED);
                Payment saved = paymentRepository.save(payment);
                savePaymentEventToOutbox(saved, "PAYMENT_REFUNDED");
                auditLogService.log("PAYMENT_REFUNDED_ON_CANCEL", "Payment", payment.getId().toString(),
                        PaymentStatus.COMPLETED.name(), PaymentStatus.REFUNDED.name(), "system", "order_cancelled");
                log.info("Hoàn tiền do hủy đơn: orderId={}, paymentId={}", orderId, payment.getId());
            } else {
                log.debug("Payment không ở trạng thái COMPLETED, bỏ qua hoàn tiền: status={}, orderId={}",
                        payment.getStatus(), orderId);
            }
        });
    }

    @Transactional
    public void processReturnRefund(UUID orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                payment.setStatus(PaymentStatus.REFUNDED);
                Payment saved = paymentRepository.save(payment);
                savePaymentEventToOutbox(saved, "PAYMENT_REFUNDED");
                auditLogService.log("PAYMENT_RETURN_REFUNDED", "Payment", payment.getId().toString(),
                        "status=REFUNDED", "system");
                log.info("Hoàn tiền thành công cho đơn trả hàng, orderId={}", orderId);
            } else {
                log.warn("Không thể hoàn tiền — trạng thái hiện tại: {}, orderId={}",
                        payment.getStatus(), orderId);
            }
        });
    }
    private Payment findById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch thanh toán: " + paymentId));
    }

    /**
     * Xây dựng PaymentEvent và lưu vào outbox trong cùng transaction nghệp vụ.
     * Scheduler sẽ đọc và publish lên Kafka sau.
     */
    private void savePaymentEventToOutbox(Payment payment, String eventType) {
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .build();
        outboxEventService.saveEvent("Payment", payment.getId().toString(), eventType, event);
    }
}
