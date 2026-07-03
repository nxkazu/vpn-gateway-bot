package com.tessera.bot;

import com.tessera.bot.bot.TesseraBot;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.Database;
import com.tessera.bot.db.ReferralRepository;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Application entry point. Wires configuration, database, the Remnawave client,
 * the service layer, i18n, and the Telegram bot together, then starts long polling.
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

        Messages messages = new Messages();
        LocaleService locales = new LocaleService(userRepo);

        RemnawaveClient panel = new RemnawaveClient(config);
        SubscriptionService subscriptions = new SubscriptionService(config, panel, userRepo);
        ReferralService referrals = new ReferralService(config, referralRepo, panel, subscriptions);

        TesseraBot bot = new TesseraBot(config, panel, userRepo, subscriptions, referrals, locales, messages);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        log.info("Tessera VPN bot '{}' started.", config.botUsername());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            database.close();
        }));

        Thread.currentThread().join();
    }
}
