package com.tessera.bot.util;

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
        return dt == null ? "—" : DATE.format(dt);
    }

    public static String status(String status) {
        if (status == null) return "неизвестно";
        return switch (status) {
            case "ACTIVE" -> "🟢 Активна";
            case "DISABLED" -> "⚪ Отключена";
            case "LIMITED" -> "🟡 Лимит трафика";
            case "EXPIRED" -> "🔴 Истекла";
            default -> status;
        };
    }

    public static String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replaceAll("([_*\[\]()~`>#+\-=|{}.!])", "\\$1");
    }
}
