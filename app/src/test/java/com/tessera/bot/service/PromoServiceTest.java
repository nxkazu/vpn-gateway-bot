package com.tessera.bot.service;

import com.tessera.bot.db.PromoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromoServiceTest {

    private PromoRepository repo;
    private PromoService service;

    @BeforeEach
    void setUp() {
        repo = mock(PromoRepository.class);
        service = new PromoService(repo);
    }

    private static PromoRepository.PromoRow row(int percent, int maxUses, int uses,
                                                OffsetDateTime expiresAt, boolean enabled) {
        return new PromoRepository.PromoRow("SALE20", percent, maxUses, uses, expiresAt, enabled);
    }

    @Test
    void validCodeIsAccepted() {
        when(repo.find("SALE20")).thenReturn(row(20, 0, 5, null, true));
        when(repo.alreadyRedeemed(eq("SALE20"), anyLong())).thenReturn(false);

        Optional<PromoService.Promo> p = service.validate("sale20", 42L);
        assertTrue(p.isPresent());
        assertEquals("SALE20", p.get().code());
        assertEquals(20, p.get().discountPercent());
    }

    @Test
    void unknownDisabledExpiredOrExhaustedCodesAreRejected() {
        when(repo.find("SALE20")).thenReturn(null);
        assertTrue(service.validate("SALE20", 1L).isEmpty(), "unknown");

        when(repo.find("SALE20")).thenReturn(row(20, 0, 0, null, false));
        assertTrue(service.validate("SALE20", 1L).isEmpty(), "disabled");

        when(repo.find("SALE20")).thenReturn(row(20, 0, 0, OffsetDateTime.now().minusDays(1), true));
        assertTrue(service.validate("SALE20", 1L).isEmpty(), "expired");

        when(repo.find("SALE20")).thenReturn(row(20, 10, 10, null, true));
        assertTrue(service.validate("SALE20", 1L).isEmpty(), "exhausted");
    }

    @Test
    void secondUseBySameUserIsRejected() {
        when(repo.find("SALE20")).thenReturn(row(20, 0, 1, null, true));
        when(repo.alreadyRedeemed("SALE20", 42L)).thenReturn(true);
        assertTrue(service.validate("SALE20", 42L).isEmpty());
    }

    @Test
    void blankAndNullCodesAreRejected() {
        assertTrue(service.validate(null, 1L).isEmpty());
        assertTrue(service.validate("   ", 1L).isEmpty());
    }

    @Test
    void discountMathRoundsDownAndNeverGoesBelowOneRuble() {
        assertEquals(80, PromoService.discountedRub(100, 20));
        assertEquals(74, PromoService.discountedRub(149, 50));   // 74.5 -> 74
        assertEquals(1, PromoService.discountedRub(1, 99));      // floor at 1
        assertEquals(1, PromoService.discountedRub(2, 99));      // 0.02 -> 1
    }
}