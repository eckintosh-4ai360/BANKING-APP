package com.company.banking.iam.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-06T10:00:00Z"));
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    void allowsUpToTheLimitWithinAWindow() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("login:kofi", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("login:kofi", 3, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.tryAcquire("login:ama", 3, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void resetsInTheNextWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("login:kofi", 3, Duration.ofMinutes(1));
        }
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("login:kofi", 3, Duration.ofMinutes(1))).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
