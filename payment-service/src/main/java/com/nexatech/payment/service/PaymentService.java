package com.nexatech.payment.service;

import com.nexatech.payment.dto.CreatePaymentRequest;
import com.nexatech.payment.dto.PaymentResponse;
import com.nexatech.payment.enums.PaymentStatus;
import com.nexatech.payment.event.PaymentEvent;
import com.nexatech.payment.event.PaymentEventProducer;
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
    private final PaymentEventProducer paymentEventProducer;

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

        publishEvent(saved, "PAYMENT_INITIATED");
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

    public Page<PaymentResponse> getMyPayments(UUID customerId, Pageable pageable) {
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

        publishEvent(saved, "PAYMENT_REFUNDED");
        log.info("Hoàn tiền thành công: {} | đơn hàng: {}", paymentId, saved.getOrderId());

        return paymentMapper.toResponse(saved);
    }

    @Transactional
    public void confirmPayment(UUID orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING
                    || payment.getStatus() == PaymentStatus.PROCESSING) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(Instant.now());
                Payment saved = paymentRepository.save(payment);
                publishEvent(saved, "PAYMENT_COMPLETED");
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
                publishEvent(saved, "PAYMENT_COMPLETED");
                log.info("Xác nhận thanh toán COD thành công khi giao hàng, orderId={}", orderId);
            }
        });
    }

    @Transactional
    public void processReturnRefund(UUID orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                payment.setStatus(PaymentStatus.REFUNDED);
                Payment saved = paymentRepository.save(payment);
                publishEvent(saved, "PAYMENT_REFUNDED");
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

    private void publishEvent(Payment payment, String eventType) {
        try {
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
            paymentEventProducer.publishPaymentEvent(event);
        } catch (Exception e) {
            log.warn("Không thể publish Kafka event [{}] cho giao dịch {}: {}",
                    eventType, payment.getId(), e.getMessage());
        }
    }
}
