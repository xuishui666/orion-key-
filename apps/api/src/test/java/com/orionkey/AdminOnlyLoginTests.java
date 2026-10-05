package com.orionkey;

import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.UserRole;
import com.orionkey.entity.User;
import com.orionkey.exception.BusinessException;
import com.orionkey.model.request.LoginRequest;
import com.orionkey.repository.CartItemRepository;
import com.orionkey.repository.UserRepository;
import com.orionkey.service.impl.AuthServiceImpl;
import com.orionkey.utils.CaptchaUtils;
import com.orionkey.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOnlyLoginTests {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtUtils jwt = mock(JwtUtils.class);
    private final AuthServiceImpl auth = new AuthServiceImpl(
            users, mock(CartItemRepository.class), passwords, jwt, mock(CaptchaUtils.class));

    @Test
    void existingCustomerCannotLogInEvenWithCorrectPassword() {
        User customer = user(UserRole.USER);
        when(users.findByUsernameOrEmail("account", "account")).thenReturn(Optional.of(customer));
        when(passwords.matches("password", customer.getPasswordHash())).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class, () -> auth.login(request(), null));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getCode());
        verify(jwt, never()).generateToken(any(), any(), any());
    }

    @Test
    void administratorCanStillLogIn() {
        User admin = user(UserRole.ADMIN);
        when(users.findByUsernameOrEmail("account", "account")).thenReturn(Optional.of(admin));
        when(passwords.matches("password", admin.getPasswordHash())).thenReturn(true);
        when(jwt.generateToken(admin.getId(), admin.getUsername(), "ADMIN")).thenReturn("admin-token");

        var result = auth.login(request(), null);

        assertEquals("admin-token", result.getToken());
        assertEquals("ADMIN", result.getUser().getRole());
    }

    private static LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.setAccount("account");
        request.setPassword("password");
        return request;
    }

    private static User user(UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("account");
        user.setPasswordHash("stored-hash");
        user.setRole(role);
        return user;
    }
}
