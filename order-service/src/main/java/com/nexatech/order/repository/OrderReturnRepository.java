package com.nexatech.order.repository;

import com.nexatech.order.model.OrderReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {

    /** Kiểm tra đơn hàng này đã có yêu cầu trả hàng chưa. */
    Optional<OrderReturn> findByOrderId(UUID orderId);

    /** Lịch sử yêu cầu trả hàng của một khách hàng (phân trang). */
    Page<OrderReturn> findByCustomerId(UUID customerId, Pageable pageable);
}
