package com.nexatech.order.service;

import com.nexatech.order.client.InventoryClient;
import com.nexatech.order.client.PaymentClient;
import com.nexatech.order.client.ProductClient;
import com.nexatech.order.client.dto.ProductInfo;
import com.nexatech.order.client.dto.StockOperationRequest;
import com.nexatech.order.client.dto.VNPayCreateDto;
import com.nexatech.order.client.dto.VNPayInfo;
import com.nexatech.order.dto.ContinuePaymentResponse;
import com.nexatech.order.dto.CreateOrderRequest;
import com.nexatech.order.dto.OrderResponse;
import com.nexatech.order.dto.RefundBankInfoRequest;
import com.nexatech.order.dto.UpdateShippingRequest;
import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.enums.PaymentMethod;
import com.nexatech.order.event.OrderEvent;
import com.nexatech.order.exception.BadRequestException;
import com.nexatech.order.exception.ForbiddenException;
import com.nexatech.order.exception.ResourceNotFoundException;
import com.nexatech.order.exception.ServiceUnavailableException;
import com.nexatech.order.mapper.OrderMapper;
import com.nexatech.order.model.Order;
import com.nexatech.order.model.OrderItem;
import com.nexatech.order.repository.OrderRepository;
import com.nexatech.order.util.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;
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
    private final PaymentClient paymentClient;
    private final OutboxEventService outboxEventService;
    private final AuditLogService auditLogService;
    private final SnapshotService snapshotService;
    private final PlatformTransactionManager transactionManager;

    @Value("${order.payment.pending-timeout-minutes:20}")
    private long pendingPaymentTimeoutMinutes;

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
            auditLogService.logEvent("ORDER_CREATED", "Order", o.getId().toString(),
                    Map.of("orderId", o.getId().toString(),
                            "orderNumber", o.getOrderNumber(),
                            "customerId", o.getCustomerId() != null ? o.getCustomerId().toString() : "guest",
                            "status", o.getStatus().name(),
                            "totalAmount", o.getTotalAmount(),
                            "paymentMethod", String.valueOf(o.getPaymentMethod())),
                    customerId != null ? customerId.toString() : "guest");
            return o;
        });

        // 4. Giữ hàng sau khi transaction DB đã commit — tránh orphan reservation
        try {
            stockRequest.setReference(orderReference(saved.getId()));
            stockRequest.setReason("Reserve stock for order " + saved.getOrderNumber());
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
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable)
                .map(orderMapper::toResponse);
    }

    public boolean hasPurchasedProduct(UUID customerId, String productId) {
        return orderRepository.existsPurchasedProduct(customerId, productId, PURCHASED_STATUSES);
    }

    public ContinuePaymentResponse continueVNPayPayment(UUID customerId, UUID orderId) {
        Order order = findById(orderId);

        if (order.getCustomerId() != null && !Objects.equals(order.getCustomerId(), customerId)) {
            throw new ForbiddenException("Bạn không có quyền thanh toán đơn hàng này");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể tiếp tục thanh toán khi đơn hàng đang chờ thanh toán");
        }
        if (order.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new BadRequestException("Đơn hàng này không sử dụng phương thức thanh toán VNPay");
        }

        Instant expiresAt = order.getCreatedAt().plusSeconds(pendingPaymentTimeoutMinutes * 60);
        if (!Instant.now().isBefore(expiresAt)) {
            cancelOrderForSaga(orderId, "payment_timeout", customerId != null ? customerId.toString() : "guest");
            throw new BadRequestException("Đơn hàng đã quá thời gian thanh toán và đã được huỷ");
        }

        VNPayInfo vnpay = paymentClient.createVNPayPayment(customerId,
                null,
                VNPayCreateDto.builder()
                        .orderId(order.getId())
                        .amount(order.getTotalAmount())
                        .orderInfo("Thanh toan don hang " + order.getOrderNumber())
                        .build());

        if (vnpay == null || vnpay.getPaymentId() == null || vnpay.getPaymentUrl() == null) {
            throw new ServiceUnavailableException("Thanh toán này không khả dụng, hãy đợi một chút rồi thử lại.");
        }

        return ContinuePaymentResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .paymentId(vnpay.getPaymentId())
                .paymentMethod(order.getPaymentMethod())
                .paymentUrl(vnpay.getPaymentUrl())
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Lấy toàn bộ đơn hàng trong hệ thống — dùng bởi admin, delegate sang getAllOrders(status, pageable).
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return getAllOrders(null, pageable);
    }

    /**
     * Lấy danh sách đơn hàng theo trạng thái (admin, có thể null để lấy tất cả).
     */
    @Transactional(readOnly = true)
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
        auditLogService.logEvent("ORDER_STATUS_CHANGED", "Order", orderId.toString(),
                Map.of("orderId", orderId.toString(),
                        "previousStatus", oldStatus.name(),
                        "newStatus", newStatus.name()),
                "system");

        // Snapshot tại các mốc quan trọng để tối ưu point-in-time query
        if (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.COMPLETED
                || newStatus == OrderStatus.RETURNED || newStatus == OrderStatus.CANCELLED
                || newStatus == OrderStatus.OUT_OF_STOCK || newStatus == OrderStatus.SHIPPING_FAILED
                || newStatus == OrderStatus.REFUND_PENDING) {
            snapshotService.takeSnapshot("Order", orderId,
                    Map.of("orderId", orderId.toString(),
                            "status", newStatus.name(),
                            "previousStatus", oldStatus.name(),
                            "snapshotReason", "terminal_state"));
        }

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

    public OrderResponse requestInventoryFinalizationAfterPayment(UUID orderId, String actor) {
        Order current = findById(orderId);
        if (current.getStatus() != OrderStatus.PENDING) {
            log.info("Skip inventory finalization request because order is no longer PENDING: orderId={} status={}",
                    orderId, current.getStatus());
            return orderMapper.toResponse(current);
        }

        return new TransactionTemplate(transactionManager).execute(status -> {
            Order order = findById(orderId);
            if (order.getStatus() != OrderStatus.PENDING) {
                return orderMapper.toResponse(order);
            }

            saveOrderEventToOutbox(order, "ORDER_PAYMENT_COMPLETED");
            auditLogService.logEvent("ORDER_PAYMENT_COMPLETED_PENDING_INVENTORY", "Order", orderId.toString(),
                    Map.of("orderId", orderId.toString(),
                            "currentStatus", OrderStatus.PENDING.name(),
                            "nextStep", "INVENTORY_FINALIZATION",
                            "actor", actor),
                    actor);
            log.info("Payment completed, inventory finalization requested before confirming order: orderId={}", orderId);
            return orderMapper.toResponse(order);
        });
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
            auditLogService.logEvent("ORDER_CANCELLED", "Order", orderId.toString(),
                    Map.of("orderId", orderId.toString(),
                            "previousStatus", "PENDING_OR_CONFIRMED",
                            "newStatus", "CANCELLED",
                            "reason", "customer_request",
                            "customerId", customerId != null ? customerId.toString() : "guest"),
                    customerId != null ? customerId.toString() : "guest");
            log.info("Huỷ đơn hàng thành công: {} | customer: {}", orderId, customerId);
            return orderMapper.toResponse(saved);
        });
    }

    /**
     * Internal saga compensation path.
     *
     * This method intentionally does not call inventory/payment/shipping synchronously.
     * It changes the order state and writes ORDER_CANCELLED to the outbox; downstream
     * services consume that event and perform their own compensating action.
     */
    public OrderResponse cancelOrderForSaga(UUID orderId, String reason, String actor) {
        Order current = findById(orderId);
        if (current.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order already cancelled, skip duplicate compensation: orderId={} reason={}", orderId, reason);
            return orderMapper.toResponse(current);
        }
        if (current.getStatus() != OrderStatus.PENDING && current.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("Skip saga cancellation for order {} because status is {}", orderId, current.getStatus());
            return orderMapper.toResponse(current);
        }

        return new TransactionTemplate(transactionManager).execute(status -> {
            Order order = findById(orderId);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return orderMapper.toResponse(order);
            }
            if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
                log.warn("Skip saga cancellation inside transaction for order {} because status is {}", orderId, order.getStatus());
                return orderMapper.toResponse(order);
            }

            OrderStatus oldStatus = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            Order saved = orderRepository.save(order);
            saveOrderEventToOutbox(saved, "ORDER_CANCELLED");
            auditLogService.logEvent("ORDER_CANCELLED", "Order", orderId.toString(),
                    Map.of("orderId", orderId.toString(),
                            "previousStatus", oldStatus.name(),
                            "newStatus", "CANCELLED",
                            "reason", reason,
                            "actor", actor),
                    actor);
            snapshotService.takeSnapshot("Order", orderId,
                    Map.of("orderId", orderId.toString(),
                            "status", "CANCELLED",
                            "previousStatus", oldStatus.name(),
                            "snapshotReason", reason));
            log.info("Saga cancelled order: orderId={} previousStatus={} reason={} actor={}",
                    orderId, oldStatus, reason, actor);
            return orderMapper.toResponse(saved);
        });
    }

    public OrderResponse markOutOfStockForCompensation(UUID orderId, String reason, String actor) {
        return markCompensationStatus(orderId, OrderStatus.OUT_OF_STOCK, "ORDER_OUT_OF_STOCK",
                reason, actor);
    }

    public OrderResponse markShippingFailedForCompensation(UUID orderId, String reason, String actor) {
        return markCompensationStatus(orderId, OrderStatus.SHIPPING_FAILED, "ORDER_SHIPPING_FAILED",
                reason, actor);
    }

    public OrderResponse markInventoryFinalizedForShipping(UUID orderId, String actor) {
        Order current = findById(orderId);
        if (current.getStatus() == OrderStatus.CONFIRMED
                || current.getStatus() == OrderStatus.PROCESSING
                || current.getStatus() == OrderStatus.SHIPPED
                || current.getStatus() == OrderStatus.DELIVERED
                || current.getStatus() == OrderStatus.COMPLETED) {
            log.info("Inventory finalized event ignored because order is already confirmed or beyond: orderId={} status={}",
                    orderId, current.getStatus());
            return orderMapper.toResponse(current);
        }
        if (current.getStatus() != OrderStatus.PENDING) {
            log.warn("Inventory finalized event ignored because order is not PENDING: orderId={} status={}",
                    orderId, current.getStatus());
            return orderMapper.toResponse(current);
        }

        return new TransactionTemplate(transactionManager).execute(status -> {
            Order order = findById(orderId);
            if (order.getStatus() != OrderStatus.PENDING) {
                return orderMapper.toResponse(order);
            }

            OrderStatus oldStatus = order.getStatus();
            order.setStatus(OrderStatus.CONFIRMED);
            Order saved = orderRepository.save(order);
            saveOrderEventToOutbox(saved, "ORDER_READY_FOR_SHIPPING");
            auditLogService.logEvent("INVENTORY_FINALIZED_FOR_SHIPPING", "Order", orderId.toString(),
                    Map.of("orderId", orderId.toString(),
                            "previousStatus", oldStatus.name(),
                            "newStatus", OrderStatus.CONFIRMED.name(),
                            "actor", actor),
                    actor);
            log.info("Order confirmed and ready for shipping after inventory finalization: orderId={}", orderId);
            return orderMapper.toResponse(saved);
        });
    }

    public OrderResponse submitRefundBankInfo(UUID orderId, RefundBankInfoRequest request) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Order order = findById(orderId);
            if (order.getStatus() != OrderStatus.OUT_OF_STOCK
                    && order.getStatus() != OrderStatus.SHIPPING_FAILED
                    && order.getStatus() != OrderStatus.REFUND_PENDING) {
                throw new BadRequestException("Refund bank information is only accepted for compensation orders");
            }

            OrderStatus oldStatus = order.getStatus();
            order.setRefundBankName(request.getBankName().trim());
            order.setRefundAccountNumber(request.getAccountNumber().trim());
            order.setRefundAccountHolder(request.getAccountHolder().trim());
            order.setRefundInfoSubmittedAt(Instant.now());
            order.setStatus(OrderStatus.REFUND_PENDING);
            Order saved = orderRepository.save(order);
            saveOrderEventToOutbox(saved, "ORDER_REFUND_PENDING");
            auditLogService.logEvent("REFUND_BANK_INFO_SUBMITTED", "Order", orderId.toString(),
                    Map.of("orderId", orderId.toString(),
                            "previousStatus", oldStatus.name(),
                            "newStatus", OrderStatus.REFUND_PENDING.name(),
                            "bankName", saved.getRefundBankName()),
                    "customer");
            return orderMapper.toResponse(saved);
        });
    }

    public boolean hasStatus(UUID orderId, OrderStatus status) {
        return findById(orderId).getStatus() == status;
    }

    public void cancelExpiredPendingOrders(Instant createdBefore, String actor) {
        List<Order> expiredOrders = orderRepository
                .findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PENDING, createdBefore);
        for (Order order : expiredOrders) {
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                continue;
            }
            cancelOrderForSaga(order.getId(), "payment_timeout", actor);
        }
    }

    // ==================== Private helpers ====================

    private OrderResponse markCompensationStatus(UUID orderId,
                                                 OrderStatus compensationStatus,
                                                 String eventType,
                                                 String reason,
                                                 String actor) {
        Order current = findById(orderId);
        if (current.getStatus() == OrderStatus.REFUNDED) {
            log.info("Order already refunded, skip compensation status: orderId={} status={}", orderId, compensationStatus);
            return orderMapper.toResponse(current);
        }
        if (current.getStatus() == compensationStatus || current.getStatus() == OrderStatus.REFUND_PENDING) {
            log.info("Order already in compensation flow: orderId={} currentStatus={}", orderId, current.getStatus());
            return orderMapper.toResponse(current);
        }
        if (current.getStatus() == OrderStatus.COMPLETED) {
            log.warn("Skip compensation for completed order: orderId={} targetStatus={}", orderId, compensationStatus);
            return orderMapper.toResponse(current);
        }

        return new TransactionTemplate(transactionManager).execute(status -> {
            Order order = findById(orderId);
            if (order.getStatus() == OrderStatus.REFUNDED
                    || order.getStatus() == compensationStatus
                    || order.getStatus() == OrderStatus.REFUND_PENDING) {
                return orderMapper.toResponse(order);
            }
            if (order.getStatus() == OrderStatus.COMPLETED) {
                return orderMapper.toResponse(order);
            }

            OrderStatus oldStatus = order.getStatus();
            order.setStatus(compensationStatus);
            order.setCompensationReason(reason);
            Order saved = orderRepository.save(order);
            saveOrderEventToOutbox(saved, eventType);
            auditLogService.logEvent(eventType, "Order", orderId.toString(),
                    Map.of("orderId", orderId.toString(),
                            "previousStatus", oldStatus.name(),
                            "newStatus", compensationStatus.name(),
                            "reason", reason != null ? reason : "",
                            "actor", actor),
                    actor);
            snapshotService.takeSnapshot("Order", orderId,
                    Map.of("orderId", orderId.toString(),
                            "status", compensationStatus.name(),
                            "previousStatus", oldStatus.name(),
                            "snapshotReason", reason != null ? reason : eventType));
            log.warn("Order moved to compensation: orderId={} status={} reason={}",
                    orderId, compensationStatus, reason);
            return orderMapper.toResponse(saved);
        });
    }

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

    private String orderReference(UUID orderId) {
        return "ORDER:" + orderId;
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
        return StockOperationRequest.builder()
                .items(stockItems)
                .reference(orderReference(order.getId()))
                .reason("Release stock for order " + order.getOrderNumber())
                .build();
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
                .compensationReason(order.getCompensationReason())
                .refundBankName(order.getRefundBankName())
                .refundAccountNumber(order.getRefundAccountNumber())
                .refundAccountHolder(order.getRefundAccountHolder())
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
