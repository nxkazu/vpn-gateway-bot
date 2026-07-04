package com.tessera.payments;

import com.tessera.core.api.ModuleContext;
import com.tessera.core.api.PaymentProvider;
import com.tessera.core.api.TelegramNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentModuleTest {

    @TempDir
    Path tempDir;

    @Test
    void doesNotRegisterPlategaWithoutSecret() throws Exception {
        Files.writeString(tempDir.resolve("payments.yml"), """
                platega:
                  enabled: true
                  merchantId: "merchant"
                  secret: ""
                """);
        TestContext ctx = new TestContext(tempDir, Map.of());

        new PaymentModule().init(ctx);

        assertEquals(List.of(), ctx.providerIds());
    }

    @Test
    void registersPlategaWithCompleteCredentials() throws Exception {
        TestContext ctx = new TestContext(tempDir, Map.of(
                "PLATEGA_MERCHANT_ID", "merchant",
                "PLATEGA_SECRET", "secret"
        ));

        new PaymentModule().init(ctx);

        assertEquals(List.of("platega"), ctx.providerIds());
    }

    private static final class TestContext implements ModuleContext {
        private final Path configDirectory;
        private final Map<String, String> env;
        private final List<PaymentProvider> providers = new ArrayList<>();

        private TestContext(Path configDirectory, Map<String, String> env) {
            this.configDirectory = configDirectory;
            this.env = env;
        }

        @Override
        public Path configDirectory() {
            return configDirectory;
        }

        @Override
        public String env(String key, String def) {
            return env.getOrDefault(key, def);
        }

        @Override
        public void registerPaymentProvider(PaymentProvider provider) {
            providers.add(provider);
        }

        @Override
        public DataSource dataSource() {
            return null;
        }

        @Override
        public TelegramNotifier notifier() {
            return null;
        }

        private List<String> providerIds() {
            return providers.stream().map(PaymentProvider::id).toList();
        }
    }
}
