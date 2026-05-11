package com.nexatech.order.repository;

import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

       Optional<Order> findByOrderNumber(String orderNumber);

       Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

       Page<Order> findByStatus(OrderStatus status, Pageable pageable);

       long countByCreatedAtBetween(Instant from, Instant to);

       // Tổng doanh thu (không tính CANCELLED / RETURNED / REFUNDED)
       @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
                     "WHERE o.createdAt BETWEEN :from AND :to " +
                     "AND o.status NOT IN ('CANCELLED', 'RETURNED', 'REFUNDED')")
       BigDecimal sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

       // Đếm đơn hàng theo từng trạng thái trong khoảng thời gian
       @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :from AND :to GROUP BY o.status")
       List<Object[]> countByStatusBetween(@Param("from") Instant from, @Param("to") Instant to);

       // Doanh thu theo từng ngày (native PostgreSQL)
       @Query(value = """
              SELECT DATE_TRUNC('day', created_at)::date AS day,
                     COALESCE(SUM(total_amount), 0) AS revenue,
                     COUNT(*) AS order_count
              FROM orders
              WHERE created_at BETWEEN :from AND :to
                     AND status NOT IN ('CANCELLED', 'RETURNED', 'REFUNDED')
              GROUP BY day
              ORDER BY day
       """, nativeQuery = true)
       List<Object[]> revenueByDay(@Param("from") Instant from, @Param("to") Instant to);

       // Top sản phẩm bán chạy theo số lượng
       @Query(value = """
              SELECT oi.product_id, oi.product_name,
                     SUM(oi.quantity)  AS total_qty,
                     SUM(oi.subtotal) AS total_revenue
              FROM order_items oi
              JOIN orders o ON oi.order_id = o.id
              WHERE o.created_at BETWEEN :from AND :to
              GROUP BY oi.product_id, oi.product_name
              ORDER BY total_qty DESC
              LIMIT :lim
       """, nativeQuery = true)
       List<Object[]> topProducts(@Param("from") Instant from, @Param("to") Instant to, @Param("lim") int limit);

       @Query("""
              SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
              FROM Order o
              JOIN o.items i
              WHERE o.customerId = :customerId
                     AND i.productId = :productId
                     AND o.status IN :statuses
       """)
       boolean existsPurchasedProduct(@Param("customerId") UUID customerId,
                     @Param("productId") String productId,
                     @Param("statuses") Set<OrderStatus> statuses);
}
