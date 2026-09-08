package com.orionkey.repository;

import com.orionkey.constant.OrderStatus;
import com.orionkey.constant.OrderType;
import com.orionkey.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query("SELECT o FROM Order o WHERE o.status IN (com.orionkey.constant.OrderStatus.PAID, com.orionkey.constant.OrderStatus.DELIVERED) AND o.paidAt >= :start AND o.paidAt < :end")
    List<Order> findPaidInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    @Modifying
    @Query("UPDATE Order o SET o.isDeleted = 1 WHERE o.id IN :ids AND o.isDeleted = 0")
    int softDeleteByIds(@Param("ids") List<UUID> ids);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByUsdtTxId(String usdtTxId);

    Page<Order> findByUserIdAndIsDeletedOrderByCreatedAtDesc(UUID userId, int isDeleted, Pageable pageable);

    Page<Order> findByUserIdAndStatusAndIsDeletedOrderByCreatedAtDesc(UUID userId, OrderStatus status, int isDeleted, Pageable pageable);

    List<Order> findByEmailInAndIsDeletedOrderByCreatedAtDesc(List<String> emails, int isDeleted);

    List<Order> findByIdInAndIsDeleted(List<UUID> ids, int isDeleted);

    default List<Order> findByIdIn(List<UUID> ids) {
        return findByIdInAndIsDeleted(ids, 0);
    }

    @Query("SELECT o FROM Order o WHERE o.isDeleted = 0 AND o.status = com.orionkey.constant.OrderStatus.PENDING AND o.expiresAt < :now")
    List<Order> findExpiredOrders(@Param("now") LocalDateTime now);

    long countByUserIdAndStatusAndIsDeleted(UUID userId, OrderStatus status, int isDeleted);

    default long countByUserIdAndStatus(UUID userId, OrderStatus status) {
        return countByUserIdAndStatusAndIsDeleted(userId, status, 0);
    }

    long countByClientIpAndStatusAndIsDeleted(String clientIp, OrderStatus status, int isDeleted);

    default long countByClientIpAndStatus(String clientIp, OrderStatus status) {
        return countByClientIpAndStatusAndIsDeleted(clientIp, status, 0);
    }

    long countByEmailAndStatusAndIsDeleted(String email, OrderStatus status, int isDeleted);

    default long countByEmailAndStatus(String email, OrderStatus status) {
        return countByEmailAndStatusAndIsDeleted(email, status, 0);
    }

    /** 悲观写锁：SELECT ... FOR UPDATE，用于防止并发发货等竞态条件 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT o FROM Order o WHERE o.isDeleted = 0 AND o.riskFlagged = true ORDER BY o.createdAt DESC")
    Page<Order> findRiskFlaggedOrders(Pageable pageable);

    // Dashboard aggregate queries
    @Query("SELECT COALESCE(SUM(o.actualAmount), 0) FROM Order o WHERE (o.status = com.orionkey.constant.OrderStatus.PAID OR o.status = com.orionkey.constant.OrderStatus.DELIVERED) AND o.paidAt >= :since")
    BigDecimal sumSalesSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE (o.status = com.orionkey.constant.OrderStatus.PAID OR o.status = com.orionkey.constant.OrderStatus.DELIVERED) AND o.paidAt >= :since")
    long countPaidOrdersSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE (o.status = com.orionkey.constant.OrderStatus.PAID OR o.status = com.orionkey.constant.OrderStatus.DELIVERED)")
    long countTotalPaidOrders();

    long countByIsDeleted(int isDeleted);

    // 管理后台订单列表 — 无搜索词
    @Query("SELECT o FROM Order o WHERE o.isDeleted = 0 AND " +
            "(:status IS NULL OR o.status = :status) " +
            "AND (:orderType IS NULL OR o.orderType = :orderType) " +
            "AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod) " +
            "AND (:isRiskFlagged IS NULL OR o.riskFlagged = :isRiskFlagged) " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findAdminOrders(@Param("status") OrderStatus status,
                                @Param("orderType") OrderType orderType,
                                @Param("paymentMethod") String paymentMethod,
                                @Param("isRiskFlagged") Boolean isRiskFlagged,
                                Pageable pageable);

    // 管理后台订单列表 — 带搜索词（按订单ID、邮箱或商品名称搜索，keyword 保证非 null）
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN OrderItem oi ON oi.orderId = o.id WHERE o.isDeleted = 0 AND " +
            "(:status IS NULL OR o.status = :status) " +
            "AND (:orderType IS NULL OR o.orderType = :orderType) " +
            "AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod) " +
            "AND (:isRiskFlagged IS NULL OR o.riskFlagged = :isRiskFlagged) " +
            "AND (str(o.id) LIKE :keywordPattern OR o.email LIKE :keywordPattern OR oi.productTitle LIKE :keywordPattern) " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findAdminOrdersByKeyword(@Param("status") OrderStatus status,
                                         @Param("orderType") OrderType orderType,
                                         @Param("paymentMethod") String paymentMethod,
                                         @Param("isRiskFlagged") Boolean isRiskFlagged,
                                         @Param("keywordPattern") String keywordPattern,
                                         Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.actualAmount), 0), COUNT(o) FROM Order o WHERE (o.status = com.orionkey.constant.OrderStatus.PAID OR o.status = com.orionkey.constant.OrderStatus.DELIVERED) AND o.paidAt >= :start AND o.paidAt < :end")
    List<Object[]> summarizeRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(o.paymentMethod, 'unknown'), COALESCE(SUM(o.actualAmount), 0), COUNT(o) FROM Order o WHERE (o.status = com.orionkey.constant.OrderStatus.PAID OR o.status = com.orionkey.constant.OrderStatus.DELIVERED) AND o.paidAt >= :start AND o.paidAt < :end GROUP BY o.paymentMethod")
    List<Object[]> summarizeRevenueByPaymentMethod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT oi.productTitle, COALESCE(SUM(oi.subtotal), 0), SUM(oi.quantity) FROM Order o JOIN OrderItem oi ON oi.orderId = o.id WHERE (o.status = com.orionkey.constant.OrderStatus.PAID OR o.status = com.orionkey.constant.OrderStatus.DELIVERED) AND o.paidAt >= :start AND o.paidAt < :end GROUP BY oi.productTitle ORDER BY COALESCE(SUM(oi.subtotal), 0) DESC")
    List<Object[]> summarizeRevenueByProduct(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
