package com.ontheway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables background jobs outside hermetic test runs.
 *
 * <p>The scheduler service remains available when this is disabled, allowing deterministic
 * direct invocation in tests without a background thread mutating shared test data.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "ontheway.eta",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {
}
