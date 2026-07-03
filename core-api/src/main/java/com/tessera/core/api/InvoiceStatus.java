package com.tessera.core.api;

/** Lifecycle status of a payment invoice. */
public enum InvoiceStatus {
    PENDING,
    PAID,
    EXPIRED,
    FAILED
}