package com.orionkey;

import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.entity.*;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.*;
import com.orionkey.service.OrderAccessService;
import com.orionkey.service.OrderService;
import com.orionkey.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderCreationConcurrencyTests {
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired ProductRepository products;
    @Autowired CardKeyRepository cards;
    @Autowired CartItemRepository carts;
    @Autowired PaymentChannelRepository channels;
    @Autowired SiteConfigRepository configs;
    @Autowired DataSource dataSource;
    @MockitoBean PaymentServiceImpl payments;
    @MockitoSpyBean OrderAccessService access;

    @ParameterizedTest
    @ValueSource(strings = {"ip", "email"})
    void directAndCartCannotBothPassTheLastPendingSlot(String dimension) throws Exception {
        try (var connection = dataSource.getConnection()) {
            assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()),
                    "PostgreSQL serializable isolation is tested by the dedicated CI database job");
        }
        var fixture = fixture();
        String email = UUID.randomUUID() + "@example.com";
        String ip = UUID.randomUUID().toString();
        String session = UUID.randomUUID().toString();
        CartItem cart = new CartItem();
        cart.setSessionToken(session); cart.setProductId(fixture.product()); cart.setQuantity(1);
        carts.saveAndFlush(cart);

        CyclicBarrier bothPassedCount = new CyclicBarrier(2);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            bothPassedCount.await(15, TimeUnit.SECONDS);
            return null;
        }).when(access).initialize(any(Order.class), anyMap());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> direct = executor.submit(() -> create(fixture, false, email, ip, null));
            Future<Integer> fromCart = executor.submit(() -> create(fixture, true,
                    dimension.equals("email") ? email : UUID.randomUUID() + "@example.com",
                    dimension.equals("ip") ? ip : UUID.randomUUID().toString(), session));
            List<Integer> outcomes = List.of(direct.get(25, TimeUnit.SECONDS), fromCart.get(25, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(outcomes, 200));
            assertEquals(1, Collections.frequency(outcomes, 409));
            assertEquals(1, dimension.equals("ip")
                    ? orders.countByClientIpAndStatus(ip, OrderStatus.PENDING)
                    : orders.countByEmailAndStatus(email, OrderStatus.PENDING));
            verify(payments, times(1)).createPayment(any(), anyString(), any(), any());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            configs.findByConfigKey("max_pending_orders_per_user").ifPresent(config -> {
                config.setConfigValue("5"); configs.saveAndFlush(config);
            });
        }
    }

    @Test
    void ordinaryRetryIsRejectedWithoutCreatingAnotherOrderOrCallingGateway() {
        var fixture = fixture();
        String email = UUID.randomUUID() + "@example.com";
        String ip = UUID.randomUUID().toString();
        try {
            assertEquals(200, create(fixture, false, email, ip, null));
            BusinessException rejected = assertThrows(BusinessException.class,
                    () -> create(fixture, false, email, ip, null));
            assertEquals(ErrorCode.UNPAID_ORDER_EXISTS, rejected.getCode());
            assertEquals(1, orders.countByClientIpAndStatus(ip, OrderStatus.PENDING));
            verify(payments, times(1)).createPayment(any(), anyString(), any(), any());
        } finally {
            var config = configs.findByConfigKey("max_pending_orders_per_user").orElseThrow();
            config.setConfigValue("5"); configs.saveAndFlush(config);
        }
    }

    private int create(Fixture fixture, boolean cart, String email, String ip, String session) {
        Map<String, Object> request = new HashMap<>(Map.of(
                "email", email, "payment_method", fixture.channel(), "quantity", 1,
                "product_id", fixture.product().toString(),
                "access_token", "f".repeat(64), "lookup_password", "test-password-123"));
        try {
            Map<String, Object> result = cart ? service.createCartOrder(request, null, ip, session)
                    : service.createDirectOrder(request, null, ip, session);
            UUID id = (UUID) ((Map<?, ?>) result.get("payment")).get("order_id");
            assertEquals(1, items.findByOrderId(id).size());
            return 200;
        } catch (BusinessException e) {
            if (e.getHttpStatus() == HttpStatus.CONFLICT) return 409;
            throw e;
        }
    }

    private Fixture fixture() {
        SiteConfig config = configs.findByConfigKey("max_pending_orders_per_user").orElseGet(SiteConfig::new);
        config.setConfigKey("max_pending_orders_per_user"); config.setConfigValue("1"); config.setConfigGroup("risk");
        configs.saveAndFlush(config);
        PaymentChannel channel = new PaymentChannel();
        channel.setChannelCode("test_" + UUID.randomUUID()); channel.setChannelName("Test");
        channels.saveAndFlush(channel);
        Product product = new Product();
        product.setTitle("Pending concurrency test"); product.setCategoryId(UUID.randomUUID());
        product.setBasePrice(BigDecimal.TEN); products.saveAndFlush(product);
        CardKey card = new CardKey(); card.setProductId(product.getId()); card.setContent(UUID.randomUUID().toString());
        cards.saveAndFlush(card);
        when(payments.createPayment(any(), anyString(), any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            UUID id = call.getArgument(0);
            assertTrue(orders.existsById(id));
            return Map.of("order_id", id);
        });
        return new Fixture(product.getId(), channel.getChannelCode());
    }

    private record Fixture(UUID product, String channel) {}
}
