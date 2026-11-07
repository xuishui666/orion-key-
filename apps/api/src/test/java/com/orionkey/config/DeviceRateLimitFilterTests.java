package com.orionkey.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.entity.SiteConfig;
import com.orionkey.repository.SiteConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceRateLimitFilterTests {

    @Test
    void failedOrdersDoNotConsumeAllowance() throws Exception {
        SiteConfigRepository configs = mock(SiteConfigRepository.class);
        when(configs.findByConfigKey("device_rate_limit_enabled"))
                .thenReturn(Optional.of(config("device_rate_limit_enabled", "true")));
        when(configs.findByConfigKey("device_order_limit_per_hour"))
                .thenReturn(Optional.of(config("device_order_limit_per_hour", "1")));
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(configs, new ObjectMapper());

        MockHttpServletResponse failed = invoke(filter, 400);
        MockHttpServletResponse successful = invoke(filter, 200);
        MockHttpServletResponse limited = invoke(filter, 200);

        assertEquals(400, failed.getStatus());
        assertEquals(200, successful.getStatus());
        assertEquals(429, limited.getStatus());
    }

    @Test
    void invalidRequestsAreLimitedAcrossBothCreationRoutesEvenWhenDeviceProtectionIsDisabled() throws Exception {
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        for (int i = 0; i < 6; i++) {
            assertEquals(400, invoke(filter, 400, i % 2 == 0 ? "/api/orders" : "/api/orders/from-cart",
                    "203.0.113.1", "a".repeat(64)).getStatus());
        }
        var limited = invoke(filter, 200, "/api/orders", "203.0.113.1", "A".repeat(64));
        assertEquals(429, limited.getStatus());
        assertTrue(Long.parseLong(limited.getHeader("Retry-After")) >= 1);
        assertEquals(200, invoke(filter, 200, "/api/orders/id/status", "203.0.113.1", "a".repeat(64)).getStatus());
        assertEquals(200, invoke(filter, 200, "/api/payments/webhook/usdt", "203.0.113.1", "a".repeat(64)).getStatus());
    }

    @Test
    void rotatingDeviceIdsOrForgingProxyHeadersCannotAvoidIpLimit() throws Exception {
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        for (int i = 0; i < 30; i++) {
            assertEquals(400, invoke(filter, 400, "/api/orders", "203.0.113.2", String.format("%064x", i)).getStatus());
        }
        assertEquals(429, invoke(filter, 200, "/api/orders", "203.0.113.2", "b".repeat(64)).getStatus());
        assertEquals(200, invoke(filter, 200, "/api/orders", "203.0.113.3", "c".repeat(64)).getStatus());
    }

    @Test
    void deviceLimitAlsoAppliesAcrossDifferentIpsAndRecoversAfterWindowExpires() throws Exception {
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        for (int i = 0; i < 6; i++) {
            assertEquals(400, invoke(filter, 400, "/api/orders", "203.0.113." + i, "d".repeat(64)).getStatus());
        }
        assertEquals(429, invoke(filter, 200, "/api/orders", "203.0.113.10", "d".repeat(64)).getStatus());
        expireMinuteWindows(filter);
        assertEquals(200, invoke(filter, 200, "/api/orders", "203.0.113.10", "d".repeat(64)).getStatus());
    }

    @Test
    void tenMinuteBudgetStillStopsSlowRepeatedFailures() throws Exception {
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        for (int i = 0; i < 20; i++) {
            if (i % 5 == 0) expireMinuteWindows(filter);
            assertEquals(400, invoke(filter, 400, "/api/orders", "203.0.113.11", "e".repeat(64)).getStatus());
        }
        expireMinuteWindows(filter);
        assertEquals(429, invoke(filter, 200, "/api/orders", "203.0.113.11", "e".repeat(64)).getStatus());
    }

    @Test
    void missingDeviceIdStillHasAnIpBudgetAndOrdinaryRetriesWork() throws Exception {
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        for (int i = 0; i < 30; i++) {
            assertEquals(i < 2 ? 400 : 200, invoke(filter, i < 2 ? 400 : 200,
                    "/api/orders", "203.0.113.12", null).getStatus());
        }
        assertEquals(429, invoke(filter, 200, "/api/orders", "203.0.113.12", null).getStatus());
    }

    @Test
    void ipTenMinuteBudgetIsSharedByMissingAndRotatingDevices() throws Exception {
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        for (int i = 0; i < 100; i++) {
            if (i % 25 == 0) expireMinuteWindows(filter);
            assertEquals(400, invoke(filter, 400, "/api/orders", "203.0.113.13",
                    i % 2 == 0 ? null : String.format("%064x", i)).getStatus());
        }
        expireMinuteWindows(filter);
        var blocked = invoke(filter, 200, "/api/orders", "203.0.113.13", null);
        assertEquals(429, blocked.getStatus());
        assertTrue(Long.parseLong(blocked.getHeader("Retry-After")) > 60);
    }

    @Test
    void trustedProxySeparatesCustomersByTheOverwrittenRealIpHeader() throws Exception {
        DeviceRateLimitFilter filter = new DeviceRateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "10.0.0.1");
        for (int i = 0; i < 32; i++) {
            var request = new MockHttpServletRequest("POST", "/api/orders");
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Real-IP", i == 31 ? "203.0.113.15" : "203.0.113.14");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(400));
            assertEquals(i == 30 ? 429 : 400, response.getStatus());
        }
    }

    private void expireMinuteWindows(DeviceRateLimitFilter filter) {
        Map<?, ?> counters = (Map<?, ?>) ReflectionTestUtils.getField(filter, "orderAttemptBuckets");
        counters.forEach((key, counter) -> {
            if (key.toString().contains("-minute:")) {
                ReflectionTestUtils.setField(counter, "windowStart", System.currentTimeMillis() - 61_000);
            }
        });
    }

    private MockHttpServletResponse invoke(DeviceRateLimitFilter filter, int downstreamStatus) throws Exception {
        return invoke(filter, downstreamStatus, "/api/orders", "203.0.113.1", "a".repeat(64));
    }

    private MockHttpServletResponse invoke(DeviceRateLimitFilter filter, int downstreamStatus,
                                           String path, String ip, String device) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        request.addHeader("X-Real-IP", java.util.UUID.randomUUID().toString());
        request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
        if (device != null) request.addHeader("X-Device-Id", device);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(downstreamStatus));
        return response;
    }

    private SiteConfig config(String key, String value) {
        SiteConfig config = new SiteConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }
}
