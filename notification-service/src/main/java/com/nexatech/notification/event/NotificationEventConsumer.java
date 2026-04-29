package com.nexatech.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
            if (!"REGISTERED".equals(event.getEventType())) return;

            UUID userId = UUID.fromString(event.getUserId());
            String name = event.getDisplayName() != null ? event.getDisplayName() : "there";

            Notification notification = notificationService.createNotification(
                    userId,
                    Notification.NotificationType.WELCOME,
                    "Welcome to NexaTech!",
                    "Hi " + name + ", your account has been created successfully."
            );

            if (event.getEmail() != null
                    && preferenceService.isEmailEnabled(userId, Notification.NotificationType.WELCOME)) {
                sendEmailWithRetrySupport(notification, event.getEmail(),
                        "welcome", "Welcome to NexaTech!",
                        Map.of("displayName", name));
            }
            log.info("Processed REGISTERED event for userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.events", groupId = "notification-order-group")
    public void handleOrderEvent(String message) {
        if (message == null) return;
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            UUID customerId = UUID.fromString(event.getCustomerId());
            String orderRef = event.getOrderNumber() != null ? event.getOrderNumber() : event.getOrderId();

            switch (event.getStatus()) {
                case "PENDING", "ORDER_PLACED", "CONFIRMED" -> {
                    Notification notification = notificationService.createNotification(
                            customerId,
                            Notification.NotificationType.ORDER_CONFIRMATION,
                            "Order Confirmed",
                            "Your order #" + orderRef + " has been placed successfully."
                    );
                    if (event.getCustomerEmail() != null && event.getTotalAmount() != null
                            && preferenceService.isEmailEnabled(customerId, Notification.NotificationType.ORDER_CONFIRMATION)) {
                        sendEmailWithRetrySupport(notification, event.getCustomerEmail(),
                                "order-confirmation", "Order Confirmed – #" + orderRef,
                                Map.of("orderId", orderRef, "total", event.getTotalAmount()));
                    }
                }
                case "CANCELLED" -> notificationService.createNotification(
                        customerId,
                        Notification.NotificationType.ORDER_STATUS_UPDATE,
                        "Order Cancelled",
                        "Your order #" + orderRef + " has been cancelled."
                );
                case "DELIVERED" -> notificationService.createNotification(
                        customerId,
                        Notification.NotificationType.ORDER_STATUS_UPDATE,
                        "Order Delivered",
                        "Your order #" + orderRef + " has been delivered successfully."
                );
                default -> log.debug("Unhandled order status: {}", event.getStatus());
            }
            log.info("Processed order event status={} orderId={}", event.getStatus(), event.getOrderId());
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
}
