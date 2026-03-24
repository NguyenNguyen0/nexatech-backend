package com.nexatech.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response trả về sau khi tạo URL thanh toán VNPay thành công.
 */
@Data
@Builder
public class VNPayResponse {

    /** ID giao dịch nội bộ — dùng để tra cứu trạng thái sau này. */
    private UUID paymentId;

    /** URL redirect khách hàng tới trang thanh toán VNPay. */
    private String paymentUrl;
}
