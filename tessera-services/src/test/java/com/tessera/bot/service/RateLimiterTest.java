package com.tessera.bot.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void limitsWithinWindowAndResetsAfterwards() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);

        assertTrue(limiter.allow("u1", 2, Duration.ofSeconds(10)));
        assertTrue(limiter.allow("u1", 2, Duration.ofSeconds(10)));
        assertFalse(limiter.allow("u1", 2, Duration.ofSeconds(10)));

        clock.now = clock.now.plusSeconds(11);
        assertTrue(limiter.allow("u1", 2, Duration.ofSeconds(10)));
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-07-05T00:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
