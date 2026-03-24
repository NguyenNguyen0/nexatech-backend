package com.nexatech.order.service;

import com.nexatech.order.dto.CreateReturnRequest;
import com.nexatech.order.dto.ReturnResponse;
import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.enums.ReturnStatus;
import com.nexatech.order.event.ReturnEvent;
import com.nexatech.order.exception.BadRequestException;
import com.nexatech.order.exception.ForbiddenException;
import com.nexatech.order.exception.ResourceNotFoundException;
import com.nexatech.order.mapper.ReturnMapper;
import com.nexatech.order.model.Order;
import com.nexatech.order.model.OrderReturn;
import com.nexatech.order.model.OrderReturnItem;
import com.nexatech.order.repository.OrderRepository;
import com.nexatech.order.repository.OrderReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xử lý nghiệp vụ trả hàng và hoàn tiền.
 *
 * Luồng:
 *   1. Khách tạo yêu cầu (createReturn) — chỉ với đơn DELIVERED
 *   2. Admin duyệt (approveReturn) → order chuyển sang RETURNED + publish ORDER_RETURN_APPROVED
 *   3. payment-service nhận event → xử lý hoàn tiền → publish PAYMENT_REFUNDED
 *   4. order-service nhận PAYMENT_REFUNDED → order chuyển sang REFUNDED (xử lý bởi OrderPaymentEventConsumer)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnService {

    private final OrderReturnRepository returnRepository;
    private final OrderRepository orderRepository;
    private final ReturnMapper returnMapper;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    /**
     * Tạo yêu cầu trả hàng — chỉ áp dụng cho đơn hàng đã giao (DELIVERED).
     * Mỗi đơn hàng chỉ được tạo duy nhất một yêu cầu trả hàng.
     */
    @Transactional
    public ReturnResponse createReturn(UUID customerId, UUID orderId, CreateReturnRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));

        // Chỉ khách hàng sở hữu đơn mới được tạo yêu cầu trả
        if (!order.getCustomerId().equals(customerId)) {
            throw new ForbiddenException("Bạn không có quyền thực hiện thao tác này");
        }

        // Chỉ đơn đã giao (DELIVERED) mới có thể trả hàng
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException(
                    "Chỉ có thể yêu cầu trả hàng cho đơn hàng đã giao (DELIVERED). " +
                    "Trạng thái hiện tại: " + order.getStatus()
            );
        }

        // Mỗi đơn chỉ được gửi một yêu cầu trả hàng
        if (returnRepository.findByOrderId(orderId).isPresent()) {
            throw new BadRequestException("Đơn hàng này đã có yêu cầu trả hàng đang xử lý");
        }

        // Map danh sách sản phẩm cần trả
        List<OrderReturnItem> returnItems = request.getItems().stream()
                .map(item -> OrderReturnItem.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .itemReason(item.getReason())
                        .build())
                .collect(Collectors.toList());

        OrderReturn orderReturn = OrderReturn.builder()
                .orderId(orderId)
                .customerId(customerId)
                .reason(request.getReason())
                .status(ReturnStatus.PENDING)
                .items(returnItems)
                .build();

        // Gắn quan hệ ngược OrderReturnItem → OrderReturn (cần cho cascade save)
        returnItems.forEach(item -> item.setOrderReturn(orderReturn));

        OrderReturn saved = returnRepository.save(orderReturn);
        log.info("Tạo yêu cầu trả hàng: returnId={}, orderId={}", saved.getId(), orderId);

        // Thông báo cho admin (notification-service lắng nghe order.events)
        publishReturnEvent(saved, "ORDER_RETURN_REQUESTED", order.getTotalAmount());

        return returnMapper.toResponse(saved);
    }

    /**
     * Admin duyệt yêu cầu trả hàng:
     *  - Cập nhật returnStatus → APPROVED
     *  - Cập nhật orderStatus → RETURNED
     *  - Publish ORDER_RETURN_APPROVED → payment-service xử lý hoàn tiền
     */
    @Transactional
    public ReturnResponse approveReturn(UUID returnId) {
        OrderReturn orderReturn = findById(returnId);

        if (orderReturn.getStatus() != ReturnStatus.PENDING) {
            throw new BadRequestException(
                    "Chỉ có thể duyệt yêu cầu ở trạng thái PENDING. Hiện tại: " + orderReturn.getStatus()
            );
        }

        orderReturn.setStatus(ReturnStatus.APPROVED);
        OrderReturn saved = returnRepository.save(orderReturn);

        // Cập nhật trạng thái đơn hàng → RETURNED
        Order order = orderRepository.findById(orderReturn.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn hàng: " + orderReturn.getOrderId()));
        order.setStatus(OrderStatus.RETURNED);
        Order savedOrder = orderRepository.save(order);

        log.info("Duyệt trả hàng: returnId={}, orderId={}", returnId, orderReturn.getOrderId());

        // Trigger hoàn tiền qua payment-service
        publishReturnEvent(saved, "ORDER_RETURN_APPROVED", savedOrder.getTotalAmount());

        return returnMapper.toResponse(saved);
    }

    /**
     * Admin từ chối yêu cầu trả hàng, ghi lý do vào adminNote.
     * Publish ORDER_RETURN_REJECTED để notification-service thông báo cho khách.
     */
    @Transactional
    public ReturnResponse rejectReturn(UUID returnId, String adminNote) {
        OrderReturn orderReturn = findById(returnId);

        if (orderReturn.getStatus() != ReturnStatus.PENDING) {
            throw new BadRequestException(
                    "Chỉ có thể từ chối yêu cầu ở trạng thái PENDING. Hiện tại: " + orderReturn.getStatus()
            );
        }

        orderReturn.setStatus(ReturnStatus.REJECTED);
        orderReturn.setAdminNote(adminNote);
        OrderReturn saved = returnRepository.save(orderReturn);

        log.info("Từ chối trả hàng: returnId={}, adminNote: {}", returnId, adminNote);
        publishReturnEvent(saved, "ORDER_RETURN_REJECTED", BigDecimal.ZERO);

        return returnMapper.toResponse(saved);
    }

    public ReturnResponse getReturn(UUID returnId) {
        return returnMapper.toResponse(findById(returnId));
    }

    public Page<ReturnResponse> getMyReturns(UUID customerId, Pageable pageable) {
        return returnRepository.findByCustomerId(customerId, pageable)
                .map(returnMapper::toResponse);
    }

    // ==================== Private helpers ====================

    private OrderReturn findById(UUID returnId) {
        return returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu trả hàng: " + returnId));
    }

    /**
     * Publish ReturnEvent lên topic "order.events".
     * payment-service lắng nghe và xử lý khi eventType = ORDER_RETURN_APPROVED.
     * notification-service có thể lắng nghe ORDER_RETURN_REQUESTED / ORDER_RETURN_REJECTED.
     */
    private void publishReturnEvent(OrderReturn orderReturn, String eventType, BigDecimal refundAmount) {
        try {
            ReturnEvent event = ReturnEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .timestamp(Instant.now())
                    .returnId(orderReturn.getId())
                    .orderId(orderReturn.getOrderId())
                    .customerId(orderReturn.getCustomerId())
                    .status(orderReturn.getStatus())
                    .reason(orderReturn.getReason())
                    .refundAmount(refundAmount)
                    .build();
            kafkaTemplate.send("order.events", orderReturn.getOrderId().toString(), event);
        } catch (Exception e) {
            log.warn("Không thể publish Kafka event [{}] cho returnId {}: {}",
                    eventType, orderReturn.getId(), e.getMessage());
        }
    }
}
