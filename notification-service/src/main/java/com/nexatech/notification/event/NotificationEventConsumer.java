package com.nexatech.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.notification.event.dto.InventoryEvent;
import com.nexatech.notification.event.dto.OrderEvent;
import com.nexatech.notification.event.dto.PaymentEvent;
import com.nexatech.notification.event.dto.UserRegisteredEvent;
import com.nexatech.notification.model.Notification;
import com.nexatech.notification.service.EmailService;
import com.nexatech.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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

    @KafkaListener(topics = "user.events", groupId = "notification-user-group")
    public void handleUserEvent(String message) {
        if (message == null) return;
        try {
            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
            String eventType = event.getEventType();
            if (eventType == null) return;

            UUID userId = UUID.fromString(event.getUserId());
            String name = event.getDisplayName() != null ? event.getDisplayName() : "there";

            if ("REGISTERED".equals(eventType)) {
                notificationService.createNotification(
                        userId,
                        Notification.NotificationType.WELCOME,
                        "Welcome to NexaTech!",
                        "Hi " + name + ", your account has been created successfully."
                );

                if (event.getEmail() != null) {
                    emailService.sendWelcomeEmail(event.getEmail(), name);
                }
            } else if ("EMAIL_OTP_REQUIRED".equals(eventType)) {
                notificationService.createNotification(
                        userId,
                        Notification.NotificationType.GENERAL,
                        "Email verification required",
                        "Your email is not verified yet. Please use OTP sent to your email."
                );
                if (event.getEmail() != null && event.getOtpCode() != null) {
                    emailService.sendEmailVerificationOtp(
                            event.getEmail(),
                            name,
                            event.getOtpCode(),
                            event.getExpiresInSeconds() != null ? event.getExpiresInSeconds() : 300L
                    );
                }
            } else if ("EMAIL_VERIFIED".equals(eventType)) {
                notificationService.createNotification(
                        userId,
                        Notification.NotificationType.GENERAL,
                        "Email verified",
                        "Your email has been verified successfully."
                );
            }
            log.info("Processed user event type={} for userId={}", eventType, userId);
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
                    notificationService.createNotification(
                            customerId,
                            Notification.NotificationType.ORDER_CONFIRMATION,
                            "Order Confirmed",
                            "Your order #" + orderRef + " has been placed successfully."
                    );
                    if (event.getCustomerEmail() != null && event.getTotalAmount() != null) {
                        emailService.sendOrderConfirmationEmail(
                                event.getCustomerEmail(), orderRef, event.getTotalAmount());
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
                    notificationService.createNotification(
                            customerId,
                            Notification.NotificationType.PAYMENT_CONFIRMATION,
                            "Payment Received",
                            "Your payment of $" + event.getAmount() + " has been processed successfully."
                    );
                    if (event.getCustomerEmail() != null && event.getPaymentId() != null) {
                        emailService.sendPaymentReceiptEmail(
                                event.getCustomerEmail(), event.getPaymentId(), event.getAmount());
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
}
