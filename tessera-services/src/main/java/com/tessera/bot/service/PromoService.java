package com.tessera.bot.service;

import com.tessera.bot.db.PromoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Promo-code validation and redemption. Codes are managed from the admin dashboard
 * ({@code promo_codes} table); a code is redeemed at most once per user, and only when
 * the discounted invoice is actually paid.
 */
@Slf4j
@RequiredArgsConstructor
public class PromoService {

    /** A validated, applicable promo code. */
    public record Promo(String code, int discountPercent) {}

    private final PromoRepository repo;

    /** Returns the promo if it exists, is enabled, unexpired, has uses left and is new to this user. */
    public Optional<Promo> validate(String rawCode, long telegramId) {
        if (rawCode == null || rawCode.isBlank()) return Optional.empty();
        String code = rawCode.trim().toUpperCase();
        PromoRepository.PromoRow row = repo.find(code);
        if (row == null || !row.enabled()) return Optional.empty();
        if (row.discountPercent() <= 0 || row.discountPercent() >= 100) return Optional.empty();
        if (row.expiresAt() != null && row.expiresAt().isBefore(OffsetDateTime.now())) return Optional.empty();
        if (row.maxUses() > 0 && row.uses() >= row.maxUses()) return Optional.empty();
        if (repo.alreadyRedeemed(code, telegramId)) return Optional.empty();
        return Optional.of(new Promo(code, row.discountPercent()));
    }

    /** Discounted price in whole rubles, never below 1. */
    public static long discountedRub(long priceRub, int discountPercent) {
        long discounted = priceRub * (100L - discountPercent) / 100L;
        return Math.max(1L, discounted);
    }

    /** Records the redemption once the invoice is paid (idempotent per user+code). */
    public void redeemOnPaid(String code, long telegramId, long invoiceId) {
        redeemOnPaid(null, code, telegramId, invoiceId);
    }

    public void redeemOnPaid(java.sql.Connection conn, String code, long telegramId, long invoiceId) {
        try {
            repo.redeem(conn, code, telegramId, invoiceId);
            log.info("Promo {} redeemed by {} (invoice {})", code, telegramId, invoiceId);
        } catch (Exception e) {
            log.warn("Promo redemption failed: code={} user={}", code, telegramId, e);
        }
    }
}