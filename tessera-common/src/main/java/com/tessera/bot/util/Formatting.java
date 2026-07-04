package com.tessera.bot.util;

import com.tessera.i18n.Lang;
import com.tessera.i18n.Messages;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Small display helpers for human-friendly messages. */
public final class Formatting {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneOffset.UTC);

    private Formatting() {}

    public static String bytes(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = (int) (Math.log(bytes) / Math.log(1024));
        i = Math.min(i, units.length - 1);
        double val = bytes / Math.pow(1024, i);
        return String.format("%.2f %s", val, units[i]);
    }

    public static String dateTime(OffsetDateTime dt) {
        return dt == null ? "\u2014" : DATE.format(dt);
    }

    /** Localized, emoji-prefixed subscription status label. */
    public static String status(Messages m, Lang lang, String status) {
        if (status == null) return m.t(lang, "status.unknown");
        return switch (status) {
            case "ACTIVE" -> m.t(lang, "status.active");
            case "DISABLED" -> m.t(lang, "status.disabled");
            case "LIMITED" -> m.t(lang, "status.limited");
            case "EXPIRED" -> m.t(lang, "status.expired");
            default -> status;
        };
    }

    public static String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!])", "\\\\$1");
    }
}
