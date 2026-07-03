package com.tessera.bot;

import com.tessera.bot.bot.TesseraBot;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.Database;
import com.tessera.bot.db.AuditRepository;
import com.tessera.bot.db.InvoiceRepository;
import com.tessera.bot.db.ReferralRepository;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.module.AppModuleContext;
import com.tessera.bot.module.ModuleLoader;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.service.PaymentService;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.nio.file.Path;

/**
 * Application entry point. Wires configuration, database, the Remnawave client,
 * the service layer, i18n, and pluggable modules together, then starts long polling.
 *
 * Modules (standalone jars, e.g. PaymentModule.jar) are discovered in the directory
 * given by {@code MODULES_DIR} (default {@code modules}) and initialized before the
 * bot starts, so their capabilities (payment providers, ...) are available to handlers.
 */
@Slf4j
public final class TesseraBotApplication {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnv();

        Database database = new Database(config);
        log.info("Running database migrations...");
        database.migrate();

        UserRepository userRepo = new UserRepository(database.dataSource());
        ReferralRepository referralRepo = new ReferralRepository(database.dataSource());
        AuditRepository auditRepo = new AuditRepository(database.dataSource());
        InvoiceRepository invoiceRepo = new InvoiceRepository(database.dataSource());

        Messages messages = new Messages();
        LocaleService locales = new LocaleService(userRepo);

        RemnawaveClient panel = new RemnawaveClient(config);
        SubscriptionService subscriptions = new SubscriptionService(config, panel, userRepo);
        ReferralService referrals = new ReferralService(config, referralRepo, panel, subscriptions);
        PaymentService payments = new PaymentService(config, invoiceRepo, subscriptions, referrals);

        Path modulesDir = Path.of(env("MODULES_DIR", "modules"));
        Path modulesConfigDir = Path.of(env("MODULES_CONFIG_DIR", "config"));
        ModuleLoader moduleLoader = new ModuleLoader();
        moduleLoader.loadAll(modulesDir, new AppModuleContext(modulesConfigDir, payments));

        TesseraBot bot = new TesseraBot(config, panel, userRepo, auditRepo,
                subscriptions, referrals, payments, locales, messages);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        log.info("Tessera VPN bot '{}' started ({} payment provider(s)).",
                config.botUsername(), payments.providers().size());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            moduleLoader.close();
            database.close();
        }));

        Thread.currentThread().join();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}