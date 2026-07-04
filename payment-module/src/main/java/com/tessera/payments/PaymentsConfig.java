package com.tessera.payments;

import com.tessera.core.api.ModuleContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Payment module configuration. A default YAML file is generated on
 * first start; environment variables override file values (12-factor friendly):
 * {@code CRYPTOBOT_ENABLED}, {@code CRYPTOBOT_TOKEN},
 * {@code PLATEGA_ENABLED}, {@code PLATEGA_MERCHANT_ID}, {@code PLATEGA_SECRET}.
 */
public class PaymentsConfig {

    private CryptoBotSection cryptobot = new CryptoBotSection();
    private PlategaSection platega = new PlategaSection();

    public CryptoBotSection getCryptobot() { return cryptobot; }
    public void setCryptobot(CryptoBotSection cryptobot) { this.cryptobot = cryptobot; }
    public PlategaSection getPlatega() { return platega; }
    public void setPlatega(PlategaSection platega) { this.platega = platega; }

    public static PaymentsConfig load(Path file, ModuleContext ctx) throws IOException {
        PaymentsConfig cfg = new PaymentsConfig();
        if (Files.exists(file)) {
            cfg.readYaml(file);
        } else {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, defaultYaml(), StandardCharsets.UTF_8);
        }

        // Environment overrides.
        String cbToken = ctx.env("CRYPTOBOT_TOKEN", "");
        if (!cbToken.isBlank()) {
            cfg.getCryptobot().setToken(cbToken);
            cfg.getCryptobot().setEnabled(true);
        }
        if (ctx.env("CRYPTOBOT_ENABLED", "").equalsIgnoreCase("false")) cfg.getCryptobot().setEnabled(false);

        String plMerchant = ctx.env("PLATEGA_MERCHANT_ID", "");
        String plSecret = ctx.env("PLATEGA_SECRET", "");
        if (!plMerchant.isBlank()) cfg.getPlatega().setMerchantId(plMerchant);
        if (!plSecret.isBlank()) {
            cfg.getPlatega().setSecret(plSecret);
            cfg.getPlatega().setEnabled(true);
        }
        if (ctx.env("PLATEGA_ENABLED", "").equalsIgnoreCase("false")) cfg.getPlatega().setEnabled(false);

        return cfg;
    }

    private void readYaml(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String section = "";
        for (String line : lines) {
            String withoutComments = stripComment(line);
            if (withoutComments.isBlank()) {
                continue;
            }

            String trimmed = withoutComments.trim();
            if (!startsWithWhitespace(withoutComments) && trimmed.endsWith(":")) {
                section = trimmed.substring(0, trimmed.length() - 1).trim();
                continue;
            }

            int colon = trimmed.indexOf(':');
            if (colon < 0 || section.isBlank()) {
                continue;
            }

            String key = trimmed.substring(0, colon).trim();
            String value = unquote(trimmed.substring(colon + 1).trim());
            apply(section, key, value);
        }
    }

    private void apply(String section, String key, String value) {
        if ("cryptobot".equals(section)) {
            switch (key) {
                case "enabled" -> cryptobot.setEnabled(Boolean.parseBoolean(value));
                case "token" -> cryptobot.setToken(value);
                case "baseUrl" -> cryptobot.setBaseUrl(value);
                case "fiat" -> cryptobot.setFiat(value);
                default -> { }
            }
            return;
        }

        if ("platega".equals(section)) {
            switch (key) {
                case "enabled" -> platega.setEnabled(Boolean.parseBoolean(value));
                case "merchantId" -> platega.setMerchantId(value);
                case "secret" -> platega.setSecret(value);
                case "baseUrl" -> platega.setBaseUrl(value);
                case "paymentMethod" -> platega.setPaymentMethod(parseInt(value, platega.getPaymentMethod()));
                default -> { }
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean startsWithWhitespace(String value) {
        return !value.isEmpty() && Character.isWhitespace(value.charAt(0));
    }

    private static String stripComment(String value) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (ch == '#' && !singleQuoted && !doubleQuoted) {
                return value.substring(0, i);
            }
        }
        return value;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String defaultYaml() {
        return """
                # Payment gateways. Environment variables override these values.
                cryptobot:
                  enabled: false
                  token: ""
                  baseUrl: "https://pay.crypt.bot/api"
                  fiat: "RUB"

                platega:
                  enabled: false
                  merchantId: ""
                  secret: ""
                  baseUrl: "https://app.platega.io"
                  paymentMethod: 2
                """;
    }

    public static class CryptoBotSection {

        private boolean enabled = false;
        private String token = "";
        private String baseUrl = "https://pay.crypt.bot/api";
        private String fiat = "RUB";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getFiat() { return fiat; }
        public void setFiat(String fiat) { this.fiat = fiat; }
    }

    public static class PlategaSection {

        private boolean enabled = false;
        private String merchantId = "";
        private String secret = "";
        private String baseUrl = "https://app.platega.io";
        private int paymentMethod = 2;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(int paymentMethod) { this.paymentMethod = paymentMethod; }
    }
}
