package com.tessera.webapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Validates Telegram Mini App initData and extracts the authenticated user. */
public final class TelegramWebAppAuth {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final String botToken;
    private final int maxAgeSeconds;
    private final Clock clock;

    public TelegramWebAppAuth(String botToken, int maxAgeSeconds) {
        this(botToken, maxAgeSeconds, Clock.systemUTC());
    }

    TelegramWebAppAuth(String botToken, int maxAgeSeconds, Clock clock) {
        this.botToken = botToken;
        this.maxAgeSeconds = maxAgeSeconds;
        this.clock = clock;
    }

    public AuthenticatedUser authenticate(String initData) {
        Map<String, String> params = parse(initData);
        String hash = params.remove("hash");
        params.remove("signature");
        if (hash == null || hash.isBlank()) {
            throw new AuthException("missing hash");
        }
        String expected = signature(dataCheckString(params));
        if (!MessageDigest.isEqual(hex(expected), hex(hash))) {
            throw new AuthException("invalid hash");
        }
        validateAge(params.get("auth_date"));
        return user(params.get("user"));
    }

    private void validateAge(String rawAuthDate) {
        if (maxAgeSeconds <= 0) return;
        try {
            long authDate = Long.parseLong(rawAuthDate);
            long now = Instant.now(clock).getEpochSecond();
            if (authDate > now + 60L || now - authDate > maxAgeSeconds) {
                throw new AuthException("expired init data");
            }
        } catch (NumberFormatException e) {
            throw new AuthException("invalid auth_date");
        }
    }

    private AuthenticatedUser user(String rawUser) {
        if (rawUser == null || rawUser.isBlank()) {
            throw new AuthException("missing user");
        }
        try {
            JsonNode node = JSON.readTree(rawUser);
            long id = node.path("id").asLong(0L);
            if (id <= 0L) throw new AuthException("missing user id");
            return new AuthenticatedUser(
                    id,
                    node.path("username").asText(null),
                    node.path("first_name").asText(null),
                    node.path("language_code").asText(null));
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("invalid user payload");
        }
    }

    private String signature(String dataCheckString) {
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8),
                botToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmac(secret, dataCheckString.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable", e);
        }
    }

    private byte[] hex(String value) {
        try {
            return HexFormat.of().parseHex(value);
        } catch (Exception e) {
            throw new AuthException("invalid hash encoding");
        }
    }

    static String dataCheckString(Map<String, String> params) {
        List<String> parts = new ArrayList<>();
        new TreeMap<>(params).forEach((key, value) -> parts.add(key + "=" + value));
        return String.join("\n", parts);
    }

    static Map<String, String> parse(String initData) {
        if (initData == null || initData.isBlank()) {
            throw new AuthException("missing init data");
        }
        Map<String, String> out = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int sep = pair.indexOf('=');
            if (sep < 0) continue;
            String key = decode(pair.substring(0, sep));
            String value = decode(pair.substring(sep + 1));
            out.put(key, value);
        }
        return out;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public record AuthenticatedUser(long telegramId, String username, String firstName, String languageCode) {}

    public static final class AuthException extends RuntimeException {
        AuthException(String message) { super(message); }
    }
}
