package com.tessera.bot.remnawave;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.remnawave.dto.CreateUserRequest;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.remnawave.dto.SystemStats;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin, typed client over the Remnawave REST API (v2.8.0).
 * All responses are wrapped by the panel in a top-level {@code response} field, which this
 * client unwraps automatically.
 */
public class RemnawaveClient {

    private static final Logger log = LoggerFactory.getLogger(RemnawaveClient.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final String baseUrl;
    private final String token;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    public RemnawaveClient(AppConfig config) {
        this.baseUrl = config.remnawaveBaseUrl();
        this.token = config.remnawaveToken();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(45))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ----------------------------------------------------------------- users

    public RemnawaveUser createUser(CreateUserRequest req) {
        JsonNode node = post("/api/users", req);
        return convert(node, RemnawaveUser.class);
    }

    /** Returns the first Remnawave user linked to a Telegram id, or {@code null} if none. */
    public RemnawaveUser findByTelegramId(long telegramId) {
        try {
            JsonNode node = get("/api/users/by-telegram-id/" + telegramId);
            if (node != null && node.isArray() && node.size() > 0) {
                return convert(node.get(0), RemnawaveUser.class);
            }
            return null;
        } catch (RemnawaveException e) {
            if (e.isNotFound()) return null;
            throw e;
        }
    }

    public RemnawaveUser getByUuid(String uuid) {
        return convert(get("/api/users/" + uuid), RemnawaveUser.class);
    }

    public RemnawaveUser getByUsername(String username) {
        return convert(get("/api/users/by-username/" + username), RemnawaveUser.class);
    }

    /** Updates the expiry date (subscription extension) for a user identified by uuid. */
    public RemnawaveUser updateExpiry(String uuid, OffsetDateTime newExpiry) {
        var body = mapper.createObjectNode();
        body.put("uuid", uuid);
        body.put("expireAt", newExpiry.toString());
        return convert(patch("/api/users", body), RemnawaveUser.class);
    }

    public void enableUser(String uuid)  { post("/api/users/" + uuid + "/actions/enable", null); }
    public void disableUser(String uuid) { post("/api/users/" + uuid + "/actions/disable", null); }
    public void revokeUser(String uuid)  { post("/api/users/" + uuid + "/actions/revoke", null); }
    public void resetTraffic(String uuid){ post("/api/users/" + uuid + "/actions/reset-traffic", null); }
    public void deleteUser(String uuid)  { delete("/api/users/" + uuid); }

    /** Total number of users in the panel (via offset pagination metadata). */
    public long totalUsers() {
        JsonNode node = get("/api/users?size=1&start=0");
        return node != null && node.has("total") ? node.get("total").asLong() : 0L;
    }

    // ---------------------------------------------------------------- system

    public SystemStats systemStats() {
        return convert(get("/api/system/stats"), SystemStats.class);
    }

    public JsonNode systemHealth() { return get("/api/system/health"); }
    public JsonNode nodesStats()   { return get("/api/system/stats/nodes"); }

    // ----------------------------------------------------------------- nodes

    public JsonNode getNodes() { return get("/api/nodes"); }
    public void restartAllNodes() { post("/api/nodes/actions/restart-all", null); }
    public void enableNode(String uuid)  { post("/api/nodes/" + uuid + "/actions/enable", null); }
    public void disableNode(String uuid) { post("/api/nodes/" + uuid + "/actions/disable", null); }
    public void restartNode(String uuid) { post("/api/nodes/" + uuid + "/actions/restart", null); }

    // ---------------------------------------------------------------- squads

    public JsonNode getInternalSquads() { return get("/api/internal-squads"); }

    // ------------------------------------------------------------- transport

    private JsonNode get(String path) {
        Request req = baseRequest(path).get().build();
        return execute(req);
    }

    private JsonNode post(String path, Object body) {
        RequestBody rb = toBody(body);
        Request req = baseRequest(path).post(rb).build();
        return execute(req);
    }

    private JsonNode patch(String path, Object body) {
        RequestBody rb = toBody(body);
        Request req = baseRequest(path).patch(rb).build();
        return execute(req);
    }

    private JsonNode delete(String path) {
        Request req = baseRequest(path).delete().build();
        return execute(req);
    }

    private Request.Builder baseRequest(String path) {
        return new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
    }

    private RequestBody toBody(Object body) {
        try {
            String json = body == null ? "{}" : mapper.writeValueAsString(body);
            return RequestBody.create(json, JSON);
        } catch (IOException e) {
            throw new RemnawaveException("Failed to serialize request body", e);
        }
    }

    private JsonNode execute(Request req) {
        try (Response resp = http.newCall(req).execute()) {
            String bodyStr = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.warn("Remnawave API {} {} -> {}: {}", req.method(), req.url(), resp.code(), bodyStr);
                throw new RemnawaveException(resp.code(),
                        "Remnawave API error " + resp.code() + ": " + bodyStr);
            }
            if (bodyStr.isBlank()) return null;
            JsonNode root = mapper.readTree(bodyStr);
            // The panel wraps payloads in {"response": ...}
            return root.has("response") ? root.get("response") : root;
        } catch (IOException e) {
            throw new RemnawaveException("Remnawave API transport error: " + e.getMessage(), e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        if (node == null) return null;
        try {
            return mapper.treeToValue(node, type);
        } catch (IOException e) {
            throw new RemnawaveException("Failed to parse Remnawave response", e);
        }
    }
}
