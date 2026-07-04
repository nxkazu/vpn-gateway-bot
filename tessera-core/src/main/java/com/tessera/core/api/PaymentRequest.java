package com.tessera.core.api;

/**
 * A request to create a payment invoice.
 *
 * @param telegramId  paying user's Telegram id
 * @param planCode    internal plan code being purchased
 * @param title       human-readable plan title
 * @param amountMinor amount in minor currency units (kopecks for RUB)
 * @param currency    ISO currency code (e.g. {@code RUB})
 * @param description invoice description shown to the payer
 * @param returnUrl   URL the payer should be sent back to after payment
 */
public record PaymentRequest(long telegramId, String planCode, String title, long amountMinor,
                             String currency, String description, String returnUrl) {
}