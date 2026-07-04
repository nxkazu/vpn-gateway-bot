package com.tessera.payments;

import com.tessera.core.api.ModuleContext;
import com.tessera.core.api.PaymentProvider;
import com.tessera.core.api.TelegramNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentsConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDefaultConfigWhenMissing() throws Exception {
        Path file = tempDir.resolve("payments.yml");

        PaymentsConfig cfg = PaymentsConfig.load(file, new TestContext(Map.of()));

        assertTrue(Files.exists(file));
        assertFalse(cfg.getCryptobot().isEnabled());
        assertEquals("https://pay.crypt.bot/api", cfg.getCryptobot().getBaseUrl());
        assertEquals(2, cfg.getPlatega().getPaymentMethod());
    }

    @Test
    void readsYamlAndAppliesEnvironmentOverrides() throws Exception {
        Path file = tempDir.resolve("payments.yml");
        Files.writeString(file, """
                cryptobot:
                  enabled: false
                  token: "from-file"
                  baseUrl: "https://testnet-pay.crypt.bot/api"
                  fiat: "USD"
                platega:
                  enabled: false
                  merchantId: "merchant-from-file"
                  secret: "secret-from-file"
                  paymentMethod: 7
                """);

        PaymentsConfig cfg = PaymentsConfig.load(file, new TestContext(Map.of(
                "CRYPTOBOT_TOKEN", "from-env",
                "PLATEGA_SECRET", "secret-from-env"
        )));

        assertTrue(cfg.getCryptobot().isEnabled());
        assertEquals("from-env", cfg.getCryptobot().getToken());
        assertEquals("https://testnet-pay.crypt.bot/api", cfg.getCryptobot().getBaseUrl());
        assertEquals("USD", cfg.getCryptobot().getFiat());
        assertTrue(cfg.getPlatega().isEnabled());
        assertEquals("merchant-from-file", cfg.getPlatega().getMerchantId());
        assertEquals("secret-from-env", cfg.getPlatega().getSecret());
        assertEquals(7, cfg.getPlatega().getPaymentMethod());
    }

    private record TestContext(Map<String, String> env) implements ModuleContext {
        @Override
        public Path configDirectory() {
            return Path.of(".");
        }

        @Override
        public String env(String key, String def) {
            return env.getOrDefault(key, def);
        }

        @Override
        public void registerPaymentProvider(PaymentProvider provider) {
        }

        @Override
        public DataSource dataSource() {
            return null;
        }

        @Override
        public TelegramNotifier notifier() {
            return null;
        }
    }
}
