package com.tessera.core.api;

/** Lets modules send plain Telegram messages to users (e.g. support-ticket replies). */
@FunctionalInterface
public interface TelegramNotifier {

    /** Sends a plain text message to the given Telegram user. Best-effort, must not throw. */
    void sendMessage(long telegramId, String text);
}