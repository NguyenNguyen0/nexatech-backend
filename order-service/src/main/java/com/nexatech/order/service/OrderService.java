package com.nexatech.order.service;

import com.nexatech.order.client.InventoryClient;
import com.nexatech.order.client.ProductClient;
import com.nexatech.order.client.dto.ProductInfo;
import com.nexatech.order.client.dto.StockOperationRequest;
import com.nexatech.order.dto.CreateOrderRequest;
import com.nexatech.order.dto.OrderResponse;
import com.nexatech.order.dto.UpdateShippingRequest;
import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.event.OrderEvent;
import com.nexatech.order.event.OrderEventProducer;
import com.nexatech.order.exception.BadRequestException;
import com.nexatech.order.exception.ForbiddenException;
import com.nexatech.order.exception.ResourceNotFoundException;
import com.nexatech.order.mapper.OrderMapper;
import com.nexatech.order.model.Order;
import com.nexatech.order.model.OrderItem;
import com.nexatech.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;
    private final OrderEventProducer orderEventProducer;

    /**
     * Tạo đơn hàng mới.
     *
     * Luồng xử lý:
     *   1. Kiểm tra tồn kho qua inventory-service (Feign)
     *   2. Lấy giá sản phẩm từ product-service (Feign)
     *   3. Lưu Order + OrderItems vào DB
     *   4. Giữ hàng (reserve) — nếu bước này thất bại, @Transactional tự rollback đơn hàng,
     *      tránh tạo ra đơn hàng "mồ côi" không có hàng giữ tương ứng.
     *   5. Publish OrderEvent (ORDER_PLACED) lên Kafka
     */
    @Transactional
    public OrderResponse createOrder(UUID customerId, CreateOrderRequest request) {
        // 1. Xây dựng request và kiểm tra tồn kho
        StockOperationRequest stockRequest = buildStockRequest(request.getItems());
        Boolean inStock = inventoryClient.checkStock(stockRequest).getData();
        if (!Boolean.TRUE.equals(inStock)) {
            throw new BadRequestException("Một hoặc nhiều sản phẩm không đủ tồn kho");
        }

        // 2. Lấy thông tin sản phẩm và xây dựng danh sách OrderItem
        List<OrderItem> items = request.getItems().stream()
                .map(this::buildOrderItem)
                .collect(Collectors.toList());

        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Tạo đơn hàng và lưu vào DB
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(customerId)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .shippingFee(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .shippingAddress(request.getShippingAddress())
                .shippingMethod(request.getShippingMethod() != null ? request.getShippingMethod() : "STANDARD")
                .paymentMethod(request.getPaymentMethod())
                .note(request.getNote())
                .items(items)
                .build();

        // Thiết lập quan hệ hai chiều @ManyToOne (JPA yêu cầu)
        items.forEach(item -> item.setOrder(order));
        Order saved = orderRepository.save(order);

        // 4. Giữ hàng — nếu FeignException (RuntimeException) được throw,
        //    @Transactional sẽ rollback cả đơn hàng vừa lưu ở trên
        inventoryClient.reserveStock(stockRequest);

        // 5. Publish event sau khi DB và tồn kho đều thành công
        publishEvent(saved, "ORDER_PLACED");

        log.info("Tạo đơn hàng thành công: {} | customer: {}", saved.getOrderNumber(), customerId);
        return orderMapper.toResponse(saved);
    }

    /**
     * Lấy chi tiết một đơn hàng theo ID.
     */
    public OrderResponse getOrder(UUID orderId) {
        return orderMapper.toResponse(findById(orderId));
    }

    /**
     * Lấy danh sách đơn hàng của customer (phân trang).
     */
    public Page<OrderResponse> getMyOrders(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable)
                .map(orderMapper::toResponse);
    }

    /**
     * Lấy toàn bộ đơn hàng trong hệ thống (dành cho admin, phân trang).
     */
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse);
    }

    /**
     * Cập nhật trạng thái đơn hàng (admin thực hiện).
     * Tự động gán timestamp vận chuyển khi status chuyển sang SHIPPED / DELIVERED.
     */
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = findById(orderId);
        OrderStatus oldStatus = order.getStatus();

        order.setStatus(newStatus);

        // Tự động ghi nhận thời điểm gửi hàng và giao hàng thành công
        if (newStatus == OrderStatus.SHIPPED && order.getShippedAt() == null) {
            order.setShippedAt(Instant.now());
        } else if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(Instant.now());
        }

        Order saved = orderRepository.save(order);
        publishEvent(saved, "ORDER_STATUS_UPDATED");
        log.info("Cập nhật trạng thái đơn hàng {} : {} → {}", orderId, oldStatus, newStatus);

        return orderMapper.toResponse(saved);
    }

    /**
     * Cập nhật thông tin tracking vận chuyển (admin thực hiện sau khi bàn giao cho đơn vị vận chuyển).
     */
    @Transactional
    public OrderResponse updateShipping(UUID orderId, UpdateShippingRequest request) {
        Order order = findById(orderId);
        order.setTrackingNumber(request.getTrackingNumber());
        order.setCarrierName(request.getCarrierName());
        order.setEstimatedDelivery(request.getEstimatedDelivery());
        Order saved = orderRepository.save(order);
        log.info("Cập nhật tracking đơn hàng {}: {} / {}", orderId, request.getCarrierName(), request.getTrackingNumber());
        return orderMapper.toResponse(saved);
    }

    /**
     * Customer tự huỷ đơn hàng.
     *
     * Luồng:
     *   1. Validate quyền sở hữu và trạng thái cho phép
     *   2. Giải phóng tồn kho đã giữ — nếu thất bại, đơn hàng giữ nguyên PENDING (an toàn)
     *   3. Cập nhật trạng thái CANCELLED và publish event
     */
    @Transactional
    public void cancelOrder(UUID customerId, UUID orderId) {
        Order order = findById(orderId);

        // Xác thực quyền sở hữu
        if (!order.getCustomerId().equals(customerId)) {
            throw new ForbiddenException("Bạn không có quyền huỷ đơn hàng này");
        }

        // Chỉ cho phép huỷ khi đơn đang ở PENDING
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException(
                    "Chỉ có thể huỷ đơn hàng ở trạng thái PENDING, trạng thái hiện tại: " + order.getStatus()
            );
        }

        // Giải phóng tồn kho — nếu thất bại thì dừng lại, đơn hàng vẫn PENDING (không mất hàng)
        try {
            inventoryClient.releaseStock(buildStockRequestFromOrder(order));
        } catch (Exception e) {
            log.error("Không thể giải phóng tồn kho cho đơn hàng {}: {}", orderId, e.getMessage());
            throw new BadRequestException("Không thể hoàn trả tồn kho, vui lòng thử lại sau");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        publishEvent(order, "ORDER_CANCELLED");
        log.info("Huỷ đơn hàng thành công: {} | customer: {}", orderId, customerId);
    }

    // ==================== Private helpers ====================

    private Order findById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    /**
     * Gọi product-service lấy thông tin và tính giá cho một dòng sản phẩm.
     */
    private OrderItem buildOrderItem(CreateOrderRequest.OrderItemRequest itemReq) {
        ProductInfo product = productClient.getProduct(itemReq.getProductId()).getData();
        if (product == null || !product.isActive()) {
            throw new BadRequestException("Sản phẩm không tồn tại hoặc đã ngừng bán: " + itemReq.getProductId());
        }
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
        return OrderItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .quantity(itemReq.getQuantity())
                .unitPrice(product.getPrice())
                .subtotal(subtotal)
                .build();
    }

    /**
     * Xây dựng StockOperationRequest từ danh sách items trong CreateOrderRequest.
     */
    private StockOperationRequest buildStockRequest(List<CreateOrderRequest.OrderItemRequest> items) {
        List<StockOperationRequest.Item> stockItems = items.stream()
                .map(i -> StockOperationRequest.Item.builder()
                        .productId(i.getProductId())
                        .quantity(i.getQuantity())
                        .build())
                .collect(Collectors.toList());
        return StockOperationRequest.builder().items(stockItems).build();
    }

    /**
     * Xây dựng StockOperationRequest từ các OrderItem đã lưu (dùng khi huỷ đơn).
     */
    private StockOperationRequest buildStockRequestFromOrder(Order order) {
        List<StockOperationRequest.Item> stockItems = order.getItems().stream()
                .map(item -> StockOperationRequest.Item.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());
        return StockOperationRequest.builder().items(stockItems).build();
    }

    /**
     * Publish OrderEvent lên Kafka.
     * Catch tất cả exception để tránh rollback DB vì lỗi Kafka.
     * TODO: thay bằng Outbox Pattern khi cần đảm bảo at-least-once delivery.
     */
    private void publishEvent(Order order, String eventType) {
        try {
            OrderEvent event = OrderEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .timestamp(Instant.now())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .customerId(order.getCustomerId())
                    .status(order.getStatus())
                    .totalAmount(order.getTotalAmount())
                    .items(order.getItems().stream()
                        .map(item -> OrderEvent.OrderItemEvent.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build())
                        .toList())
                    .build();
            orderEventProducer.publishOrderEvent(event);
        } catch (Exception e) {
            log.warn("Không thể publish Kafka event [{}] cho đơn hàng {}: {}",
                    eventType, order.getId(), e.getMessage());
        }
    }

    /**
     * Tạo mã đơn hàng duy nhất theo format: ORD-{timestamp}-{random 8 ký tự}.
     */
    private String generateOrderNumber() {
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + System.currentTimeMillis() + "-" + random;
    }
}
