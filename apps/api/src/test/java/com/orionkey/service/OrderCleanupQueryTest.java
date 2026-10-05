package com.orionkey.service;

import com.orionkey.constant.OrderStatus;
import com.orionkey.constant.OrderType;
import com.orionkey.entity.Order;
import com.orionkey.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class OrderCleanupQueryTest {
    @Autowired private OrderRepository orders;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void selectsOnlyCompletedPaidAndPreviousDayExpiredUnpaidOrders() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 3, 30);
        Order delivered = save(OrderStatus.DELIVERED, now.minusDays(31), now.minusDays(31));
        Order awaitingDelivery = save(OrderStatus.PAID, now.minusDays(31), now.minusDays(31));
        Order yesterdayUnpaid = save(OrderStatus.EXPIRED, null, now.minusHours(5));
        Order todayUnpaid = save(OrderStatus.PENDING, null, now.minusHours(1));
        jdbc.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(now.minusDays(1)), yesterdayUnpaid.getId());
        jdbc.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(now.minusHours(1)), todayUnpaid.getId());
        entityManager.clear();

        Set<?> ids = orders.findPurgeCandidates(now.minusDays(30),
                        now.toLocalDate().atStartOfDay(), now, PageRequest.of(0, 100))
                .stream().map(Order::getId).collect(Collectors.toSet());

        assertEquals(Set.of(delivered.getId(), yesterdayUnpaid.getId()), ids);
    }

    private Order save(OrderStatus status, LocalDateTime paidAt, LocalDateTime expiresAt) {
        Order order = new Order();
        order.setOrderType(OrderType.DIRECT);
        order.setStatus(status);
        order.setPaidAt(paidAt);
        order.setExpiresAt(expiresAt);
        return orders.saveAndFlush(order);
    }
}
