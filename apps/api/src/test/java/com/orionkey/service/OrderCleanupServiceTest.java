package com.orionkey.service;

import com.orionkey.entity.Order;
import com.orionkey.entity.UnmatchedTransaction;
import com.orionkey.repository.CardKeyRepository;
import com.orionkey.repository.OrderItemRepository;
import com.orionkey.repository.OrderRepository;
import com.orionkey.repository.PointsLogRepository;
import com.orionkey.repository.UnmatchedTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderCleanupServiceTest {
    @Test
    void detachesReferencesAndKeepsTxidMarkerBeforeDeletingOrders() {
        var orders = mock(OrderRepository.class);
        var items = mock(OrderItemRepository.class);
        var cards = mock(CardKeyRepository.class);
        var reviews = mock(UnmatchedTransactionRepository.class);
        var points = mock(PointsLogRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());

        Order paid = new Order();
        paid.setId(UUID.randomUUID());
        paid.setUsdtTxId("test-txid");
        paid.setUsdtChain("usdt_bep20");
        Order unpaid = new Order();
        unpaid.setId(UUID.randomUUID());
        when(orders.findPurgeCandidates(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(paid, unpaid));
        when(reviews.findByTxid("test-txid")).thenReturn(Optional.empty());

        new OrderCleanupService(orders, items, cards, reviews, points, manager).purgeOldOrders();

        var unpaidCutoff = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orders).findPurgeCandidates(any(), unpaidCutoff.capture(), any(), any(Pageable.class));
        assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(), unpaidCutoff.getValue());

        var marker = org.mockito.ArgumentCaptor.forClass(UnmatchedTransaction.class);
        verify(reviews).saveAndFlush(marker.capture());
        assertEquals("PURGED_ORDER", marker.getValue().getSource());
        assertEquals("AUTO_APPROVED", marker.getValue().getStatus());
        assertEquals("test-txid", marker.getValue().getTxid());
        assertTrue(marker.getValue().getOrderId() == null);
        List<UUID> ids = List.of(paid.getId(), unpaid.getId());
        var inOrder = inOrder(reviews, cards, points, items, orders);
        inOrder.verify(reviews).saveAndFlush(any(UnmatchedTransaction.class));
        inOrder.verify(cards).detachOrders(ids);
        inOrder.verify(reviews).detachOrders(ids);
        inOrder.verify(points).detachOrders(ids);
        inOrder.verify(items).deleteByOrderIds(ids);
        inOrder.verify(orders).deleteAllInBatch(List.of(paid, unpaid));
    }

    @Test
    void emptyBatchDoesNotDeleteAnything() {
        var orders = mock(OrderRepository.class);
        var items = mock(OrderItemRepository.class);
        var cards = mock(CardKeyRepository.class);
        var reviews = mock(UnmatchedTransactionRepository.class);
        var points = mock(PointsLogRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(orders.findPurgeCandidates(any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of());

        new OrderCleanupService(orders, items, cards, reviews, points, manager).purgeOldOrders();

        verifyNoInteractions(items, cards, reviews, points);
        verify(orders, never()).deleteAllInBatch(anyCollection());
    }
}
