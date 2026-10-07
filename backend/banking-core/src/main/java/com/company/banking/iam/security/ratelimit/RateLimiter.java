package com.company.banking.iam.security.ratelimit;

import java.time.Duration;

/**
 * Fixed-window rate limiter.
 */
public interface RateLimiter {

    /**
     * @return {@code true} if the call identified by {@code key} is within {@code limit} calls per {@code window}
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
