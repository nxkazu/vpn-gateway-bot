package com.tessera.payments.platega;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Platega merchant gate (SBP / cards). Transactions are created via
 * {@code POST /transaction/process} (authenticated with X-MerchantId / X-Secret headers)
 * and confirmed by polling {@code GET /transaction/{id}} — no public callback endpoint
 * is required, which keeps the bot long-polling only.
 */
public final class PlategaProvider implements PaymentProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final PaymentsConfig.PlategaSection cfg;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public PlategaProvider(PaymentsConfig.PlategaSection cfg) {
        this.cfg = cfg;
    }

    @Override
    public String id() {
        return "platega";
    }

    @Override
    public String displayName() {
        return "\uD83D\uDCB3 Platega (СБП/карты)";
    }

    @Override
    public CreatedInvoice createInvoice(PaymentRequest req) throws Exception {
        String transactionId = UUID.randomUUID().toString();

        ObjectNode details = mapper.createObjectNode();
        details.put("amount", new BigDecimal(req.amountMinor()).movePointLeft(2));
        details.put("currency", req.currency());

        ObjectNode body = mapper.createObjectNode();
        body.put("paymentMethod", cfg.getPaymentMethod());
        body.put("id", transactionId);
        body.set("paymentDetails", details);
        body.put("description", req.description());
        body.put("return", req.returnUrl());
        body.put("failedUrl", req.returnUrl());

        Request request = new Request.Builder()
                .url(cfg.getBaseUrl() + "/transaction/process")
                .header("X-MerchantId", cfg.getMerchantId())
                .header("X-Secret", cfg.getSecret())
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        JsonNode root = execute(request);
        String redirect = root.path("redirect").asText(null);
        if (redirect == null || redirect.isBlank()) {
            throw new IOException("Platega did not return a redirect URL: " + root);
        }
        return new CreatedInvoice(transactionId, redirect);
    }

    @Override
    public InvoiceStatus checkInvoice(String externalId) throws Exception {
        Request request = new Request.Builder()
                .url(cfg.getBaseUrl() + "/transaction/" + externalId)
                .header("X-MerchantId", cfg.getMerchantId())
                .header("X-Secret", cfg.getSecret())
                .get()
                .build();
        JsonNode root = execute(request);
        String status = root.path("status").asText("");
        return switch (status) {
            case "CONFIRMED", "PAID", "SUCCESS" -> InvoiceStatus.PAID;
            case "CANCELED", "CANCELLED", "DECLINED", "FAILED" -> InvoiceStatus.FAILED;
            case "EXPIRED" -> InvoiceStatus.EXPIRED;
            default -> InvoiceStatus.PENDING;
        };
    }

    private JsonNode execute(Request request) throws IOException {
        try (Response response = http.newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Platega API error " + response.code() + ": " + text);
            }
            return mapper.readTree(text);
        }
    }
}