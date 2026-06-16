package com.ontheway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Shared application beans.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Clock used where deterministic/consistent time is needed (e.g. the ETA engine and
     * the auto-advance scheduler). Uses the system default zone so it agrees with the
     * {@code LocalDateTime.now()} timestamps used elsewhere (order/pickup times).
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
