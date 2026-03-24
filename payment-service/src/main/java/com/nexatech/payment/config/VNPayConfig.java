package com.nexatech.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình VNPay — giá trị được nạp từ config-server (payment-service.yml).
 * Biến môi trường: VNPAY_TMN_CODE, VNPAY_HASH_SECRET
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vnpay")
public class VNPayConfig {

    /** Mã định danh merchant trên hệ thống VNPay (TMN Code). */
    private String tmnCode;

    /** Khóa bí mật để tính chữ ký HMAC-SHA512. Không được commit lên git! */
    private String hashSecret;

    /** URL cổng thanh toán VNPay (sandbox hoặc production). */
    private String paymentUrl;

    /** URL VNPay redirect trình duyệt sau khi hoàn tất thanh toán. */
    private String returnUrl;

    /** URL VNPay gọi server-to-server để thông báo kết quả (IPN). */
    private String ipnUrl;
}
