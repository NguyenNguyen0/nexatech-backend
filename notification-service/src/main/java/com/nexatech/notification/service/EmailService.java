package com.nexatech.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.notification.event.dto.AuditLogEvent;
import com.nexatech.notification.model.Notification;
import com.nexatech.notification.service.audit.AuditEventPublisher;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final AuditEventPublisher auditEventPublisher;

    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * Core email sending method. Throws RuntimeException on failure so callers can handle it.
     */
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            String html = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Email sent to {} | subject: '{}'", to, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            Map<String, Object> meta = new HashMap<>();
            meta.put("subject", subject);
            meta.put("template", templateName);
            meta.put("error", e.getMessage());
            auditEventPublisher.publish(AuditLogEvent.builder()
                    .action("EMAIL_SEND_FAILED")
                    .entityType("Email")
                    .entityId(to)
                    .entityLabel(subject)
                    .actor("system")
                    .reason(e.getMessage())
                    .message("Email delivery failed for " + to)
                    .metadata(meta)
                    .build());
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }

    /**
     * Re-send an email using the metadata stored on the Notification entity.
     * Used by the retry scheduler.
     *
     * @throws RuntimeException if the email cannot be sent
     */
    public void sendEmailForNotification(Notification notification) {
        if (notification.getRecipientEmail() == null || notification.getEmailTemplateName() == null) {
            throw new RuntimeException("Cannot retry notification " + notification.getId()
                    + ": missing recipientEmail or emailTemplateName");
        }

        Map<String, Object> variables;
        try {
            variables = objectMapper.readValue(
                    notification.getEmailTemplateVariables(),
                    new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse template variables for notification "
                    + notification.getId(), e);
        }

        sendEmail(notification.getRecipientEmail(),
                notification.getSubject(),
                notification.getEmailTemplateName(),
                variables);
    }

    // ===== Convenience methods for specific email types =====

    public void sendWelcomeEmail(String to, String displayName) {
        Map<String, Object> variables = Map.of("displayName", displayName);
        sendEmail(to, "Welcome to NexaTech!", "welcome", variables);
    }

    public void sendEmailVerificationOtp(String to, String displayName, String otpCode, long expiresInSeconds) {
        Map<String, Object> variables = Map.of(
                "displayName", displayName,
                "otpCode", otpCode,
                "expiresInMinutes", Math.max(1L, expiresInSeconds / 60L)
        );
        sendEmail(to, "NexaTech Email Verification OTP", "email-otp", variables);
    }

    public void sendOrderConfirmationEmail(String to, String orderId, BigDecimal total) {
        Map<String, Object> variables = Map.of(
                "orderId", orderId,
                "total", total
        );
        sendEmail(to, "Order Confirmed – #" + orderId, "order-confirmation", variables);
    }

    public void sendPaymentReceiptEmail(String to, String paymentId, BigDecimal amount) {
        Map<String, Object> variables = Map.of(
                "paymentId", paymentId,
                "amount", amount
        );
        sendEmail(to, "Payment Receipt – #" + paymentId, "payment-receipt", variables);
    }

    public void sendEmailVerificationEmail(String to, String displayName, String verificationUrl) {
        Map<String, Object> variables = Map.of(
                "displayName", displayName,
                "verificationUrl", verificationUrl
        );
        sendEmail(to, "Verify Your Email — NexaTech", "email-verification", variables);
    }

    // ===== Helper to serialize template variables =====

    /**
     * Serialize a Map to JSON for storing in notification entity.
     */
    public String serializeVariables(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            log.error("Failed to serialize template variables: {}", e.getMessage());
            return "{}";
        }
    }
}
