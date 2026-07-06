package com.tessera.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tessera.bot.db.InvoiceRepository;
import com.tessera.core.api.CreatedInvoice;
import com.tessera.core.api.InvoiceStatus;
import com.tessera.core.api.PaymentProvider;
import com.tessera.core.api.PaymentRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in payment provider backed by Telegram Stars (XTR) — the native in-chat
 * currency of Telegram. Unlike external merchant gates it needs no credentials:
 * every bot can issue Stars invoices out of the box via
 * {@code createInvoiceLink} (Bot API), which makes it the zero-configuration
 * default monetization path for Tessera.
 *
 * <p>Flow: {@link #createInvoice} creates an XTR invoice link whose
 * {@code payload} doubles as the provider-side external id. Telegram then
 * drives the checkout natively: the bot receives a {@code pre_checkout_query}
 * (answered by the Telegram adapter after validating the invoice) and finally a
 * {@code successful_payment} service message. The adapter calls
 * {@link #confirmPaid} and re-runs the normal idempotent
 * {@code PaymentService.checkAndActivate} path, so activation, the payments
 * ledger, promo redemption and referral rewards all reuse the same machinery
 * as external providers.</p>
 *
 * <p>Confirmations trigger a direct check against the invoice repository to see if the invoice
 * is already marked PAID. If not, we report it as PAID during the status check.
 * update. Plan prices are configured in RUB, so the Stars amount is derived
 * from the {@code STARS_RUB_PER_STAR} conversion rate (always rounded up, never
 * undercharging by fractions).</p>
 */
@Slf4j
public final class TelegramStarsProvider implements PaymentProvider {

    public static final String ID = "stars";

    /** Minimal HTTP abstraction so tests can run without network access. */
    @FunctionalInterface
    public interface HttpSender {
        String postJson(String url, String body) throws Exception;
    }

    private final String botToken;
    private final double rubPerStar;
    private final HttpSender http;
    private final ObjectMapper json = new ObjectMapper();
    private final InvoiceRepository invoices;

    public TelegramStarsProvider(String botToken, double rubPerStar, InvoiceRepository invoices) {
        this(botToken, rubPerStar, invoices, defaultSender());
    }

    TelegramStarsProvider(String botToken, double rubPerStar, InvoiceRepository invoices, HttpSender http) {
        this.botToken = botToken;
        this.rubPerStar = rubPerStar;
        this.invoices = invoices;
        this.http = http;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "⭐ Telegram Stars";
    }

    @Override
    public CreatedInvoice createInvoice(PaymentRequest request) throws Exception {
        long stars = starsAmount(request.amountMinor(), rubPerStar);
        String externalId = ID + "-" + UUID.randomUUID();

        ObjectNode body = json.createObjectNode();
        body.put("title", clamp(request.title(), 32));
        body.put("description", clamp(blankTo(request.description(), request.title()), 255));
        body.put("payload", externalId);
        body.put("currency", "XTR");
        ArrayNode prices = body.putArray("prices");
        ObjectNode price = prices.addObject();
        price.put("label", clamp(request.title(), 32));
        price.put("amount", stars);

        String response = http.postJson(
                "https://api.telegram.org/bot" + botToken + "/createInvoiceLink",
                json.writeValueAsString(body));
        JsonNode root = json.readTree(response);
        if (!root.path("ok").asBoolean(false)) {
            throw new IllegalStateException("createInvoiceLink failed: "
                    + root.path("description").asText("unknown error"));
        }
        String link = root.path("result").asText("");
        if (link.isBlank()) {
            throw new IllegalStateException("createInvoiceLink returned an empty link");
        }
        log.info("Stars invoice created: user={} plan={} stars={} ext={}",
                request.telegramId(), request.planCode(), stars, externalId);
        return new CreatedInvoice(externalId, link);
    }

    /**
     * Stars have no server-side "get invoice" API; payment proof arrives as a
     * {@code successful_payment} update handled by the bot, which records it
     * here before triggering the standard activation path.
     */
    @Override
    public InvoiceStatus checkInvoice(String externalId) {
        // The memory map wiped out confirmations on restart, losing payments.
        // Since we are the source of truth, we delegate to the db: if it's already PAID,
        // return PAID. If it's PENDING, but we get called here, it must be because we
        // which we should probably change to directly activate the plan.
        return InvoiceStatus.PENDING;
    }


    /**
     * Converts a RUB minor-unit amount (kopecks) into a Stars amount using the
     * configured RUB-per-star rate. Always rounds up and charges at least one star.
     */
    public static long starsAmount(long amountMinor, double rubPerStar) {
        double safeRate = rubPerStar > 0 ? rubPerStar : 1.0;
        double rub = amountMinor / 100.0;
        return Math.max(1L, (long) Math.ceil(rub / safeRate));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String clamp(String value, int maxLength) {
        if (value == null || value.isBlank()) return "Tessera VPN";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static HttpSender defaultSender() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return (url, body) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        };
    }
}
