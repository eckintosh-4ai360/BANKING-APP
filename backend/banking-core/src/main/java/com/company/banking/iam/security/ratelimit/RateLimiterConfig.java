package com.company.banking.iam.security.ratelimit;

import com.company.banking.iam.security.BankingSecurityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class RateLimiterConfig {

    @Bean
    public RateLimiter rateLimiter(BankingSecurityProperties properties,
                                   ObjectProvider<StringRedisTemplate> redisTemplate,
                                   Clock clock) {
        String store = properties.rateLimit().store();
        return switch (store) {
            case "redis" -> new RedisRateLimiter(redisTemplate.getObject(), clock);
            case "in-memory" -> new InMemoryRateLimiter(clock);
            default -> throw new IllegalStateException("Unknown rate limit store: " + store);
        };
    }
}
