package com.nexatech.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromAddress;

    private void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
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
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }

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
}
