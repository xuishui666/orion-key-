package com.orionkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.*;
import com.orionkey.entity.*;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.*;
import com.orionkey.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderPrivacyAndDeliveryTests {
    @Autowired OrderRepository orders;
    @MockitoSpyBean OrderItemRepository items;
    @Autowired CardKeyRepository cards;
    @Autowired OrderAccessService access;
    @Autowired DeliverService delivery;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    private static final String TOKEN = "a".repeat(64);
    private static final String PASSWORD = "lookup-password-123";

    @Test
    void emailAndOrderIdAloneCannotReadOrMutateOrders() throws Exception {
        Order order = order(OrderStatus.PENDING, true);
        mvc.perform(post("/api/orders/query").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("emails", List.of(order.getEmail())))))
                .andExpect(status().isForbidden());
        for (String endpoint : List.of("status", "export")) {
            mvc.perform(get("/api/orders/" + order.getId() + "/" + endpoint).contextPath("/api"))
                    .andExpect(status().isForbidden());
        }
        for (String endpoint : List.of("refresh", "repay", "txid-verify")) {
            mvc.perform(post("/api/orders/" + order.getId() + "/" + endpoint).contextPath("/api")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/orders/deliver").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("order_ids", List.of(order.getId().toString())))))
                .andExpect(status().isForbidden());
        String proof = proof(TOKEN, null, null);
        mvc.perform(get("/api/orders/" + order.getId() + "/status").contextPath("/api").header("X-Order-Proof", proof))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void credentialsAreHashedAndPasswordLookupIsScoped() throws Exception {
        Order order = order(OrderStatus.PENDING, true);
        Order other = order(OrderStatus.PENDING, true);
        assertNotEquals(TOKEN, order.getAccessTokenHash());
        assertNotEquals(PASSWORD, order.getLookupPasswordHash());
        var request = new MockHttpServletRequest();
        assertEquals(List.of(order.getId().toString()), access.resolveQuery(
                Map.of("emails", List.of(order.getEmail()), "lookup_password", PASSWORD), request));
        assertThrows(BusinessException.class, () -> access.resolveQuery(
                Map.of("emails", List.of(order.getEmail()), "lookup_password", "wrong-password"), request));
        request.addHeader("X-Order-Proof", proof(null, order.getEmail(), PASSWORD));
        access.authorize(order.getId(), request);
        assertThrows(BusinessException.class, () -> access.authorize(other.getId(), request));
        request.removeHeader("X-Order-Proof");
        request.addHeader("X-Order-Proof", "bnVsbA");
        assertThrows(BusinessException.class, () -> access.authorize(order.getId(), request));
    }

    @Test
    void legacyOrderRequiresItsOriginalNonemptySession() {
        Order order = order(OrderStatus.PENDING, false);
        var request = new MockHttpServletRequest();
        assertThrows(BusinessException.class, () -> access.authorize(order.getId(), request));
        request.addHeader("X-Session-Token", order.getSessionToken());
        access.authorize(order.getId(), request);
        request.removeHeader("X-Session-Token");
        request.addHeader("X-Session-Token", "someone-else");
        assertThrows(BusinessException.class, () -> access.authorize(order.getId(), request));
    }

    @Test
    void laterStockFailureRollsBackAllEarlierAllocations() {
        Order order = order(OrderStatus.PAID, false);
        UUID firstProduct = UUID.randomUUID();
        UUID secondProduct = UUID.randomUUID();
        OrderItem firstItem = item(order, firstProduct);
        OrderItem secondItem = item(order, secondProduct);
        doReturn(List.of(firstItem, secondItem)).when(items).findByOrderId(order.getId());
        CardKey first = card(firstProduct);
        for (int i = 0; i < 2; i++) {
            assertThrows(BusinessException.class, () -> delivery.deliverOrders(Map.of("order_ids", List.of(order.getId().toString()))));
            assertEquals(CardKeyStatus.AVAILABLE, cards.findById(first.getId()).orElseThrow().getStatus());
            assertTrue(cards.findByOrderId(order.getId()).isEmpty());
            assertEquals(OrderStatus.PAID, orders.findById(order.getId()).orElseThrow().getStatus());
        }
        card(secondProduct);
        delivery.deliverOrders(Map.of("order_ids", List.of(order.getId().toString())));
        assertEquals(2, cards.findByOrderId(order.getId()).size());
        delivery.deliverOrders(Map.of("order_ids", List.of(order.getId().toString())));
        assertEquals(2, cards.findByOrderId(order.getId()).size());
    }

    @Test
    void staleCallbackCannotOverwriteDeliveredOrder() throws Exception {
        Order order = order(OrderStatus.PENDING, false);
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch updated = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> stale = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Order snapshot = orders.findById(order.getId()).orElseThrow();
                read.countDown();
                try { assertTrue(updated.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                snapshot.setStatus(OrderStatus.PAID);
                orders.saveAndFlush(snapshot);
            }));
            assertTrue(read.await(10, TimeUnit.SECONDS));
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Order current = orders.findByIdForUpdate(order.getId()).orElseThrow();
                current.setStatus(OrderStatus.DELIVERED);
                orders.saveAndFlush(current);
            });
            updated.countDown();
            ExecutionException error = assertThrows(ExecutionException.class, () -> stale.get(10, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof OptimisticLockingFailureException);
            assertEquals(OrderStatus.DELIVERED, orders.findById(order.getId()).orElseThrow().getStatus());
        } finally { updated.countDown(); executor.shutdownNow(); }
    }

    private Order order(OrderStatus status, boolean protectedOrder) {
        Order order = new Order();
        order.setOrderType(OrderType.CART);
        order.setStatus(status);
        order.setEmail(UUID.randomUUID() + "@example.com");
        order.setSessionToken(UUID.randomUUID().toString());
        order.setExpiresAt(LocalDateTime.now().plusHours(1));
        order.setTotalAmount(BigDecimal.TEN);
        order.setActualAmount(BigDecimal.TEN);
        if (protectedOrder) access.initialize(order, Map.of("access_token", TOKEN, "lookup_password", PASSWORD));
        return orders.saveAndFlush(order);
    }

    private OrderItem item(Order order, UUID product) {
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId()); item.setProductId(product); item.setProductTitle("Test");
        item.setQuantity(1); item.setUnitPrice(BigDecimal.ONE); item.setSubtotal(BigDecimal.ONE);
        return items.saveAndFlush(item);
    }

    private CardKey card(UUID product) {
        CardKey card = new CardKey(); card.setProductId(product); card.setContent(UUID.randomUUID().toString());
        return cards.saveAndFlush(card);
    }

    private String proof(String token, String email, String password) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsString(
                new OrderAccessService.Proof(token, email, password)).getBytes(StandardCharsets.UTF_8));
    }
}
