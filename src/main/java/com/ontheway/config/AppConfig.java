package com.ontheway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Shared application beans.
 */
@Configuration
public class AppConfig {

    /** System UTC clock. Injected where deterministic time is needed (e.g. the ETA engine). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
