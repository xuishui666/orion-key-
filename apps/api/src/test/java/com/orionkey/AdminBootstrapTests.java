package com.orionkey;

import com.orionkey.config.AdminBootstrapRunner;
import com.orionkey.constant.UserRole;
import com.orionkey.entity.User;
import com.orionkey.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTests {
    @Test
    void createsInitialAdminWithEncodedPasswordAndNeverOverwritesExistingUsers() {
        UserRepository users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder();
        var runner = new AdminBootstrapRunner(users, encoder);
        when(users.count()).thenReturn(0L, 1L);
        runner.run(null);
        runner.run(null);
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users, times(1)).save(saved.capture());
        assertEquals("admin", saved.getValue().getUsername());
        assertEquals(UserRole.ADMIN, saved.getValue().getRole());
        assertNotEquals("admin123456", saved.getValue().getPasswordHash());
        assertTrue(encoder.matches("admin123456", saved.getValue().getPasswordHash()));
    }

    @Test
    void existingInstallationIsUntouched() {
        UserRepository users = mock(UserRepository.class);
        when(users.count()).thenReturn(3L);
        new AdminBootstrapRunner(users, new BCryptPasswordEncoder()).run(null);
        verify(users, never()).save(any());
    }
}
