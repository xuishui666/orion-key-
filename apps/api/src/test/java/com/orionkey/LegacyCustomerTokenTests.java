package com.orionkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.config.JwtAuthenticationFilter;
import com.orionkey.context.RequestContext;
import com.orionkey.repository.UserRepository;
import com.orionkey.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyCustomerTokenTests {
    @Test
    void customerTokenDoesNotAuthenticateGuestOrderRequest() throws Exception {
        JwtUtils jwt = mock(JwtUtils.class);
        UserRepository users = mock(UserRepository.class);
        Claims claims = mock(Claims.class);
        when(jwt.parseTokenSafe("old-customer-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(claims.get("username", String.class)).thenReturn("customer");
        when(claims.get("role", String.class)).thenReturn("USER");

        var filter = new JwtAuthenticationFilter(jwt, users, new ObjectMapper());
        var request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Authorization", "Bearer old-customer-token");
        var called = new AtomicBoolean();
        FilterChain chain = (req, res) -> {
            called.set(true);
            assertNull(RequestContext.getUserId());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        };

        SecurityContextHolder.clearContext();
        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertTrue(called.get());
            verifyNoInteractions(users);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
