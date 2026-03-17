package com.nexatech.order.service;

import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.dto.CreateOrderRequest;
import com.nexatech.order.dto.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    public OrderResponse createOrder(UUID customerId, CreateOrderRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public OrderResponse getOrder(UUID orderId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<OrderResponse> getMyOrders(UUID customerId, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void cancelOrder(UUID customerId, UUID orderId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
