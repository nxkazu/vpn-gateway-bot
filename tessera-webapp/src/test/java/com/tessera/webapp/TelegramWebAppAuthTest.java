package com.tessera.webapp;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramWebAppAuthTest {

    private static final String TOKEN = "123456:ABC";

    @Test
    void authenticatesSignedInitData() {
        TelegramWebAppAuth auth = new TelegramWebAppAuth(TOKEN, 3600,
                Clock.fixed(Instant.ofEpochSecond(1_700_000_100L), ZoneOffset.UTC));

        TelegramWebAppAuth.AuthenticatedUser user = auth.authenticate(initData(1_700_000_000L));

        assertEquals(42L, user.telegramId());
        assertEquals("neo", user.username());
    }

    @Test
    void rejectsTamperedData() {
        TelegramWebAppAuth auth = new TelegramWebAppAuth(TOKEN, 3600,
                Clock.fixed(Instant.ofEpochSecond(1_700_000_100L), ZoneOffset.UTC));

        String tampered = initData(1_700_000_000L).replace("neo", "trinity");

        assertThrows(TelegramWebAppAuth.AuthException.class, () -> auth.authenticate(tampered));
    }

    @Test
    void rejectsExpiredData() {
        TelegramWebAppAuth auth = new TelegramWebAppAuth(TOKEN, 10,
                Clock.fixed(Instant.ofEpochSecond(1_700_000_100L), ZoneOffset.UTC));

        assertThrows(TelegramWebAppAuth.AuthException.class, () -> auth.authenticate(initData(1_700_000_000L)));
    }

    private String initData(long authDate) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("auth_date", String.valueOf(authDate));
        params.put("query_id", "q1");
        params.put("user", "{\"id\":42,\"first_name\":\"Neo\",\"username\":\"neo\",\"language_code\":\"en\"}");
        String check = TelegramWebAppAuth.dataCheckString(params);
        String hash = sign(check);
        return encode(params) + "&hash=" + hash;
    }

    private String sign(String check) {
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), TOKEN.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmac(secret, check.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String encode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
