package com.tessera.core.api;

/**
 * A freshly created invoice.
 *
 * @param externalId provider-side invoice/transaction identifier
 * @param payUrl     URL the user opens to complete the payment
 */
public record CreatedInvoice(String externalId, String payUrl) {
}