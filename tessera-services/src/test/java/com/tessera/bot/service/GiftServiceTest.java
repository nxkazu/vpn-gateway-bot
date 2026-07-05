package com.tessera.bot.service;

import com.tessera.bot.db.GiftRepository;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GiftServiceTest {

    private static final long BUYER = 100L;
    private static final long REDEEMER = 200L;

    private GiftRepository repo;
    private SubscriptionService subscriptions;
    private GiftService service;

    @BeforeEach
    void setUp() {
        repo = mock(GiftRepository.class);
        subscriptions = mock(SubscriptionService.class);
        service = new GiftService(repo, subscriptions);
    }

    private static GiftRepository.Gift gift(String code, String status) {
        // "m1" is one of the built-in default plans, resolvable without a database.
        return new GiftRepository.Gift(1L, code, BUYER, "m1", 7L, status, null, null, null);
    }

    // ----------------------------------------------------------- code format

    @Test
    void generatedCodesUseUnambiguousAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = service.generateCode();
            assertTrue(code.matches("TSR-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}"),
                    "unexpected code: " + code);
        }
    }

    // -------------------------------------------------------------- redeem

    @Test
    void redeemUnknownCodeIsInvalid() {
        when(repo.findByCode(anyString())).thenReturn(Optional.empty());
        assertInstanceOf(GiftService.RedeemOutcome.Invalid.class,
                service.redeem("TSR-XXXX-YYYY", REDEEMER));
        verify(repo, never()).claim(anyString(), anyLong());
    }

    @Test
    void redeemNormalizesTypedCode() {
        when(repo.findByCode("TSR-AB12-CD34")).thenReturn(Optional.empty());
        service.redeem("  tsr-ab12-cd34  ", REDEEMER);
        verify(repo).findByCode("TSR-AB12-CD34");
    }

    @Test
    void redeemUnpaidGiftReportsNotPaidYet() {
        when(repo.findByCode("TSR-AAAA-BBBB")).thenReturn(Optional.of(gift("TSR-AAAA-BBBB", "PENDING")));
        assertInstanceOf(GiftService.RedeemOutcome.NotPaidYet.class,
                service.redeem("TSR-AAAA-BBBB", REDEEMER));
    }

    @Test
    void redeemUsedGiftReportsAlreadyRedeemed() {
        when(repo.findByCode("TSR-AAAA-BBBB")).thenReturn(Optional.of(gift("TSR-AAAA-BBBB", "REDEEMED")));
        assertInstanceOf(GiftService.RedeemOutcome.AlreadyRedeemed.class,
                service.redeem("TSR-AAAA-BBBB", REDEEMER));
    }

    @Test
    void redeemActiveGiftActivatesPlanForRedeemer() {
        when(repo.findByCode("TSR-AAAA-BBBB")).thenReturn(Optional.of(gift("TSR-AAAA-BBBB", "ACTIVE")));
        when(repo.claim("TSR-AAAA-BBBB", REDEEMER)).thenReturn(true);
        RemnawaveUser user = new RemnawaveUser();
        when(subscriptions.activatePlan(eq(REDEEMER), anyInt(), anyLong(), anyString())).thenReturn(user);

        GiftService.RedeemOutcome outcome = service.redeem("TSR-AAAA-BBBB", REDEEMER);

        GiftService.RedeemOutcome.Redeemed redeemed =
                assertInstanceOf(GiftService.RedeemOutcome.Redeemed.class, outcome);
        assertEquals("m1", redeemed.plan().code());
        verify(subscriptions).activatePlan(eq(REDEEMER), eq(redeemed.plan().days()),
                eq(redeemed.plan().trafficBytes()), eq("gift:m1"));
    }

    @Test
    void losingTheClaimRaceReportsAlreadyRedeemed() {
        when(repo.findByCode("TSR-AAAA-BBBB")).thenReturn(Optional.of(gift("TSR-AAAA-BBBB", "ACTIVE")));
        when(repo.claim("TSR-AAAA-BBBB", REDEEMER)).thenReturn(false);

        assertInstanceOf(GiftService.RedeemOutcome.AlreadyRedeemed.class,
                service.redeem("TSR-AAAA-BBBB", REDEEMER));
        verify(subscriptions, never()).activatePlan(anyLong(), anyInt(), anyLong(), anyString());
    }

    @Test
    void failedActivationReleasesTheCode() {
        when(repo.findByCode("TSR-AAAA-BBBB")).thenReturn(Optional.of(gift("TSR-AAAA-BBBB", "ACTIVE")));
        when(repo.claim("TSR-AAAA-BBBB", REDEEMER)).thenReturn(true);
        when(subscriptions.activatePlan(anyLong(), anyInt(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("panel down"));

        assertThrows(RuntimeException.class, () -> service.redeem("TSR-AAAA-BBBB", REDEEMER));
        verify(repo).release("TSR-AAAA-BBBB");
    }

    // ---------------------------------------------------------- deep links

    @Test
    void startPayloadParsingExtractsGiftCodes() {
        assertEquals(Optional.of("TSR-AB12-CD34"), GiftService.codeFromStartPayload("gift_TSR-AB12-CD34"));
        assertEquals(Optional.empty(), GiftService.codeFromStartPayload("ref_12345"));
        assertEquals(Optional.empty(), GiftService.codeFromStartPayload("gift_"));
        assertEquals(Optional.empty(), GiftService.codeFromStartPayload(null));
        assertEquals("https://t.me/TesseraBot?start=gift_TSR-AB12-CD34",
                GiftService.shareLink("TesseraBot", "TSR-AB12-CD34"));
    }
}
