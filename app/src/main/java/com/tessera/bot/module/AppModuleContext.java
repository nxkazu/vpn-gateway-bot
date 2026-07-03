package com.tessera.bot.module;

import com.tessera.bot.service.PaymentService;
import com.tessera.core.api.ModuleContext;
import com.tessera.core.api.PaymentProvider;

import java.nio.file.Path;

/** Application-side implementation of the {@link ModuleContext} SPI. */
public final class AppModuleContext implements ModuleContext {

    private final Path configDirectory;
    private final PaymentService payments;

    public AppModuleContext(Path configDirectory, PaymentService payments) {
        this.configDirectory = configDirectory;
        this.payments = payments;
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }

    @Override
    public String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    @Override
    public void registerPaymentProvider(PaymentProvider provider) {
        payments.register(provider);
    }
}