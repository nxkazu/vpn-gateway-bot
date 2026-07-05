package com.tessera.experience;

import com.tessera.bot.remnawave.dto.RemnawaveUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceServiceTest {

    private final ExperienceService service = new ExperienceService(
            Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void returnsOkInsightForHealthySubscription() {
        RemnawaveUser user = user(10, gb(12), gb(100), "ACTIVE");

        ExperienceService.SubscriptionInsight insight = service.insight(user);

        assertEquals(10, insight.daysLeft());
        assertEquals(12, insight.usagePercent());
        assertEquals("[#---------]", insight.usageBar());
        assertEquals(ExperienceService.Advice.OK, insight.advice());
    }

    @Test
    void warnsWhenTrafficIsHigh() {
        RemnawaveUser user = user(20, gb(92), gb(100), "ACTIVE");

        ExperienceService.SubscriptionInsight insight = service.insight(user);

        assertTrue(insight.trafficHigh());
        assertEquals(ExperienceService.Advice.TRAFFIC_HIGH, insight.advice());
    }

    @Test
    void handlesUnlimitedTraffic() {
        RemnawaveUser user = user(20, gb(92), 0, "ACTIVE");

        ExperienceService.SubscriptionInsight insight = service.insight(user);

        assertTrue(insight.unlimitedTraffic());
        assertEquals(-1, insight.usagePercent());
        assertEquals("[unlimited]", insight.usageBar());
    }

    private RemnawaveUser user(int days, long used, long limit, String status) {
        RemnawaveUser u = new RemnawaveUser();
        u.status = status;
        u.expireAt = OffsetDateTime.parse("2026-07-05T00:00:00Z").plusDays(days);
        u.trafficLimitBytes = limit;
        u.userTraffic = new RemnawaveUser.UserTraffic();
        u.userTraffic.usedTrafficBytes = used;
        return u;
    }

    private static long gb(long value) {
        return value * 1024L * 1024L * 1024L;
    }
}
