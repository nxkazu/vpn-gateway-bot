package com.tessera.bot.db.model;

/** A locally tracked payment invoice (one per provider transaction). */
public record PaymentInvoice(long id, long telegramId, String planCode, String provider,
                             String externalId, String payUrl, long amountCents,
                             String currency, String status, String promoCode) {
}