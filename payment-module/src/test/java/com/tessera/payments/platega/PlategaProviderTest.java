package com.tessera.payments.platega;

import com.tessera.core.api.InvoiceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the mapping of the documented Platega status enum (docs.platega.io). */
class PlategaProviderTest {

    @Test
    void documentedStatusesMapCorrectly() {
        assertEquals(InvoiceStatus.PAID, PlategaProvider.mapStatus("CONFIRMED"));
        assertEquals(InvoiceStatus.FAILED, PlategaProvider.mapStatus("CANCELED"));
        assertEquals(InvoiceStatus.FAILED, PlategaProvider.mapStatus("CHARGEBACKED"));
        assertEquals(InvoiceStatus.PENDING, PlategaProvider.mapStatus("PENDING"));
    }

    @Test
    void unknownStatusesStayPendingSoWeNeverActivateByAccident() {
        assertEquals(InvoiceStatus.PENDING, PlategaProvider.mapStatus(""));
        assertEquals(InvoiceStatus.PENDING, PlategaProvider.mapStatus("SOMETHING_NEW"));
    }
}