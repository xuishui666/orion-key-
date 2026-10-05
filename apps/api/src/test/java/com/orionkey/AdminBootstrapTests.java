package com.orionkey;

import com.orionkey.config.AdminBootstrapRunner;
import com.orionkey.constant.UserRole;
import com.orionkey.entity.User;
import com.orionkey.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTests {
    @Test
    void createsInitialAdminWithEncodedPasswordAndNeverOverwritesExistingUsers() {
        UserRepository users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder();
        String password = UUID.randomUUID().toString();
        var environment = new MockEnvironment()
                .withProperty("ADMIN_BOOTSTRAP_PASSWORD", password)
                .withProperty("security.password-plain", "false");
        var runner = new AdminBootstrapRunner(users, encoder, environment);
        when(users.count()).thenReturn(0L, 1L);
        runner.run(null);
        runner.run(null);
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users, times(1)).save(saved.capture());
        assertEquals("admin", saved.getValue().getUsername());
        assertEquals(UserRole.ADMIN, saved.getValue().getRole());
        assertNotEquals(password, saved.getValue().getPasswordHash());
        assertTrue(encoder.matches(password, saved.getValue().getPasswordHash()));
    }

    @Test
    void existingInstallationIsUntouched() {
        UserRepository users = mock(UserRepository.class);
        when(users.count()).thenReturn(3L);
        new AdminBootstrapRunner(users, new BCryptPasswordEncoder(), new MockEnvironment()).run(null);
        verify(users, never()).save(any());
    }

    @Test
    void newInstallationRequiresPrivatePasswordAndHashedStorage() {
        UserRepository users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder();
        when(users.count()).thenReturn(0L);
        assertThrows(IllegalStateException.class,
                () -> new AdminBootstrapRunner(users, encoder, new MockEnvironment()).run(null));
        var plainEnvironment = new MockEnvironment()
                .withProperty("ADMIN_BOOTSTRAP_PASSWORD", UUID.randomUUID().toString())
                .withProperty("security.password-plain", "true");
        assertThrows(IllegalStateException.class,
                () -> new AdminBootstrapRunner(users, encoder, plainEnvironment).run(null));
        verify(users, never()).save(any());
    }
}
