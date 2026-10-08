package com.company.banking.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs (outbox relay, housekeeping). Off in tests, which run jobs explicitly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "banking.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
