package com.orionkey.config;

import com.orionkey.constant.UserRole;
import com.orionkey.entity.User;
import com.orionkey.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Only bootstrap a completely new installation; never alter existing users.
        if (users.count() != 0) return;
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@orionkey.com");
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode("admin123456"));
        users.save(admin);
        log.warn("Initial administrator created. Change the default password before using this installation.");
    }
}
