package com.tessera.bot;

import com.tessera.bot.bot.TesseraBot;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.Database;
import com.tessera.bot.db.AuditRepository;
import com.tessera.bot.db.InvoiceRepository;
import com.tessera.bot.db.NoticeRepository;
import com.tessera.bot.db.PlanRepository;
import com.tessera.bot.db.PromoRepository;
import com.tessera.bot.db.ReferralRepository;
import com.tessera.bot.db.TicketRepository;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.module.AppModuleContext;
import com.tessera.bot.module.ModuleLoader;
import com.tessera.bot.module.NotifierHolder;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.service.ExpiryNotifier;
import com.tessera.bot.service.PaymentService;
import com.tessera.bot.service.PaymentWatcher;
import com.tessera.bot.service.Plans;
import com.tessera.bot.service.PromoService;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import com.tessera.bot.service.SupportService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.nio.file.Path;

/**
 * Application entry point. Wires configuration, database, the Remnawave client,
 * the service layer, i18n, background workers and pluggable modules together,
 * then starts long polling.
 *
 * Modules (standalone jars, e.g. PaymentModule.jar, AdminDashboard.jar) are discovered
 * in the directory given by {@code MODULES_DIR} (default {@code modules}) and initialized
 * before the bot starts, so their capabilities are available to handlers.
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
        PlanRepository planRepo = new PlanRepository(database.dataSource());
        TicketRepository ticketRepo = new TicketRepository(database.dataSource());
        PromoRepository promoRepo = new PromoRepository(database.dataSource());
        NoticeRepository noticeRepo = new NoticeRepository(database.dataSource());

        Plans.init(planRepo);

        Messages messages = new Messages();
        LocaleService locales = new LocaleService(userRepo);

        NotifierHolder notifier = new NotifierHolder();

        RemnawaveClient panel = new RemnawaveClient(config);
        SubscriptionService subscriptions = new SubscriptionService(config, panel, userRepo);
        ReferralService referrals = new ReferralService(config, referralRepo, panel, subscriptions);
        PromoService promos = new PromoService(promoRepo);
        PaymentService payments = new PaymentService(config, invoiceRepo, subscriptions, referrals, promos);
        SupportService support = new SupportService(ticketRepo, config.adminIds(), notifier);

        Path modulesDir = Path.of(env("MODULES_DIR", "modules"));
        Path modulesConfigDir = Path.of(env("MODULES_CONFIG_DIR", "config"));
        ModuleLoader moduleLoader = new ModuleLoader();
        moduleLoader.loadAll(modulesDir,
                new AppModuleContext(modulesConfigDir, payments, database.dataSource(), notifier));

        TesseraBot bot = new TesseraBot(config, panel, userRepo, auditRepo,
                subscriptions, referrals, payments, support, promos, locales, messages);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        notifier.setDelegate(bot::sendPlain);

        // Background workers (started after the bot is up so notifications can be delivered).
        PaymentWatcher paymentWatcher = null;
        int paymentCheckSeconds = Integer.parseInt(env("PAYMENT_CHECK_SECONDS", "60"));
        if (paymentCheckSeconds > 0) {
            paymentWatcher = new PaymentWatcher(invoiceRepo, payments, locales, messages, notifier);
            paymentWatcher.start(paymentCheckSeconds);
        }
        ExpiryNotifier expiryNotifier = null;
        int expiryCheckHours = Integer.parseInt(env("EXPIRY_CHECK_HOURS", "6"));
        int expiryNotifyDays = Integer.parseInt(env("EXPIRY_NOTIFY_DAYS", "3"));
        if (expiryCheckHours > 0 && expiryNotifyDays > 0) {
            expiryNotifier = new ExpiryNotifier(panel, noticeRepo, locales, messages, notifier, expiryNotifyDays);
            expiryNotifier.start(expiryCheckHours);
        }

        log.info("Tessera VPN bot '{}' started ({} payment provider(s), {} module(s)).",
                config.botUsername(), payments.providers().size(), moduleLoader.modules().size());

        PaymentWatcher finalWatcher = paymentWatcher;
        ExpiryNotifier finalExpiry = expiryNotifier;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            if (finalWatcher != null) finalWatcher.close();
            if (finalExpiry != null) finalExpiry.close();
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