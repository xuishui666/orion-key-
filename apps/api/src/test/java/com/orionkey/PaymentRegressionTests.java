package com.orionkey;

import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.entity.*;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.*;
import com.orionkey.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class PaymentRegressionTests {
    @Autowired OrderService orders;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository items;
    @Autowired ProductRepository products;
    @Autowired CardKeyRepository keys;
    @Autowired PaymentChannelRepository channels;
    @Autowired AdminOrderService adminOrders;
    @Autowired AdminCardKeyService adminKeys;
    @MockitoBean com.orionkey.service.impl.PaymentServiceImpl payments;

    @Test
    void gatewayFailureKeepsCommittedOrderAndItems() {
        String channelCode = "test_" + UUID.randomUUID();
        PaymentChannel channel = new PaymentChannel();
        channel.setChannelCode(channelCode);
        channel.setChannelName("Test");
        channels.save(channel);
        Product product = new Product();
        product.setTitle("Transaction test");
        product.setCategoryId(UUID.randomUUID());
        product.setBasePrice(new BigDecimal("10.00"));
        products.save(product);
        CardKey key = new CardKey();
        key.setProductId(product.getId());
        key.setContent(UUID.randomUUID().toString());
        keys.save(key);
        when(payments.createPayment(any(), anyString(), any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            UUID id = call.getArgument(0);
            assertTrue(orderRepository.existsById(id));
            assertEquals(1, items.findByOrderId(id).size());
            throw new BusinessException(ErrorCode.CHANNEL_UNAVAILABLE, "Gateway unavailable");
        });
        Map<String, Object> result = orders.createDirectOrder(Map.of(
                "product_id", product.getId().toString(), "quantity", 1,
                "email", UUID.randomUUID() + "@example.com", "payment_method", channelCode),
                null, null, UUID.randomUUID().toString());
        Map<?, ?> payment = (Map<?, ?>) result.get("payment");
        Order saved = orderRepository.findById((UUID) payment.get("order_id")).orElseThrow();
        assertEquals(OrderStatus.PENDING, saved.getStatus());
        assertEquals("Gateway unavailable", payment.get("error"));
        saved.setUsdtWalletAddress("wallet");
        saved.setUsdtCryptoAmount("1.234");
        saved.setUsdtChain("usdt_trc20");
        orderRepository.save(saved);
        assertEquals("1.234", orders.getOrderStatus(saved.getId()).get("crypto_amount"));
        assertEquals(channelCode, orders.getOrderStatus(saved.getId()).get("payment_method"));
        saved.setStatus(OrderStatus.PAID);
        saved.setPaidAt(java.time.LocalDateTime.of(2098, 1, 1, 12, 0));
        orderRepository.save(saved);
        adminOrders.deleteOrder(saved.getId());
        assertEquals(OrderStatus.PAID, orderRepository.findById(saved.getId()).orElseThrow().getStatus());
        Map<?, ?> revenue = (Map<?, ?>) adminOrders.getRevenueStats("2098-01-01", "2098-01-01");
        assertEquals(1L, ((Number) revenue.get("order_count")).longValue());
        assertEquals(0, new BigDecimal(revenue.get("total_amount").toString()).compareTo(new BigDecimal("10.00")));
        adminKeys.deleteCardKey(key.getId());
        assertEquals(0, keys.countByProductIdAndStatus(product.getId(), com.orionkey.constant.CardKeyStatus.AVAILABLE));
    }

    @Test
    void revenueQueryHandlesEmptyRangeAndRejectsReversedDates() {
        Map<?, ?> result = (Map<?, ?>) adminOrders.getRevenueStats("2099-01-01", "2099-01-02");
        assertEquals(0L, ((Number) result.get("order_count")).longValue());
        assertThrows(BusinessException.class, () -> adminOrders.getRevenueStats("2026-02-01", "2026-01-01"));
    }
}
