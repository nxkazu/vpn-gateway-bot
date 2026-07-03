package com.tessera.bot.i18n;

import com.tessera.bot.db.UserRepository;
import lombok.RequiredArgsConstructor;

/** Resolves and persists the preferred {@link Lang} for a Telegram user. */
@RequiredArgsConstructor
public class LocaleService {

    private final UserRepository users;

    /** Stored preference wins; otherwise fall back to the user's Telegram language, then EN. */
    public Lang forUser(long telegramId) {
        return users.find(telegramId)
                .map(u -> {
                    if (u.locale != null) return Lang.fromCode(u.locale);
                    return Lang.fromCode(u.languageCode);
                })
                .orElse(Lang.DEFAULT);
    }

    public void setLanguage(long telegramId, Lang lang) {
        users.setLocale(telegramId, lang.code());
    }
}
