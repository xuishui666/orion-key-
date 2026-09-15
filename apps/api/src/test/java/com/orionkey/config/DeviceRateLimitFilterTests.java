package com.orionkey.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.entity.SiteConfig;
import com.orionkey.repository.SiteConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private MockHttpServletResponse invoke(DeviceRateLimitFilter filter, int downstreamStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Device-Id", "a".repeat(64));
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
