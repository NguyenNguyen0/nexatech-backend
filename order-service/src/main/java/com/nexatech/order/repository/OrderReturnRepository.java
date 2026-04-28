package com.nexatech.order.repository;

import com.nexatech.order.enums.ReturnStatus;
import com.nexatech.order.model.OrderReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {

    Optional<OrderReturn> findByOrderId(UUID orderId);

    Page<OrderReturn> findByCustomerId(UUID customerId, Pageable pageable);

    Page<OrderReturn> findByStatus(ReturnStatus status, Pageable pageable);
}
