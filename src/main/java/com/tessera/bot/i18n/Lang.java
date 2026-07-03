package com.tessera.bot.i18n;

import java.util.Arrays;
import java.util.Optional;

/** Supported UI languages. {@code code} matches the ResourceBundle suffix and Telegram language codes. */
public enum Lang {
    EN("en", "English", "\uD83C\uDDEC\uD83C\uDDE7"),
    RU("ru", "Русский", "\uD83C\uDDF7\uD83C\uDDFA"),
    ES("es", "Español", "\uD83C\uDDEA\uD83C\uDDF8"),
    DE("de", "Deutsch", "\uD83C\uDDE9\uD83C\uDDEA"),
    FR("fr", "Français", "\uD83C\uDDEB\uD83C\uDDF7"),
    ZH("zh", "中文", "\uD83C\uDDE8\uD83C\uDDF3");

    public static final Lang DEFAULT = EN;

    private final String code;
    private final String displayName;
    private final String flag;

    Lang(String code, String displayName, String flag) {
        this.code = code;
        this.displayName = displayName;
        this.flag = flag;
    }

    public String code() { return code; }
    public String displayName() { return displayName; }
    public String flag() { return flag; }
    public String label() { return flag + " " + displayName; }

    /** Resolves a Lang from an arbitrary language code (e.g. Telegram's "en-US"), defaulting to EN. */
    public static Lang fromCode(String code) {
        return fromCodeOpt(code).orElse(DEFAULT);
    }

    public static Optional<Lang> fromCodeOpt(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String norm = code.toLowerCase().split("[-_]")[0];
        return Arrays.stream(values()).filter(l -> l.code.equals(norm)).findFirst();
    }
}
