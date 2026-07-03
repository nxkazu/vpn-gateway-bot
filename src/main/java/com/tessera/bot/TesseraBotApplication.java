package com.tessera.bot;

import com.tessera.bot.bot.TesseraBot;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.Database;
import com.tessera.bot.db.ReferralRepository;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Application entry point. Wires configuration, database, the Remnawave client,
 * the service layer, and the Telegram bot together, then starts long polling.
 */
public final class TesseraBotApplication {

    private static final Logger log = LoggerFactory.getLogger(TesseraBotApplication.class);

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnv();

        Database database = new Database(config);
        log.info("Running database migrations...");
        database.migrate();

        UserRepository userRepo = new UserRepository(database.dataSource());
        ReferralRepository referralRepo = new ReferralRepository(database.dataSource());

        RemnawaveClient panel = new RemnawaveClient(config);
        SubscriptionService subscriptions = new SubscriptionService(config, panel, userRepo);
        ReferralService referrals = new ReferralService(config, referralRepo, panel, subscriptions);

        TesseraBot bot = new TesseraBot(config, panel, userRepo, subscriptions, referrals);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        log.info("Tessera VPN bot '{}' started.", config.botUsername());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            database.close();
        }));

        // Keep the main thread alive.
        Thread.currentThread().join();
    }
}
