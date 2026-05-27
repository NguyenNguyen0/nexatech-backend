package com.nexatech.payment.service;

import com.nexatech.payment.config.VNPayConfig;
import com.nexatech.payment.dto.VNPayPaymentRequest;
import com.nexatech.payment.dto.VNPayResponse;
import com.nexatech.payment.enums.PaymentMethod;
import com.nexatech.payment.enums.PaymentStatus;
import com.nexatech.payment.event.PaymentEvent;
import com.nexatech.payment.event.PaymentEventProducer;
import com.nexatech.payment.exception.BadRequestException;
import com.nexatech.payment.model.Payment;
import com.nexatech.payment.repository.PaymentRepository;
import com.nexatech.payment.util.VNPayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayService {

    private final VNPayConfig vnPayConfig;
    private final PaymentRepository paymentRepository;
    private final OutboxEventService outboxEventService;
    private final AuditLogService auditLogService;
    private final SnapshotService snapshotService;
    private final PaymentEventProducer paymentEventProducer;

    /**
     * Tạo URL thanh toán VNPay và lưu giao dịch với trạng thái PENDING.
     * Payment ID (không dấu gạch ngang) được dùng làm vnp_TxnRef.
     */
    @Transactional
    public VNPayResponse createPaymentUrl(UUID customerId, VNPayPaymentRequest request) {
        // Kiểm tra đơn hàng đã có giao dịch VNPay đang chờ xử lý chưa
        paymentRepository.findByOrderId(request.getOrderId()).ifPresent(p -> {
            if (p.getMethod() == PaymentMethod.VNPAY && p.getStatus() == PaymentStatus.PENDING) {
                throw new BadRequestException(
                        "Đơn hàng " + request.getOrderId() + " đã có giao dịch VNPay đang chờ thanh toán");
            }
        });

        // Tạo bản ghi thanh toán nội bộ với trạng thái PENDING
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(customerId)
                .amount(request.getAmount())
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .build();
        Payment saved = paymentRepository.save(payment);

        // Dùng UUID không dấu gạch ngang làm vnp_TxnRef để dễ tra cứu trong IPN
        String txnRef = saved.getId().toString().replace("-", "");
        saved.setTransactionId(txnRef);
        paymentRepository.save(saved);

        String paymentUrl = buildVNPayUrl(txnRef, request);

        log.info("Tạo URL VNPay thành công: paymentId={}, orderId={}", saved.getId(), request.getOrderId());
        return VNPayResponse.builder()
                .paymentId(saved.getId())
                .paymentUrl(paymentUrl)
                .build();
    }

    /**
     * Xử lý IPN (Instant Payment Notification) từ VNPay gọi server-to-server.
     * Phải trả về {"RspCode":"00","Message":"Confirm Success"} trong 5 giây nếu hợp lệ.
     * VNPay sẽ gọi lại tối đa 3 lần nếu không nhận được response 00.
     */
    @Transactional
    public Map<String, String> processIpn(Map<String, String> params) {
        Map<String, String> result = new LinkedHashMap<>();

        // Tách vnp_SecureHash và vnp_SecureHashType ra trước khi tính hash
        // VNPay v2.1.0 có thể gửi thêm vnp_SecureHashType (vd: SHA512) trong callback
        String receivedHash = params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        // Tính lại chữ ký từ params còn lại
        String hashData = VNPayUtil.buildHashData(params);
        String expectedHash = VNPayUtil.hmacSha512(vnPayConfig.getHashSecret(), hashData);

        if (!expectedHash.equalsIgnoreCase(receivedHash)) {
            log.warn("IPN: chữ ký không hợp lệ — có thể bị giả mạo");
            result.put("RspCode", "97");
            result.put("Message", "Invalid signature");
            return result;
        }

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");

        // Tìm giao dịch theo txnRef
        Payment payment = paymentRepository.findByTransactionId(txnRef).orElse(null);

        if (payment == null) {
            log.warn("IPN: không tìm thấy giao dịch với txnRef={}", txnRef);
            result.put("RspCode", "01");
            result.put("Message", "Order not found");
            return result;
        }

        // Idempotent check: tránh xử lý trùng lặp nếu VNPay gọi lại
        if (payment.getStatus() != PaymentStatus.PENDING) {
            result.put("RspCode", "02");
            result.put("Message", "Order already confirmed");
            return result;
        }

        if ("00".equals(responseCode)) {
            // Thanh toán thành công
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
            savePaymentEventToOutbox(payment, "PAYMENT_COMPLETED");
            auditLogService.logEvent("VNPAY_PAYMENT_COMPLETED", "Payment", payment.getId().toString(),
                    Map.of("paymentId", payment.getId().toString(),
                            "orderId", payment.getOrderId().toString(),
                            "txnRef", txnRef,
                            "newStatus", "COMPLETED"),
                    "vnpay");
            snapshotService.takeSnapshot("Payment", payment.getId(),
                    Map.of("paymentId", payment.getId().toString(),
                            "orderId", payment.getOrderId().toString(),
                            "status", "COMPLETED",
                            "method", "VNPAY",
                            "txnRef", txnRef,
                            "snapshotReason", "vnpay_completed"));
            log.info("IPN: thanh toán VNPay thành công — orderId={}", payment.getOrderId());
        } else {
            // Thanh toán thất bại (user hủy / hết hạn / lỗi ngân hàng)
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("VNPay response code: " + responseCode);
            paymentRepository.save(payment);
            savePaymentEventToOutbox(payment, "PAYMENT_FAILED");
            auditLogService.logEvent("VNPAY_PAYMENT_FAILED", "Payment", payment.getId().toString(),
                    Map.of("paymentId", payment.getId().toString(),
                            "orderId", payment.getOrderId().toString(),
                            "responseCode", responseCode,
                            "reason", "vnpay_failure"),
                    "vnpay");
            log.warn("IPN: thanh toán VNPay thất bại — responseCode={}, orderId={}", responseCode, payment.getOrderId());
        }

        result.put("RspCode", "00");
        result.put("Message", "Confirm Success");
        return result;
    }

    /**
     * Xử lý return URL — VNPay redirect trình duyệt sau khi thanh toán xong.
     * Cũng cập nhật trạng thái payment nếu IPN chưa được xử lý (fallback cho môi trường dev/local).
     */
    @Transactional
    public Map<String, String> processReturn(Map<String, String> params) {
        // Clone để không làm thay đổi params gốc (processIpn cần params nguyên bản)
        Map<String, String> cloned = new LinkedHashMap<>(params);

        String receivedHash = cloned.remove("vnp_SecureHash");
        cloned.remove("vnp_SecureHashType");
        String hashData = VNPayUtil.buildHashData(cloned);
        String expectedHash = VNPayUtil.hmacSha512(vnPayConfig.getHashSecret(), hashData);

        boolean valid = expectedHash.equalsIgnoreCase(receivedHash);

        Map<String, String> result = new LinkedHashMap<>(cloned);
        result.put("signatureValid", String.valueOf(valid));
        result.put("success", valid && "00".equals(cloned.get("vnp_ResponseCode")) ? "true" : "false");

        // Fallback: cập nhật DB nếu IPN chưa xử lý (status vẫn PENDING)
        if (valid) {
            String txnRef = cloned.get("vnp_TxnRef");
            String responseCode = cloned.get("vnp_ResponseCode");
            paymentRepository.findByTransactionId(txnRef).ifPresent(payment -> {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    if ("00".equals(responseCode)) {
                        payment.setStatus(PaymentStatus.COMPLETED);
                        paymentRepository.save(payment);
                        savePaymentEventToOutbox(payment, "PAYMENT_COMPLETED");
                        log.info("Return URL fallback: thanh toán VNPay thành công — orderId={}", payment.getOrderId());
                        // Publish ngay lập tức sau khi transaction commit — không chờ outbox scheduler
                        PaymentEvent immediateEvent = buildPaymentEvent(payment, "PAYMENT_COMPLETED");
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    paymentEventProducer.publishPaymentEvent(immediateEvent);
                                    log.info("Return fallback: đã publish PAYMENT_COMPLETED ngay tức thì — orderId={}", payment.getOrderId());
                                } catch (Exception ex) {
                                    log.warn("Return fallback: immediate Kafka publish thất bại (outbox sẽ retry) — orderId={} error={}", payment.getOrderId(), ex.getMessage());
                                }
                            }
                        });
                    } else {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason("VNPay return code: " + responseCode);
                        paymentRepository.save(payment);
                        savePaymentEventToOutbox(payment, "PAYMENT_FAILED");
                        log.warn("Return URL fallback: thanh toán VNPay thất bại — responseCode={}, orderId={}", responseCode, payment.getOrderId());
                    }
                }
            });
        }

        return result;
    }

    /**
     * Tra cứu orderId từ txnRef (payment UUID không dấu gạch ngang).
     * Dùng khi xử lý redirect về frontend sau thanh toán VNPAY.
     */
    public String getOrderIdByTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) return null;
        return paymentRepository.findByTransactionId(txnRef)
                .map(p -> p.getOrderId().toString())
                .orElse(null);
    }

    // ==================== Private helpers ====================

    /**
     * Xây dựng URL redirect sang VNPay với đầy đủ tham số và chữ ký.
     * VNPay yêu cầu amount = giá trị VND * 100 (không có thập phân).
     */
    private String buildVNPayUrl(String txnRef, VNPayPaymentRequest request) {
        long vnpAmount = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        // Dùng LinkedHashMap để giữ thứ tự khi debug, nhưng VNPayUtil.buildHashData sẽ sort lại
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        params.put("vnp_Amount", String.valueOf(vnpAmount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", request.getOrderInfo());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        params.put("vnp_IpAddr",
                (request.getIpAddress() != null && !request.getIpAddress().isBlank())
                        ? request.getIpAddress() : "127.0.0.1");
        params.put("vnp_CreateDate", VNPayUtil.getNow());
        params.put("vnp_ExpireDate", VNPayUtil.getExpireTime(15));

        // Tính chữ ký và thêm vào params
        String secureHash = VNPayUtil.hmacSha512(
                vnPayConfig.getHashSecret(),
                VNPayUtil.buildHashData(params));
        params.put("vnp_SecureHash", secureHash);

        return vnPayConfig.getPaymentUrl() + "?" + VNPayUtil.buildQueryString(params);
    }

    private void savePaymentEventToOutbox(Payment payment, String eventType) {
        PaymentEvent event = buildPaymentEvent(payment, eventType);
        outboxEventService.saveEvent("Payment", payment.getId().toString(), eventType, event);
    }

    private PaymentEvent buildPaymentEvent(Payment payment, String eventType) {
        return PaymentEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .build();
    }
}
