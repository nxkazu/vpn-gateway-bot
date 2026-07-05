package com.tessera.experience;

import com.tessera.bot.remnawave.dto.RemnawaveUser;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Product-facing VPN experience logic: subscription health, traffic pressure,
 * and compact values used by the Telegram layer.
 */
public final class ExperienceService {

    private static final int BAR_SEGMENTS = 10;
    private final Clock clock;

    public ExperienceService() {
        this(Clock.systemUTC());
    }

    ExperienceService(Clock clock) {
        this.clock = clock;
    }

    public SubscriptionInsight insight(RemnawaveUser user) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime expireAt = user == null ? null : user.expireAt;
        long daysLeft = daysLeft(now, expireAt);

        long used = user != null && user.userTraffic != null ? Math.max(0L, user.userTraffic.usedTrafficBytes) : 0L;
        long limit = user == null || user.trafficLimitBytes == null ? 0L : Math.max(0L, user.trafficLimitBytes);
        boolean unlimited = limit == 0L;
        long remaining = unlimited ? 0L : Math.max(0L, limit - used);
        int percent = unlimited ? -1 : (int) Math.min(999L, Math.round((used * 100.0) / Math.max(1L, limit)));

        boolean active = user != null && user.isActive() && expireAt != null && expireAt.isAfter(now);
        boolean expiringSoon = active && daysLeft <= 3;
        boolean trafficHigh = !unlimited && percent >= 85;
        boolean trafficLimited = user != null && "LIMITED".equals(user.status);

        Advice advice;
        if (!active) advice = Advice.RENEW_NOW;
        else if (trafficLimited || trafficHigh) advice = Advice.TRAFFIC_HIGH;
        else if (expiringSoon) advice = Advice.RENEW_SOON;
        else advice = Advice.OK;

        return new SubscriptionInsight(daysLeft, used, limit, remaining, percent,
                usageBar(percent), unlimited, active, expiringSoon, trafficHigh, advice);
    }

    private long daysLeft(OffsetDateTime now, OffsetDateTime expireAt) {
        if (expireAt == null || !expireAt.isAfter(now)) return 0L;
        long seconds = Duration.between(now, expireAt).getSeconds();
        return Math.max(1L, (seconds + 86_399L) / 86_400L);
    }

    private String usageBar(int percent) {
        if (percent < 0) return "[unlimited]";
        int filled = Math.max(0, Math.min(BAR_SEGMENTS, (int) Math.round(percent / 10.0)));
        return "[" + "#".repeat(filled) + "-".repeat(BAR_SEGMENTS - filled) + "]";
    }

    public enum Advice {
        OK("sub.advice.ok"),
        RENEW_SOON("sub.advice.renew_soon"),
        RENEW_NOW("sub.advice.renew_now"),
        TRAFFIC_HIGH("sub.advice.traffic_high");

        private final String messageKey;

        Advice(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() { return messageKey; }
    }

    public record SubscriptionInsight(
            long daysLeft,
            long usedBytes,
            long limitBytes,
            long remainingBytes,
            int usagePercent,
            String usageBar,
            boolean unlimitedTraffic,
            boolean active,
            boolean expiringSoon,
            boolean trafficHigh,
            Advice advice
    ) {}
}
