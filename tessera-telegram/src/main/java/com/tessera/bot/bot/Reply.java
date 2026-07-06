package com.tessera.bot.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/** A rendered response: message text plus an optional inline keyboard. */
public record Reply(String text, InlineKeyboardMarkup keyboard, byte[] photo) {
    public static Reply of(String text, InlineKeyboardMarkup keyboard) {
        return new Reply(text, keyboard, null);
    }
    public static Reply of(String text) {
        return new Reply(text, null, null);
    }
    public static Reply of(String text, InlineKeyboardMarkup keyboard, byte[] photo) {
        return new Reply(text, keyboard, photo);
    }
}
