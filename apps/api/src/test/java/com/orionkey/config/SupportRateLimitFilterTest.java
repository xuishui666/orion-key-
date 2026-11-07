package com.orionkey.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.repository.SiteConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SupportRateLimitFilterTest {
    @Test
    void capsCustomerTextAndImagesTogetherAndKeepsOtherDevicesIndependent() throws Exception {
        var filter = new RateLimitFilter(mock(SiteConfigRepository.class), new ObjectMapper());
        for (int i = 0; i < 10; i++) {
            assertEquals(200, send(filter, i % 2 == 0 ? "messages" : "images", "a", "192.0.2.1"));
        }
        assertEquals(429, send(filter, "messages", "a", "192.0.2.1"));
        assertEquals(200, send(filter, "messages", "b", "192.0.2.2"));
    }

    private int send(RateLimitFilter filter, String endpoint, String device, String ip) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/support/conversations/test/" + endpoint);
        request.setRemoteAddr(ip);
        request.addHeader("X-Device-Id", device.repeat(64));
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertEquals(response.getStatus() != 429, called.get());
        return response.getStatus();
    }
}
