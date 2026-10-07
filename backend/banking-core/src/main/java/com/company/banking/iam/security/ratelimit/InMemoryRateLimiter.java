package com.company.banking.iam.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-local limiter for tests and single-instance development. Not shared between instances.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long bucket = clock.millis() / window.toMillis();
        Window current = windows.compute(key, (k, existing) ->
                existing == null || existing.bucket != bucket ? new Window(bucket) : existing);
        return current.count.incrementAndGet() <= limit;
    }

    public void reset() {
        windows.clear();
    }

    private static final class Window {
        private final long bucket;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long bucket) {
            this.bucket = bucket;
        }
    }
}
