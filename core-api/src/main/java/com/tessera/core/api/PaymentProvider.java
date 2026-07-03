package com.tessera.core.api;

/**
 * A merchant gate capable of creating payment invoices and reporting their status.
 * Implementations live in payment modules (e.g. CryptoBot, Platega).
 */
public interface PaymentProvider {

    /** Stable identifier used in callback data (e.g. {@code cryptobot}, {@code platega}). */
    String id();

    /** Human-readable label shown on the payment-method button. */
    String displayName();

    /** Creates an invoice for the given request and returns its external id + pay URL. */
    CreatedInvoice createInvoice(PaymentRequest request) throws Exception;

    /** Checks the current status of a previously created invoice. */
    InvoiceStatus checkInvoice(String externalId) throws Exception;
}