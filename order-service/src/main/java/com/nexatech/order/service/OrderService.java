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
import com.nexatech.order.exception.BadRequestException;
import com.nexatech.order.exception.ForbiddenException;
import com.nexatech.order.exception.ResourceNotFoundException;
import com.nexatech.order.mapper.OrderMapper;
import com.nexatech.order.model.Order;
import com.nexatech.order.model.OrderItem;
import com.nexatech.order.repository.OrderRepository;
import com.nexatech.order.util.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<OrderStatus> PURCHASED_STATUSES = EnumSet.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;
    private final OutboxEventService outboxEventService;
    private final AuditLogService auditLogService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Tạo đơn hàng mới.
     *
     * Luồng xử lý (tách Feign calls ra ngoài transaction để tránh long-held DB lock):
     *   1. Kiểm tra tồn kho qua inventory-service (Feign) — ngoài transaction
     *   2. Lấy giá sản phẩm từ product-service (Feign) — ngoài transaction
     *   3. Lưu Order + OrderItems + Outbox event trong một transaction ngắn gọn
     *   4. Giữ hàng (reserve) qua inventory-service (Feign) — ngoài transaction
     *      Nếu bước này thất bại, best-effort cancel đơn hàng vừa tạo.
     *
     * Anti-pattern được khắc phục: không gọi I/O mạng (Feign) trong @Transactional
     * để tránh giam lock DB trong thời gian chờ mạng.
     */
    public OrderResponse createOrder(UUID customerId, CreateOrderRequest request) {
        // 1. Xây dựng request và kiểm tra tồn kho (ngoài transaction)
        StockOperationRequest stockRequest = buildStockRequest(request.getItems());
        Boolean inStock = inventoryClient.checkStock(stockRequest).getData();
        if (!Boolean.TRUE.equals(inStock)) {
            throw new BadRequestException("Một hoặc nhiều sản phẩm không đủ tồn kho");
        }

        // 2. Lấy thông tin sản phẩm và xây dựng danh sách OrderItem (ngoài transaction)
        List<OrderItem> items = request.getItems().stream()
                .map(this::buildOrderItem)
                .collect(Collectors.toList());

        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
                .guestName(request.getGuestName())
                .guestEmail(request.getGuestEmail())
                .guestPhone(request.getGuestPhone())
                .items(items)
                .build();
        items.forEach(item -> item.setOrder(order));

        // 3. Lưu đơn hàng + outbox event trong transaction ngắn gọn (không có I/O mạng)
        Order saved = new TransactionTemplate(transactionManager).execute(status -> {
            Order o = orderRepository.save(order);
            saveOrderEventToOutbox(o, "ORDER_PLACED");
            auditLogService.log("ORDER_CREATED", "Order", o.getId().toString(),
                    "status=" + o.getStatus(), customerId != null ? customerId.toString() : "guest");
            return o;
        });

        // 4. Giữ hàng sau khi transaction DB đã commit — tránh orphan reservation
        try {
            inventoryClient.reserveStock(stockRequest);
        } catch (Exception e) {
            log.error("Giữ hàng thất bại cho đơn hàng {}: {}", saved.getId(), e.getMessage());
            // Best-effort: đánh dấu đơn hàng là CANCELLED để tránh đơn mồ côi
            try {
                new TransactionTemplate(transactionManager).execute(st -> {
                    Order o = orderRepository.findById(saved.getId()).orElse(null);
                    if (o != null && o.getStatus() == OrderStatus.PENDING) {
                        o.setStatus(OrderStatus.CANCELLED);
                        orderRepository.save(o);
                        saveOrderEventToOutbox(o, "ORDER_CANCELLED");
                    }
                    return null;
                });
            } catch (Exception ex) {
                log.error("Không thể rollback đơn hàng {}: {}", saved.getId(), ex.getMessage());
            }
            throw new BadRequestException("Không thể giữ hàng, vui lòng thử lại sau");
        }

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

    public boolean hasPurchasedProduct(UUID customerId, String productId) {
        return orderRepository.existsPurchasedProduct(customerId, productId, PURCHASED_STATUSES);
    }

    /**
     * Lấy toàn bộ đơn hàng trong hệ thống — dùng bởi admin, delegate sang getAllOrders(status, pageable).
     */
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return getAllOrders(null, pageable);
    }

    /**
     * Lấy danh sách đơn hàng theo trạng thái (admin, có thể null để lấy tất cả).
     */
    public Page<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByStatus(status, pageable).map(orderMapper::toResponse);
        }
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
    }

    /**
     * Cập nhật trạng thái đơn hàng (admin thực hiện).
     * Validate state machine trước khi cập nhật.
     * Tự động gán timestamp vận chuyển khi status chuyển sang SHIPPED / DELIVERED.
     */
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = findById(orderId);
        OrderStatus oldStatus = order.getStatus();

        OrderStateMachine.validate(oldStatus, newStatus);
        order.setStatus(newStatus);

        // Tự động ghi nhận thời điểm gửi hàng và giao hàng thành công
        if (newStatus == OrderStatus.SHIPPED && order.getShippedAt() == null) {
            order.setShippedAt(Instant.now());
        } else if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(Instant.now());
        }

        Order saved = orderRepository.save(order);
        saveOrderEventToOutbox(saved, "ORDER_STATUS_UPDATED");
        auditLogService.log("ORDER_STATUS_CHANGED", "Order", orderId.toString(),
                oldStatus.name(), newStatus.name(), "system", null);
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
        if (request.getShippingFee() != null) {
            order.setShippingFee(request.getShippingFee());
        }
        Order saved = orderRepository.save(order);
        log.info("Cập nhật tracking đơn hàng {}: {} / {}", orderId, request.getCarrierName(), request.getTrackingNumber());
        return orderMapper.toResponse(saved);
    }

    @Transactional
    public void publishShipmentCreatedEvent(UUID orderId) {
        Order order = findById(orderId);
        saveOrderEventToOutbox(order, "ORDER_SHIPMENT_CREATED");
    }

    /**
     * Customer tự huỷ đơn hàng.
     *
     * Luồng (tách Feign calls ra ngoài transaction):
     *   1. Validate quyền sở hữu và trạng thái cho phép (không cần transaction)
     *   2. Giải phóng tồn kho đã giữ — ngoài transaction
     *      Nếu thất bại, đơn hàng giữ nguyên PENDING (an toàn hơn là huỷ mà không nhả kho)
     *   3. Cập nhật trạng thái CANCELLED và lưu outbox event trong transaction ngắn gọn
     */
    public OrderResponse cancelOrder(UUID customerId, UUID orderId) {
        Order order = findById(orderId);

        // Xác thực quyền sở hữu (dùng Objects.equals để tránh NPE với guest orders)
        if (!Objects.equals(order.getCustomerId(), customerId)) {
            throw new ForbiddenException("Bạn không có quyền huỷ đơn hàng này");
        }

        // Cho phép huỷ khi đơn đang ở PENDING hoặc CONFIRMED
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException(
                    "Chỉ có thể huỷ đơn hàng ở trạng thái PENDING hoặc CONFIRMED, trạng thái hiện tại: " + order.getStatus()
            );
        }

        // Giải phóng tồn kho ngoài transaction — nếu thất bại, đơn hàng vẫn giữ nguyên (không mất hàng)
        try {
            inventoryClient.releaseStock(buildStockRequestFromOrder(order));
        } catch (Exception e) {
            log.error("Không thể giải phóng tồn kho cho đơn hàng {}: {}", orderId, e.getMessage());
            throw new BadRequestException("Không thể hoàn trả tồn kho, vui lòng thử lại sau");
        }

        // Cập nhật trạng thái trong transaction ngắn gọn (không có I/O mạng)
        return new TransactionTemplate(transactionManager).execute(status -> {
            Order o = findById(orderId);
            o.setStatus(OrderStatus.CANCELLED);
            Order saved = orderRepository.save(o);
            saveOrderEventToOutbox(saved, "ORDER_CANCELLED");
            auditLogService.log("ORDER_CANCELLED", "Order", orderId.toString(),
                    null, "CANCELLED", customerId != null ? customerId.toString() : "guest", "customer request");
            log.info("Huỷ đơn hàng thành công: {} | customer: {}", orderId, customerId);
            return orderMapper.toResponse(saved);
        });
    }

    // ==================== Private helpers ====================

    private Order findById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    /**
     * Gọi product-service lấy thông tin và tính giá cho một dòng sản phẩm.
     * Nếu product-service không khả dụng, dùng unitPrice từ frontend (localStorage cart) làm fallback.
     */
    private OrderItem buildOrderItem(CreateOrderRequest.OrderItemRequest itemReq) {
        BigDecimal price = null;
        String productName = null;
        String productId = itemReq.getProductId();

        try {
            ProductInfo product = productClient.getProduct(productId).getData();
            if (product != null && product.isActive()) {
                // Prefer client-provided unitPrice (may be flash sale / discounted price);
                // fall back to product-service price only when client sends nothing.
                price = (itemReq.getUnitPrice() != null && itemReq.getUnitPrice().compareTo(BigDecimal.ZERO) > 0)
                        ? itemReq.getUnitPrice()
                        : product.getPrice();
                productName = product.getName();
            } else if (product != null && !product.isActive()) {
                throw new BadRequestException("Sản phẩm không tồn tại hoặc đã ngừng bán: " + productId);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không thể lấy thông tin sản phẩm {} từ product-service ({}), dùng giá từ request", productId, e.getMessage());
        }

        // Fallback to client-provided price when product-service is unavailable
        if (price == null) {
            price = itemReq.getUnitPrice();
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Không thể xác định giá sản phẩm: " + productId);
        }
        if (productName == null) {
            productName = StringUtils.hasText(itemReq.getProductName())
                    ? itemReq.getProductName().trim()
                    : "Sản phẩm " + productId;
        }

        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
        return OrderItem.builder()
                .productId(productId)
                .productName(productName)
                .quantity(itemReq.getQuantity())
                .unitPrice(price)
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
     * Xây dựng OrderEvent và lưu vào outbox trong cùng transaction nghiệp vụ.
     * Scheduler sẽ đọc và publish lên Kafka sau — đảm bảo at-least-once delivery.
     */
    private void saveOrderEventToOutbox(Order order, String eventType) {
        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .recipientUserId(order.getCustomerId())
                .recipientEmail(order.getGuestEmail())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .discount(order.getDiscount())
                .shippingAddress(order.getShippingAddress())
                .shippingMethod(order.getShippingMethod())
                .carrierName(order.getCarrierName())
                .trackingNumber(order.getTrackingNumber())
                .guestName(order.getGuestName())
                .guestPhone(order.getGuestPhone())
                .note(order.getNote())
                .items(order.getItems().stream()
                        .map(item -> OrderEvent.OrderItemEvent.builder()
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .subtotal(item.getSubtotal())
                                .build())
                        .toList())
                .build();
        outboxEventService.saveEvent("Order", order.getId().toString(), eventType, event);
    }

    /**
     * Tạo mã đơn hàng duy nhất theo format: ORD-{timestamp}-{random 8 ký tự}.
     */
    private String generateOrderNumber() {
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + System.currentTimeMillis() + "-" + random;
    }
}
