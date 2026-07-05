package com.tessera.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.bot.service.PaymentService;
import com.tessera.bot.util.Formatting;
import com.tessera.core.api.TelegramNotifier;
import com.tessera.i18n.Lang;
import com.tessera.i18n.Messages;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** Built-in ops HTTP server for health, metrics, backups and payment webhooks. */
public final class OpsHttpServer implements AutoCloseable {

    private final AppConfig config;
    private final OpsHealthService health;
    private final DatabaseBackupService backups;
    private final PaymentService payments;
    private final LocaleService locales;
    private final Messages messages;
    private final TelegramNotifier notifier;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    public OpsHttpServer(AppConfig config, OpsHealthService health, DatabaseBackupService backups,
                         PaymentService payments, LocaleService locales, Messages messages,
                         TelegramNotifier notifier) {
        this.config = config;
        this.health = health;
        this.backups = backups;
        this.payments = payments;
        this.locales = locales;
        this.messages = messages;
        this.notifier = notifier;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.opsBind(), config.opsPort()), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/health".equals(path)) {
                HealthReport report = health.check();
                if (!config.opsHealthPublic() && !authorized(ex, config.opsToken())) {
                    sendJson(ex, 401, Map.of("error", "unauthorized"));
                    return;
                }
                sendJson(ex, report.healthy() ? 200 : 503, report);
                return;
            }
            if ("/metrics".equals(path)) {
                if (!authorized(ex, config.opsToken())) {
                    sendJson(ex, 401, Map.of("error", "unauthorized"));
                    return;
                }
                send(ex, 200, metrics(health.check()), "text/plain; charset=utf-8");
                return;
            }
            if (path.startsWith("/webhooks/payments/")) {
                webhook(ex, path);
                return;
            }
            if ("/ops/backups/run".equals(path)) {
                if (!"POST".equals(ex.getRequestMethod())) {
                    sendJson(ex, 405, Map.of("error", "POST required"));
                    return;
                }
                if (!authorized(ex, config.opsToken())) {
                    sendJson(ex, 401, Map.of("error", "unauthorized"));
                    return;
                }
                DatabaseBackupService.BackupResult result = backups.backupNow();
                sendJson(ex, 200, Map.of("ok", true, "file", result.file().toString(), "bytes", result.bytes()));
                return;
            }
            sendJson(ex, 404, Map.of("error", "not found"));
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private void webhook(HttpExchange ex, String path) throws Exception {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "POST required"));
            return;
        }
        if (config.paymentWebhookSecret().isBlank()
                || !authorized(ex, config.paymentWebhookSecret())) {
            sendJson(ex, 401, Map.of("error", "unauthorized"));
            return;
        }
        String rest = path.substring("/webhooks/payments/".length());
        String[] parts = rest.split("/", 2);
        String provider = parts[0];
        String externalId = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : null;
        PaymentService.WebhookResult result = payments.handleWebhook(provider, externalId, readBody(ex));
        if (result.accepted() && result.outcome() instanceof PaymentService.CheckOutcome.Paid paid) {
            Lang lang = locales.forUser(result.invoice().telegramId());
            notifier.sendMessage(result.invoice().telegramId(), messages.t(lang, "pay.auto.confirmed",
                    paid.plan().title(), Formatting.dateTime(paid.user().expireAt)));
        }
        sendJson(ex, result.accepted() ? 200 : 202, Map.of(
                "accepted", result.accepted(),
                "provider", provider,
                "externalId", result.externalId() == null ? "" : result.externalId()));
    }

    private boolean authorized(HttpExchange ex, String expected) {
        if (expected == null || expected.isBlank()) return false;
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")
                && constantTimeEquals(auth.substring("Bearer ".length()), expected)) {
            return true;
        }
        String header = ex.getRequestHeaders().getFirst("X-Tessera-Token");
        if (header != null && constantTimeEquals(header, expected)) return true;
        String webhook = ex.getRequestHeaders().getFirst("X-Tessera-Webhook-Secret");
        if (webhook != null && constantTimeEquals(webhook, expected)) return true;
        return constantTimeEquals(query(ex).get("secret"), expected);
    }

    private String metrics(HealthReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("tessera_health_up ").append(report.healthy() ? "1" : "0").append('\n');
        for (HealthReport.Check check : report.checks()) {
            sb.append("tessera_check_up{name=\"").append(check.name()).append("\"} ")
                    .append("UP".equals(check.status()) ? "1" : "0").append('\n');
            sb.append("tessera_check_latency_ms{name=\"").append(check.name()).append("\"} ")
                    .append(check.latencyMs()).append('\n');
        }
        return sb.toString();
    }

    private Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange ex, int code, Object payload) throws IOException {
        send(ex, code, json.writeValueAsString(payload), "application/json; charset=utf-8");
    }

    private void send(HttpExchange ex, int code, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        if (server != null) server.stop(0);
    }
}
