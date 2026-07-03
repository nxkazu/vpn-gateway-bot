package com.tessera.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tessera.core.api.TelegramNotifier;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Minimal embedded web server (JDK HttpServer, zero external deps) serving the admin
 * dashboard UI and its JSON API. All {@code /api/*} routes require the bearer token.
 */
final class DashboardServer {

    private static final System.Logger LOG = System.getLogger(DashboardServer.class.getName());

    private final String bind;
    private final int port;
    private final String token;
    private final DashboardDao dao;
    private final TelegramNotifier notifier;
    private final ObjectMapper json = new ObjectMapper();

    private HttpServer server;

    DashboardServer(String bind, int port, String token, DataSource ds, TelegramNotifier notifier) {
        this.bind = bind;
        this.port = port;
        this.token = token;
        this.dao = new DashboardDao(ds);
        this.notifier = notifier;
    }

    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    void stop() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                serveIndex(ex);
                return;
            }
            if (path.startsWith("/api/")) {
                if (!authorized(ex)) {
                    sendJson(ex, 401, Map.of("error", "unauthorized"));
                    return;
                }
                api(ex, path);
                return;
            }
            send(ex, 404, "not found", "text/plain");
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "dashboard request failed", e);
            try {
                sendJson(ex, 500, Map.of("error", String.valueOf(e.getMessage())));
            } catch (IOException ignored) {
                // connection gone
            }
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        Map<String, String> q = query(ex);

        switch (path) {
            case "/api/tickets" -> {
                String status = q.getOrDefault("status", "OPEN").toUpperCase();
                sendJson(ex, 200, dao.tickets(status));
            }
            case "/api/ticket" -> {
                long id = Long.parseLong(q.get("id"));
                Map<String, Object> out = new HashMap<>();
                out.put("ticket", dao.ticket(id));
                out.put("messages", dao.messages(id));
                sendJson(ex, 200, out);
            }
            case "/api/reply" -> {
                requirePost(method);
                JsonNode body = json.readTree(readBody(ex));
                long id = body.get("ticketId").asLong();
                String text = body.get("text").asText("").trim();
                if (text.isEmpty()) {
                    sendJson(ex, 400, Map.of("error", "empty text"));
                    return;
                }
                Map<String, Object> t = dao.ticket(id);
                if (t == null) {
                    sendJson(ex, 404, Map.of("error", "ticket not found"));
                    return;
                }
                dao.addAdminMessage(id, body.path("adminName").asText("admin"), text);
                long telegramId = ((Number) t.get("telegram_id")).longValue();
                notifier.sendMessage(telegramId,
                        "\uD83D\uDCAC \u041E\u0442\u0432\u0435\u0442 \u043F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0438 / Support reply (#" + id + "):\n\n" + text);
                sendJson(ex, 200, Map.of("ok", true));
            }
            case "/api/close" -> {
                requirePost(method);
                JsonNode body = json.readTree(readBody(ex));
                long id = body.get("ticketId").asLong();
                Map<String, Object> t = dao.ticket(id);
                if (t == null) {
                    sendJson(ex, 404, Map.of("error", "ticket not found"));
                    return;
                }
                dao.setTicketStatus(id, "CLOSED");
                long telegramId = ((Number) t.get("telegram_id")).longValue();
                notifier.sendMessage(telegramId,
                        "\u2705 \u0422\u0438\u043A\u0435\u0442 #" + id + " \u0437\u0430\u043A\u0440\u044B\u0442 / Ticket closed. \u0421\u043F\u0430\u0441\u0438\u0431\u043E \u0437\u0430 \u043E\u0431\u0440\u0430\u0449\u0435\u043D\u0438\u0435!");
                sendJson(ex, 200, Map.of("ok", true));
            }
            case "/api/plans" -> {
                if ("GET".equals(method)) {
                    sendJson(ex, 200, dao.plans());
                } else {
                    JsonNode body = json.readTree(readBody(ex));
                    dao.upsertPlan(
                            body.get("code").asText(),
                            body.get("title").asText(),
                            body.get("days").asInt(),
                            body.get("trafficGb").asLong(),
                            body.get("priceRub").asLong(),
                            body.path("enabled").asBoolean(true),
                            body.path("sortOrder").asInt(0));
                    sendJson(ex, 200, Map.of("ok", true));
                }
            }
            case "/api/stats" -> sendJson(ex, 200, dao.stats());
            default -> sendJson(ex, 404, Map.of("error", "unknown endpoint"));
        }
    }

    // ------------------------------------------------------------- helpers

    private boolean authorized(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return h != null && h.equals("Bearer " + token);
    }

    private void requirePost(String method) {
        if (!"POST".equals(method)) throw new IllegalArgumentException("POST required");
    }

    private Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                out.put(java.net.URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
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

    private void serveIndex(HttpExchange ex) throws IOException {
        try (InputStream in = DashboardServer.class.getResourceAsStream("/dashboard/index.html")) {
            if (in == null) {
                send(ex, 500, "dashboard UI resource missing", "text/plain");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
        }
    }
}