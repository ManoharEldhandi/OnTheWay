package com.ontheway.config;

import com.ontheway.model.User;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the first administrator on a fresh, durable database (the {@code dev}/{@code prod}
 * profiles run against MySQL with no demo seed). Without this, a freshly deployed instance would
 * have no admin — and self-registration as {@code ADMIN} is deliberately blocked — so the admin
 * console would be unreachable.
 *
 * <p>The admin is created <b>only</b> from explicitly supplied credentials
 * ({@code ONTHEWAY_ADMIN_EMAIL} / {@code ONTHEWAY_ADMIN_PASSWORD}); nothing is hardcoded. It is
 * idempotent and a no-op when an administrator already exists, when credentials are not provided,
 * or when the demo seeder is active ({@code ontheway.seed.enabled=true}).
 */
@Component
@ConditionalOnProperty(name = "ontheway.seed.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ontheway.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${ontheway.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${ontheway.bootstrap.admin-name:Administrator}")
    private String adminName;

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return; // No bootstrap credentials supplied; nothing to do.
        }
        if (userRepository.countByRole(UserRole.ADMIN) > 0) {
            return; // An administrator already exists.
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.warn("Admin bootstrap skipped: a non-admin user already uses {}", adminEmail);
            return;
        }
        userRepository.save(User.builder()
                .name(adminName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .build());
        log.info("Bootstrapped initial administrator account: {}", adminEmail);
    }
}
