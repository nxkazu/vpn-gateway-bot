package com.tessera.bot.db.model;

/**
 * A locally tracked payment invoice (one per provider transaction).
 * {@code kind} distinguishes what a paid invoice unlocks: {@code PLAN} activates
 * the buyer's own subscription, {@code GIFT} activates a redeemable gift code.
 */
public record PaymentInvoice(long id, long telegramId, String planCode, String provider,
                             String externalId, String payUrl, long amountCents,
                             String currency, String status, String promoCode, String kind) {

    public static final String KIND_PLAN = "PLAN";
    public static final String KIND_GIFT = "GIFT";

    public boolean isGift() {
        return KIND_GIFT.equals(kind);
    }
}
