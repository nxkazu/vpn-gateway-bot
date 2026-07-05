package com.tessera.payments;

import com.tessera.core.api.BotModule;
import com.tessera.core.api.ModuleContext;
import com.tessera.payments.cryptobot.CryptoBotProvider;
import com.tessera.payments.platega.PlategaProvider;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Payment module: registers the CryptoBot and Platega merchant gates with the host
 * application. Configuration lives in {@code <configDir>/payments.yml};
 * environment variables override file values.
 */
public final class PaymentModule implements BotModule {

    private static final System.Logger LOG = System.getLogger(PaymentModule.class.getName());

    @Override
    public String id() {
        return "tessera-payment";
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public void init(ModuleContext ctx) throws Exception {
        Path cfgDir = ctx.configDirectory();
        Files.createDirectories(cfgDir);
        PaymentsConfig cfg = PaymentsConfig.load(cfgDir.resolve("payments.yml"), ctx);

        if (cfg.getCryptobot().isEnabled() && !cfg.getCryptobot().getToken().isBlank()) {
            ctx.registerPaymentProvider(new CryptoBotProvider(cfg.getCryptobot()));
            LOG.log(System.Logger.Level.INFO, "CryptoBot merchant gate enabled");
        } else {
            LOG.log(System.Logger.Level.INFO, "CryptoBot merchant gate disabled (missing token or disabled)");
        }

        if (cfg.getPlatega().isEnabled()
                && !cfg.getPlatega().getMerchantId().isBlank()
                && !cfg.getPlatega().getSecret().isBlank()) {
            ctx.registerPaymentProvider(new PlategaProvider(cfg.getPlatega()));
            LOG.log(System.Logger.Level.INFO, "Platega merchant gate enabled");
        } else {
            LOG.log(System.Logger.Level.INFO, "Platega merchant gate disabled (missing credentials or disabled)");
        }
    }
}
