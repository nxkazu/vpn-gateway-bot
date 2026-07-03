package com.tessera.bot.service;

import com.tessera.bot.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Focused unit tests for referral payload parsing. Uses a mocked AppConfig so no
 * environment or network is required.
 */
class ReferralServiceTest {

    private ReferralService serviceWithBot(String username) {
        AppConfig config = mock(AppConfig.class);
        when(config.botUsername()).thenReturn(username);
        when(config.referralRewardDays()).thenReturn(15);
        return new ReferralService(config, null, null, null);
    }

    @Test
    void buildsDeepLink() {
        ReferralService svc = serviceWithBot("TesseraVpnBot");
        assertEquals("https://t.me/TesseraVpnBot?start=ref_42", svc.buildReferralLink(42));
    }

    @Test
    void parsesValidReferrer() {
        ReferralService svc = serviceWithBot("TesseraVpnBot");
        assertEquals(Optional.of(100L), svc.parseReferrerFromPayload("ref_100", 200));
    }

    @Test
    void rejectsSelfReferral() {
        ReferralService svc = serviceWithBot("TesseraVpnBot");
        assertTrue(svc.parseReferrerFromPayload("ref_50", 50).isEmpty());
    }

    @Test
    void ignoresMalformedPayload() {
        ReferralService svc = serviceWithBot("TesseraVpnBot");
        assertTrue(svc.parseReferrerFromPayload("hello", 1).isEmpty());
        assertTrue(svc.parseReferrerFromPayload("", 1).isEmpty());
        assertTrue(svc.parseReferrerFromPayload(null, 1).isEmpty());
    }
}
