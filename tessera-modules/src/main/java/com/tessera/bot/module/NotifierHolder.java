package com.tessera.bot.module;

import com.tessera.core.api.TelegramNotifier;

/**
 * Mutable {@link TelegramNotifier} handed to modules at load time (before the Telegram
 * bot exists). The application sets the real delegate once the bot is registered;
 * until then sends are silently dropped.
 */
public final class NotifierHolder implements TelegramNotifier {

    private volatile TelegramNotifier delegate;

    public void setDelegate(TelegramNotifier delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendMessage(long telegramId, String text) {
        TelegramNotifier d = delegate;
        if (d != null) {
            d.sendMessage(telegramId, text);
        }
    }
}