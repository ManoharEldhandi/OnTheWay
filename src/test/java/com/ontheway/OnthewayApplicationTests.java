package com.ontheway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: boots the full application context under the "test" profile.
 * This proves that the Flyway migrations apply cleanly to an H2 (MySQL-mode)
 * database and that every JPA entity maps onto the migrated schema.
 */
@SpringBootTest
@ActiveProfiles("test")
class OnthewayApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to start (bad migration, mapping mismatch,
        // missing bean, invalid config) this test fails.
    }
}
