package com.company.banking.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * All business code reads time from this clock (UTC) so tests can control it.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
