package com.nexatech.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.notification.event.dto.EmailVerificationEvent;
import com.nexatech.notification.event.dto.ForgotPasswordEvent;
import com.nexatech.notification.event.dto.InventoryEvent;
import com.nexatech.notification.event.dto.OrderEvent;
import com.nexatech.notification.event.dto.PaymentEvent;
import com.nexatech.notification.event.dto.UserRegisteredEvent;
import com.nexatech.notification.model.Notification;
import com.nexatech.notification.repository.NotificationRepository;
import com.nexatech.notification.service.EmailService;
import com.nexatech.notification.service.NotificationPreferenceService;
import com.nexatech.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    @Value("${app.admin-user-id}")
    private UUID adminUserId;

    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationRepository notificationRepository;

    @KafkaListener(topics = "user.events", groupId = "notification-user-group")
    public void handleUserEvent(String message) {
        if (message == null) return;
        try {
            // Determine event type first for cross-service compatibility.
            var tree = objectMapper.readTree(message);
            String eventType = tree.has("eventType") ? tree.get("eventType").asText() : null;

            if ("EMAIL_VERIFICATION_REQUESTED".equals(eventType)) {
                handleEmailVerificationEvent(message);
                return;
            } else if ("FORGOT_PASSWORD".equals(eventType)) {
                handleForgotPasswordEvent(message);
                return;
            }

            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
            String normalizedEventType = event.getEventType();
            if (normalizedEventType == null) return;

            UUID userId = UUID.fromString(event.getUserId());
            String name = event.getDisplayName() != null ? event.getDisplayName() : "there";

            if ("REGISTERED".equals(normalizedEventType)) {
                Notification notification = notificationService.createNotification(
                        userId,
                        Notification.NotificationType.WELCOME,
                        "Welcome to NexaTech!",
                        "Hi " + name + ", your account has been created successfully."
                );

                if (event.getEmail() != null
                        && preferenceService.isEmailEnabled(userId, Notification.NotificationType.WELCOME)) {
                    sendEmailWithRetrySupport(
                            notification,
                            event.getEmail(),
                            "welcome",
                            "Welcome to NexaTech!",
                            Map.of("displayName", name)
                    );
                }
            } else if ("EMAIL_OTP_REQUIRED".equals(normalizedEventType)) {
                Notification notification = notificationService.createNotification(
                        userId,
                        Notification.NotificationType.GENERAL,
                        "Email verification required",
                        "Your email is not verified yet. Please use OTP sent to your email."
                );

                if (event.getEmail() != null
                        && event.getOtpCode() != null
                        && preferenceService.isEmailEnabled(userId, Notification.NotificationType.GENERAL)) {
                    sendEmailWithRetrySupport(
                            notification,
                            event.getEmail(),
                            "email-otp",
                            "NexaTech Email Verification OTP",
                            Map.of(
                                    "displayName", name,
                                    "otpCode", event.getOtpCode(),
                                    "expiresInMinutes", Math.max(1L, (event.getExpiresInSeconds() != null
                                            ? event.getExpiresInSeconds() : 300L) / 60L)
                            )
                    );
                }
            } else if ("EMAIL_VERIFIED".equals(normalizedEventType)) {
                notificationService.createNotification(
                        userId,
                        Notification.NotificationType.GENERAL,
                        "Email verified",
                        "Your email has been verified successfully."
                );
            }
            log.info("Processed user event type={} for userId={}", normalizedEventType, userId);
        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle email verification event from identity-service.
     * Renders email-verification template and sends verification email.
     */
    private void handleEmailVerificationEvent(String message) {
        try {
            EmailVerificationEvent event = objectMapper.readValue(message, EmailVerificationEvent.class);

            if (event.getEmail() == null || event.getVerificationUrl() == null) {
                log.warn("Email verification event missing email or verificationUrl, skipping");
                return;
            }

            UUID userId = event.getUserId() != null ? UUID.fromString(event.getUserId()) : adminUserId;
            String name = event.getDisplayName() != null ? event.getDisplayName() : "there";

            // Create in-app notification
            Notification notification = notificationService.createNotification(
                    userId,
                    Notification.NotificationType.EMAIL_VERIFICATION,
                    "Verify Your Email",
                    "Please verify your email address to activate your account."
            );

            // Send verification email with retry support
            sendEmailWithRetrySupport(notification, event.getEmail(),
                    "email-verification", "Verify Your Email — NexaTech",
                    Map.of("displayName", name, "verificationUrl", event.getVerificationUrl()));

            log.info("Processed EMAIL_VERIFICATION_REQUESTED event for userId={}, email={}",
                    userId, event.getEmail());
        } catch (Exception e) {
            log.error("Failed to process email verification event: {}", e.getMessage(), e);
        }
    }

    private void handleForgotPasswordEvent(String message) {
        try {
            ForgotPasswordEvent event = objectMapper.readValue(message, ForgotPasswordEvent.class);

            if (event.getEmail() == null || event.getNewPassword() == null) {
                log.warn("Forgot password event missing email or newPassword, skipping");
                return;
            }

            UUID userId = event.getUserId() != null ? UUID.fromString(event.getUserId()) : adminUserId;
            String name = event.getDisplayName() != null ? event.getDisplayName() : "there";

            // Create in-app notification
            Notification notification = notificationService.createNotification(
                    userId,
                    Notification.NotificationType.GENERAL,
                    "Mật khẩu của bạn đã được đặt lại",
                    "Hệ thống đã tạo mật khẩu mới cho bạn. Vui lòng kiểm tra email."
            );

            // Send email
            sendEmailWithRetrySupport(notification, event.getEmail(),
                    "forgot-password", "NexaTech — Mật khẩu mới của bạn",
                    Map.of(
                        "displayName", name, 
                        "newPassword", event.getNewPassword(),
                        "loginUrl", "http://localhost:3000/auth/login" // Using default frontend URL
                    ));

            log.info("Processed FORGOT_PASSWORD event for userId={}, email={}",
                    userId, event.getEmail());
        } catch (Exception e) {
            log.error("Failed to process forgot password event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.events", groupId = "notification-order-group")
    public void handleOrderEvent(String message) {
        if (message == null) return;
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            if (!"ORDER_SHIPMENT_CREATED".equals(event.getEventType())
                    || !"COD".equalsIgnoreCase(event.getPaymentMethod())) {
                log.debug("Order event ignored for email: type={} paymentMethod={} orderId={}",
                        event.getEventType(), event.getPaymentMethod(), event.getOrderId());
                return;
            }

            String recipientEmail = resolveOrderRecipientEmail(event);
            if (!StringUtils.hasText(recipientEmail)) {
                log.info("Skipping shipment-created email because buyer did not provide email: orderId={}",
                        event.getOrderId());
                return;
            }
            if (event.getTotalAmount() == null) {
                log.warn("Shipment-created order event missing totalAmount, skipping email: orderId={}",
                        event.getOrderId());
                return;
            }

            UUID recipientUserId = parseNullableUuid(
                    StringUtils.hasText(event.getRecipientUserId())
                            ? event.getRecipientUserId()
                            : event.getCustomerId());
            String orderRef = event.getOrderNumber() != null ? event.getOrderNumber() : event.getOrderId();

            if (recipientUserId != null) {
                Notification notification = notificationService.createNotification(
                        recipientUserId,
                        Notification.NotificationType.ORDER_CONFIRMATION,
                        "Shipment created",
                        "Your shipment for COD order #" + orderRef + " has been created."
                );

                if (preferenceService.isEmailEnabled(recipientUserId, Notification.NotificationType.ORDER_CONFIRMATION)) {
                    sendEmailWithRetrySupport(notification, recipientEmail,
                            "order-confirmation", "NexaTech - Đơn hàng đã tạo vận đơn #" + orderRef,
                            buildOrderEmailVariables(event, orderRef));
                }
            } else {
                emailService.sendEmail(recipientEmail,
                        "NexaTech - Đơn hàng đã tạo vận đơn #" + orderRef,
                        "order-confirmation",
                        buildOrderEmailVariables(event, orderRef));
                log.info("Sent shipment-created email for guest orderId={} recipient={}",
                        event.getOrderId(), recipientEmail);
            }

            log.info("Processed shipment-created order email event orderId={} recipientUserId={}",
                    event.getOrderId(), recipientUserId);
        } catch (Exception e) {
            log.error("Failed to process order event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.events", groupId = "notification-payment-group")
    public void handlePaymentEvent(String message) {
        if (message == null) return;
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            UUID customerId = UUID.fromString(event.getCustomerId());

            switch (event.getStatus()) {
                case "COMPLETED", "PAYMENT_COMPLETED" -> {
                    Notification notification = notificationService.createNotification(
                            customerId,
                            Notification.NotificationType.PAYMENT_CONFIRMATION,
                            "Payment Received",
                            "Your payment of $" + event.getAmount() + " has been processed successfully."
                    );
                    if (event.getCustomerEmail() != null && event.getPaymentId() != null
                            && preferenceService.isEmailEnabled(customerId, Notification.NotificationType.PAYMENT_CONFIRMATION)) {
                        sendEmailWithRetrySupport(notification, event.getCustomerEmail(),
                                "payment-receipt", "Payment Receipt – #" + event.getPaymentId(),
                                Map.of("paymentId", event.getPaymentId(), "amount", event.getAmount()));
                    }
                }
                case "REFUNDED" -> notificationService.createNotification(
                        customerId,
                        Notification.NotificationType.GENERAL,
                        "Refund Processed",
                        "Your refund of $" + event.getAmount() + " has been processed successfully."
                );
                case "FAILED" -> notificationService.createNotification(
                        customerId,
                        Notification.NotificationType.PAYMENT_FAILED,
                        "Payment Failed",
                        "Your payment could not be processed. Please try again or contact support."
                );
                default -> log.debug("Unhandled payment status: {}", event.getStatus());
            }
            log.info("Processed payment event status={} paymentId={}", event.getStatus(), event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "inventory.events", groupId = "notification-inventory-group")
    public void handleInventoryEvent(String message) {
        if (message == null) return;
        try {
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);
            if (!"LOW_STOCK".equals(event.getEventType())) {
                log.debug("Unhandled inventory event type: {}", event.getEventType());
                return;
            }

            String productLabel = event.getProductName() != null
                    ? event.getProductName()
                    : event.getProductId();

            String content = String.format(
                    "Sản phẩm '%s' sắp hết hàng (còn %d sản phẩm, ngưỡng tối thiểu: %d).",
                    productLabel, event.getQuantity(), event.getReorderLevel());

            notificationService.createNotification(
                    adminUserId,
                    Notification.NotificationType.LOW_STOCK_ALERT,
                    "Cảnh báo hàng tồn kho thấp",
                    content
            );
            log.info("Low-stock alert created for productId={} quantity={}",
                    event.getProductId(), event.getQuantity());
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage(), e);
        }
    }

    // ===== Private helper =====

    /**
     * Attempt to send email. On failure, mark the notification as FAILED with retry metadata
     * so the NotificationRetryScheduler can pick it up later.
     */
    private void sendEmailWithRetrySupport(Notification notification, String recipientEmail,
                                            String templateName, String subject,
                                            Map<String, Object> variables) {
        // Store email metadata on the notification for retry
        notification.setRecipientEmail(recipientEmail);
        notification.setEmailTemplateName(templateName);
        notification.setEmailTemplateVariables(emailService.serializeVariables(variables));
        notification.setChannel("EMAIL");

        try {
            emailService.sendEmail(recipientEmail, subject, templateName, variables);
            notificationRepository.save(notification);
        } catch (Exception e) {
            notification.setStatus(Notification.NotificationStatus.FAILED);
            notification.setFailureReason(e.getMessage());
            notificationRepository.save(notification);
            log.warn("Email send failed for notificationId={}, will be retried: {}",
                    notification.getId(), e.getMessage());
        }
    }

    private String resolveOrderRecipientEmail(OrderEvent event) {
        if (StringUtils.hasText(event.getRecipientEmail())) {
            return event.getRecipientEmail().trim();
        }
        return null;
    }

    private UUID parseNullableUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid notification recipient user id: {}", value);
            return null;
        }
    }

    private Map<String, Object> buildOrderEmailVariables(OrderEvent event, String orderRef) {
        BigDecimal subtotal = defaultAmount(event.getTotalAmount());
        BigDecimal shippingFee = defaultAmount(event.getShippingFee());
        BigDecimal discount = defaultAmount(event.getDiscount());
        BigDecimal grandTotal = subtotal.add(shippingFee).subtract(discount);

        List<Map<String, Object>> items = new ArrayList<>();
        if (event.getItems() != null) {
            for (OrderEvent.OrderItemEvent item : event.getItems()) {
                Map<String, Object> row = new HashMap<>();
                row.put("productName", StringUtils.hasText(item.getProductName()) ? item.getProductName() : item.getProductId());
                row.put("quantity", item.getQuantity());
                row.put("unitPrice", formatVnd(defaultAmount(item.getUnitPrice())));
                row.put("subtotal", formatVnd(defaultAmount(item.getSubtotal())));
                items.add(row);
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", orderRef);
        variables.put("buyerName", event.getGuestName());
        variables.put("buyerEmail", event.getRecipientEmail());
        variables.put("buyerPhone", event.getGuestPhone());
        variables.put("shippingAddress", event.getShippingAddress());
        variables.put("shippingMethod", event.getShippingMethod());
        variables.put("paymentMethod", event.getPaymentMethod());
        variables.put("carrierName", event.getCarrierName());
        variables.put("trackingNumber", event.getTrackingNumber());
        variables.put("note", event.getNote());
        variables.put("items", items);
        variables.put("subtotal", formatVnd(subtotal));
        variables.put("shippingFee", formatVnd(shippingFee));
        variables.put("discount", formatVnd(discount));
        variables.put("grandTotal", formatVnd(grandTotal));
        return variables;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatVnd(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        formatter.setMaximumFractionDigits(0);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(amount) + " ₫";
    }
}
