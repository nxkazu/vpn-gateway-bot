package com.tessera.webapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.Plans;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.service.AnalyticsService;
import com.tessera.bot.service.RateLimiter;
import com.tessera.bot.service.SubscriptionService;
import com.tessera.bot.util.Formatting;
import com.tessera.experience.ExperienceService;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/** Telegram Mini App cabinet server: static UI + authenticated JSON API. */
public final class TesseraWebAppServer implements AutoCloseable {

    private final AppConfig config;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final RemnawaveClient panel;
    private final ExperienceService experience;
    private final AnalyticsService analytics;
    private final RateLimiter limiter;
    private final TelegramWebAppAuth auth;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    public TesseraWebAppServer(AppConfig config, UserRepository users, SubscriptionService subscriptions,
                               RemnawaveClient panel, ExperienceService experience,
                               AnalyticsService analytics, RateLimiter limiter) {
        this.config = config;
        this.users = users;
        this.subscriptions = subscriptions;
        this.panel = panel;
        this.experience = experience;
        this.analytics = analytics;
        this.limiter = limiter;
        this.auth = new TelegramWebAppAuth(config.botToken(), config.webappInitDataMaxAgeSeconds());
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.webappBind(), config.webappPort()), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                serveIndex(ex);
                return;
            }
            if ("/api/me".equals(path) && "GET".equals(ex.getRequestMethod())) {
                TelegramWebAppAuth.AuthenticatedUser user = authenticate(ex);
                analytics.track(user.telegramId(), AnalyticsService.Event.WEBAPP_OPENED);
                sendJson(ex, 200, model(user));
                return;
            }
            if (path.startsWith("/api/devices/")) {
                devices(ex, path);
                return;
            }
            sendJson(ex, 404, Map.of("error", "not found"));
        } catch (TelegramWebAppAuth.AuthException e) {
            sendJson(ex, 401, Map.of("error", "unauthorized", "message", e.getMessage()));
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage() == null ? "server error" : e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private void devices(HttpExchange ex, String path) throws Exception {
        TelegramWebAppAuth.AuthenticatedUser user = authenticate(ex);
        RemnawaveUser sub = subscriptionOrThrow(user.telegramId());
        List<Device> devices = devices(sub.uuid);
        if ("/api/devices/reset".equals(path) && "POST".equals(ex.getRequestMethod())) {
            if (!allowDeviceAction(user.telegramId())) {
                sendJson(ex, 429, Map.of("error", "device action limit reached"));
                return;
            }
            for (Device device : devices) {
                panel.deleteHwidDevice(sub.uuid, device.hwid());
            }
            analytics.track(user.telegramId(), AnalyticsService.Event.WEBAPP_DEVICES_RESET,
                    Map.of("count", devices.size()));
            sendJson(ex, 200, model(user));
            return;
        }
        if ("DELETE".equals(ex.getRequestMethod())) {
            String raw = path.substring("/api/devices/".length());
            int index;
            try {
                index = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                sendJson(ex, 400, Map.of("error", "bad device index"));
                return;
            }
            if (index < 0 || index >= devices.size()) {
                sendJson(ex, 404, Map.of("error", "device not found"));
                return;
            }
            if (!allowDeviceAction(user.telegramId())) {
                sendJson(ex, 429, Map.of("error", "device action limit reached"));
                return;
            }
            Device device = devices.get(index);
            panel.deleteHwidDevice(sub.uuid, device.hwid());
            analytics.track(user.telegramId(), AnalyticsService.Event.WEBAPP_DEVICE_REMOVED,
                    Map.of("index", index));
            sendJson(ex, 200, model(user));
            return;
        }
        sendJson(ex, 405, Map.of("error", "method not allowed"));
    }

    private boolean allowDeviceAction(long telegramId) {
        int limit = config.webappDeviceResetLimitPerDay();
        return limit <= 0 || limiter.allow("webapp-device:" + telegramId, limit, Duration.ofDays(1));
    }

    private TelegramWebAppAuth.AuthenticatedUser authenticate(HttpExchange ex) {
        String initData = ex.getRequestHeaders().getFirst("X-Telegram-Init-Data");
        TelegramWebAppAuth.AuthenticatedUser user = auth.authenticate(initData);
        users.upsert(user.telegramId(), user.username(), user.firstName(), user.languageCode());
        return user;
    }

    private Map<String, Object> model(TelegramWebAppAuth.AuthenticatedUser user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", Map.of(
                "telegramId", user.telegramId(),
                "username", user.username() == null ? "" : user.username(),
                "firstName", user.firstName() == null ? "" : user.firstName(),
                "languageCode", user.languageCode() == null ? "en" : user.languageCode()));
        out.put("botUrl", "https://t.me/" + config.botUsername());
        out.put("subscription", subscription(user.telegramId()));
        out.put("plans", Plans.all().stream().map(plan -> Map.of(
                "code", plan.code(),
                "title", plan.title(),
                "days", plan.days(),
                "trafficGb", plan.trafficGb(),
                "priceRub", plan.priceRub())).toList());
        return out;
    }

    private Map<String, Object> subscription(long telegramId) {
        Optional<RemnawaveUser> found = subscriptions.currentSubscription(telegramId);
        if (found.isEmpty()) {
            return Map.of("active", false, "devices", List.of());
        }
        RemnawaveUser sub = found.get();
        ExperienceService.SubscriptionInsight insight = experience.insight(sub);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active", insight.active());
        out.put("uuid", sub.uuid == null ? "" : sub.uuid);
        out.put("username", sub.username == null ? "" : sub.username);
        out.put("status", sub.status == null ? "UNKNOWN" : sub.status);
        out.put("expireAt", sub.expireAt == null ? "" : sub.expireAt.toString());
        out.put("expireAtText", Formatting.dateTime(sub.expireAt));
        out.put("subscriptionUrl", sub.subscriptionUrl == null ? "" : sub.subscriptionUrl);
        out.put("daysLeft", insight.daysLeft());
        out.put("usedText", Formatting.bytes(insight.usedBytes()));
        out.put("limitText", insight.unlimitedTraffic() ? "unlimited" : Formatting.bytes(insight.limitBytes()));
        out.put("remainingText", insight.unlimitedTraffic() ? "unlimited" : Formatting.bytes(insight.remainingBytes()));
        out.put("usagePercent", insight.usagePercent());
        out.put("unlimitedTraffic", insight.unlimitedTraffic());
        out.put("advice", insight.advice().name());
        out.put("devices", devices(sub.uuid).stream().map(Device::safeView).toList());
        return out;
    }

    private RemnawaveUser subscriptionOrThrow(long telegramId) {
        return subscriptions.currentSubscription(telegramId)
                .orElseThrow(() -> new IllegalStateException("subscription not found"));
    }

    private List<Device> devices(String uuid) {
        if (uuid == null || uuid.isBlank()) return List.of();
        JsonNode resp = panel.getUserHwidDevices(uuid);
        JsonNode arr = resp == null ? null : (resp.has("devices") ? resp.get("devices") : resp);
        List<Device> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            int index = 0;
            for (JsonNode d : arr) {
                String hwid = d.path("hwid").asText("");
                if (hwid.isBlank()) continue;
                String platform = d.path("platform").asText("");
                String model = d.path("deviceModel").asText("");
                String label = label(platform, model, hwid);
                out.add(new Device(index++, hwid, label, platform, model, mask(hwid)));
            }
        }
        return out;
    }

    private String label(String platform, String model, String hwid) {
        String prefix = platform == null || platform.isBlank() ? "" : platform + " ";
        String main = model == null || model.isBlank() ? mask(hwid) : model;
        return (prefix + main).trim();
    }

    private String mask(String hwid) {
        if (hwid == null || hwid.length() <= 8) return "••••";
        return "••••" + hwid.substring(hwid.length() - 6);
    }

    private void serveIndex(HttpExchange ex) throws IOException {
        try (InputStream in = TesseraWebAppServer.class.getResourceAsStream("/webapp/index.html")) {
            if (in == null) {
                send(ex, 500, "webapp UI resource missing", "text/plain; charset=utf-8");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
        }
    }

    private void sendJson(HttpExchange ex, int code, Object payload) throws IOException {
        send(ex, code, json.writeValueAsString(payload), "application/json; charset=utf-8");
    }

    private void send(HttpExchange ex, int code, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        if (server != null) server.stop(0);
    }

    private record Device(int index, String hwid, String label, String platform, String model, String maskedHwid) {
        Map<String, Object> safeView() {
            return Map.of(
                    "index", index,
                    "label", label,
                    "platform", platform == null ? "" : platform,
                    "model", model == null ? "" : model,
                    "maskedHwid", maskedHwid);
        }
    }
}
