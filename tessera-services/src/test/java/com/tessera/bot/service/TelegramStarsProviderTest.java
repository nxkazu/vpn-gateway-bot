package com.tessera.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tessera.core.api.CreatedInvoice;
import com.tessera.core.api.InvoiceStatus;
import com.tessera.core.api.PaymentRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramStarsProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static PaymentRequest request(long amountMinor) {
        return new PaymentRequest(42L, "m1", "1 month", amountMinor, "RUB",
                "Tessera VPN — 1 month", "https://t.me/TesseraVpnBot");
    }

    // ------------------------------------------------------- stars conversion

    @Test
    void conversionRoundsUpAndNeverUndercharges() {
        // 149 RUB at 1.6 RUB/star -> 93.125 -> 94 stars
        assertEquals(94, TelegramStarsProvider.starsAmount(149_00, 1.6));
        // exact division stays exact: 160 RUB / 1.6 = 100
        assertEquals(100, TelegramStarsProvider.starsAmount(160_00, 1.6));
    }

    @Test
    void conversionChargesAtLeastOneStar() {
        assertEquals(1, TelegramStarsProvider.starsAmount(1, 1.6));
        assertEquals(1, TelegramStarsProvider.starsAmount(0, 1.6));
    }

    @Test
    void conversionFallsBackToParityOnInvalidRate() {
        assertEquals(149, TelegramStarsProvider.starsAmount(149_00, 0));
        assertEquals(149, TelegramStarsProvider.starsAmount(149_00, -3));
    }

    // -------------------------------------------------------- invoice creation

    @Test
    void createInvoiceBuildsXtrInvoiceLinkRequest() throws Exception {
        List<String> bodies = new ArrayList<>();
        TelegramStarsProvider provider = new TelegramStarsProvider("TOKEN", 1.6, null, (url, body) -> {
            assertTrue(url.contains("/botTOKEN/createInvoiceLink"));
            bodies.add(body);
            return "{\"ok\":true,\"result\":\"https://t.me/$abc\"}";
        });

        CreatedInvoice created = provider.createInvoice(request(149_00));

        assertEquals("https://t.me/$abc", created.payUrl());
        assertTrue(created.externalId().startsWith("stars-"));

        JsonNode sent = JSON.readTree(bodies.get(0));
        assertEquals("XTR", sent.path("currency").asText());
        assertEquals(created.externalId(), sent.path("payload").asText());
        assertEquals(94, sent.path("prices").get(0).path("amount").asLong());
        assertEquals("1 month", sent.path("title").asText());
    }

    @Test
    void createInvoiceFailsWhenTelegramRejects() {
        TelegramStarsProvider provider = new TelegramStarsProvider("TOKEN", 1.6, null,
                (url, body) -> "{\"ok\":false,\"description\":\"PAYMENT_PROVIDER_INVALID\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> provider.createInvoice(request(149_00)));
        assertTrue(e.getMessage().contains("PAYMENT_PROVIDER_INVALID"));
    }

    // -------------------------------------------------------- status lifecycle


}
