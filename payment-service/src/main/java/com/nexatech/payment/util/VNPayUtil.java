package com.nexatech.payment.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tiện ích tích hợp cổng thanh toán VNPay.
 *
 * Quy tắc tính chữ ký (theo tài liệu VNPay):
 *   1. Lấy tất cả params (trừ vnp_SecureHash)
 *   2. Sắp xếp theo key (alphabetically)
 *   3. Nối thành chuỗi: key=URLEncode(value)&...
 *   4. Ký HMAC-SHA512 với hashSecret
 */
public class VNPayUtil {

    private static final DateTimeFormatter VN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Tính chữ ký HMAC-SHA512.
     *
     * @param key  khóa bí mật (hashSecret)
     * @param data chuỗi dữ liệu cần ký
     * @return chuỗi hex lowercase 128 ký tự
     */
    public static String hmacSha512(String key, String data) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Missing VNPay hash secret. Set VNPAY_HASH_SECRET before starting payment-service.");
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tính HMAC-SHA512", e);
        }
    }

    /**
     * Xây dựng chuỗi hashData để ký theo chuẩn VNPay:
     * key=URLEncode(value), sắp xếp alphabetically, nối bằng '&'.
     * Không encode key, chỉ encode value.
     */
    public static String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /**
     * Xây dựng query string URL (encode cả key và value).
     * Dùng để nối vào URL redirect.
     */
    public static String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    /** Lấy thời điểm hiện tại theo giờ Việt Nam, định dạng yyyyMMddHHmmss. */
    public static String getNow() {
        return ZonedDateTime.now(VN_ZONE).format(VN_DATE_FORMAT);
    }

    /** Tính thời gian hết hạn sau `minutes` phút, định dạng yyyyMMddHHmmss. */
    public static String getExpireTime(int minutes) {
        return ZonedDateTime.now(VN_ZONE).plusMinutes(minutes).format(VN_DATE_FORMAT);
    }
}
