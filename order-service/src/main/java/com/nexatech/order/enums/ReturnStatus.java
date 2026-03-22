package com.nexatech.order.enums;

/**
 * Trạng thái yêu cầu trả hàng.
 */
public enum ReturnStatus {
    /** Khách hàng vừa gửi yêu cầu, chờ admin xem xét. */
    PENDING,
    /** Admin đã duyệt, đang tiến hành hoàn tiền. */
    APPROVED,
    /** Admin từ chối yêu cầu trả hàng. */
    REJECTED,
    /** Hoàn tiền thành công, luồng kết thúc. */
    COMPLETED
}
