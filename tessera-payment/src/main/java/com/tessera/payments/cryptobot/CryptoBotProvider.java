package com.tessera.payments.cryptobot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tessera.core.api.CreatedInvoice;
import com.tessera.core.api.InvoiceStatus;
import com.tessera.core.api.PaymentProvider;
import com.tessera.core.api.PaymentRequest;
import com.tessera.payments.PaymentsConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * CryptoBot merchant gate backed by the Crypto Pay API ({@code https://pay.crypt.bot/api}).
 * Invoices are issued in fiat (RUB by default); the payer chooses the crypto asset in
 * the CryptoBot UI. Status is confirmed by polling {@code getInvoices}.
 */
public final class CryptoBotProvider implements PaymentProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final PaymentsConfig.CryptoBotSection cfg;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public CryptoBotProvider(PaymentsConfig.CryptoBotSection cfg) {
        this.cfg = cfg;
    }

    @Override
    public String id() {
        return "cryptobot";
    }

    @Override
    public String displayName() {
        return "\uD83E\uDD16 CryptoBot (crypto)";
    }

    @Override
    public CreatedInvoice createInvoice(PaymentRequest req) throws Exception {
        String amount = new BigDecimal(req.amountMinor()).movePointLeft(2).toPlainString();
        ObjectNode body = mapper.createObjectNode();
        body.put("currency_type", "fiat");
        body.put("fiat", cfg.getFiat());
        body.put("amount", amount);
        body.put("description", req.description());
        body.put("payload", req.telegramId() + ":" + req.planCode());
        body.put("expires_in", 3600);

        JsonNode result = call("createInvoice", body.toString());
        String invoiceId = result.get("invoice_id").asText();
        String payUrl = result.hasNonNull("bot_invoice_url")
                ? result.get("bot_invoice_url").asText()
                : result.path("pay_url").asText(null);
        return new CreatedInvoice(invoiceId, payUrl);
    }

    @Override
    public InvoiceStatus checkInvoice(String externalId) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("invoice_ids", externalId);
        JsonNode result = call("getInvoices", body.toString());
        JsonNode items = result.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return InvoiceStatus.FAILED;
        }
        String status = items.get(0).path("status").asText("");
        return switch (status) {
            case "paid" -> InvoiceStatus.PAID;
            case "active" -> InvoiceStatus.PENDING;
            case "expired" -> InvoiceStatus.EXPIRED;
            default -> InvoiceStatus.FAILED;
        };
    }

    private JsonNode call(String method, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(cfg.getBaseUrl() + "/" + method)
                .header("Crypto-Pay-API-Token", cfg.getToken())
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        try (Response response = http.newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            JsonNode root = mapper.readTree(text);
            if (!root.path("ok").asBoolean(false)) {
                throw new IOException("CryptoBot API error (" + method + "): " + text);
            }
            return root.get("result");
        }
    }
}