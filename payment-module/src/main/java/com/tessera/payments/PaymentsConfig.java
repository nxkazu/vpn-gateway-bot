package com.tessera.payments;

import com.tessera.core.api.ModuleContext;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;

import java.nio.file.Path;

/**
 * Payment module configuration (okaeri-configs, YAML). A default file is generated on
 * first start; environment variables override file values (12-factor friendly):
 * {@code CRYPTOBOT_ENABLED}, {@code CRYPTOBOT_TOKEN},
 * {@code PLATEGA_ENABLED}, {@code PLATEGA_MERCHANT_ID}, {@code PLATEGA_SECRET}.
 */
public class PaymentsConfig extends OkaeriConfig {

    @Comment("CryptoBot (Crypto Pay API) merchant gate")
    private CryptoBotSection cryptobot = new CryptoBotSection();

    @Comment("Platega merchant gate")
    private PlategaSection platega = new PlategaSection();

    public CryptoBotSection getCryptobot() { return cryptobot; }
    public void setCryptobot(CryptoBotSection cryptobot) { this.cryptobot = cryptobot; }
    public PlategaSection getPlatega() { return platega; }
    public void setPlatega(PlategaSection platega) { this.platega = platega; }

    public static PaymentsConfig load(Path file, ModuleContext ctx) {
        PaymentsConfig cfg = ConfigManager.create(PaymentsConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(file);
            it.saveDefaults();
            it.load(true);
        });

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

    public static class CryptoBotSection extends OkaeriConfig {

        @Comment("Enable the CryptoBot gate")
        private boolean enabled = false;

        @Comment("Crypto Pay API token: @CryptoBot -> Crypto Pay -> Create App")
        private String token = "";

        @Comment("API base URL (use https://testnet-pay.crypt.bot/api for testnet)")
        private String baseUrl = "https://pay.crypt.bot/api";

        @Comment("Fiat currency used for invoicing")
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

    public static class PlategaSection extends OkaeriConfig {

        @Comment("Enable the Platega gate")
        private boolean enabled = false;

        @Comment("Merchant id issued by Platega (sent as X-MerchantId)")
        private String merchantId = "";

        @Comment("Merchant secret issued by Platega (sent as X-Secret)")
        private String secret = "";

        @Comment("API base URL")
        private String baseUrl = "https://app.platega.io";

        @Comment("Platega payment method code (e.g. 2 = SBP)")
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