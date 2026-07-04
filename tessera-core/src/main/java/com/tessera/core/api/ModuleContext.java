package com.tessera.core.api;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Host-application services exposed to {@link BotModule}s. Deliberately narrow:
 * modules interact with the core through capability registration, not direct wiring.
 */
public interface ModuleContext {

    /** Directory where modules may keep their configuration files. */
    Path configDirectory();

    /** Reads an environment variable, returning {@code def} when absent or blank. */
    String env(String key, String def);

    /** Registers a payment provider with the host application. */
    void registerPaymentProvider(PaymentProvider provider);

    /** The application's Postgres connection pool (shared schema, Flyway-managed). */
    DataSource dataSource();

    /** Sends Telegram messages to users on behalf of the bot (available once the bot is up). */
    TelegramNotifier notifier();
}