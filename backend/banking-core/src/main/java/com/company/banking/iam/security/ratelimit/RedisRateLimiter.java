package com.company.banking.iam.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Shared counters in Redis ({@code INCR} + {@code EXPIRE} per window). If Redis is unavailable the limiter fails
 * open and logs, because the database-backed account lockout still protects credentials; an outage of the cache
 * must not lock every user out.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long bucket = clock.millis() / window.toMillis();
        String redisKey = "rl:" + key + ":" + bucket;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            return count == null || count <= limit;
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            return true;
        }
    }
}
