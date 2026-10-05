package com.orionkey.config;

import com.orionkey.constant.UserRole;
import com.orionkey.entity.User;
import com.orionkey.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Only bootstrap a completely new installation; never alter existing users.
        if (users.count() != 0) return;
        String password = environment.getProperty("ADMIN_BOOTSTRAP_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set ADMIN_BOOTSTRAP_PASSWORD before starting a new installation");
        }
        if (environment.getProperty("security.password-plain", Boolean.class, false)) {
            throw new IllegalStateException("Set PASSWORD_PLAIN=false before creating the initial administrator");
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@orionkey.com");
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(password));
        users.save(admin);
        log.info("Initial administrator created");
    }
}
