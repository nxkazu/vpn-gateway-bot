package com.tessera.bot.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory fixed-window limiter for Telegram anti-abuse controls. */
public final class RateLimiter {

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String key, int limit, Duration window) {
        if (limit <= 0) return true;
        long now = clock.millis();
        long windowMillis = Math.max(1L, window.toMillis());
        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now >= existing.resetAtMillis) {
                return new Window(now + windowMillis, 1);
            }
            if (existing.count >= limit) {
                return new Window(existing.resetAtMillis, existing.count + 1);
            }
            return new Window(existing.resetAtMillis, existing.count + 1);
        });
        return current.count <= limit;
    }

    private record Window(long resetAtMillis, int count) {}
}
