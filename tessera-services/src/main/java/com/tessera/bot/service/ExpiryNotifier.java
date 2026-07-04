package com.tessera.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tessera.bot.db.NoticeRepository;
import com.tessera.i18n.Lang;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.i18n.Messages;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.util.Formatting;
import com.tessera.core.api.TelegramNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically scans Remnawave accounts and warns users whose subscription expires
 * within the configured window. Each user is warned at most once per expiry date
 * (deduplicated via the {@code expiry_notices} table), so extending the subscription
 * re-arms the notification for the new date.
 */
@Slf4j
@RequiredArgsConstructor
public final class ExpiryNotifier implements AutoCloseable {

    private static final int PAGE_SIZE = 200;

    private final RemnawaveClient panel;
    private final NoticeRepository notices;
    private final LocaleService locales;
    private final Messages messages;
    private final TelegramNotifier notifier;
    private final int notifyDays;

    private ScheduledExecutorService executor;

    /** Starts the notifier with the given check period (hours). */
    public void start(int periodHours) {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "expiry-notifier");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, 1, periodHours * 60L, TimeUnit.MINUTES);
        log.info("Expiry notifier started (every {}h, window {}d)", periodHours, notifyDays);
    }

    private void tick() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowEnd = now.plusDays(notifyDays);
        int start = 0;
        long total = Long.MAX_VALUE;
        int notified = 0;
        try {
            while (start < total) {
                JsonNode page = panel.listUsers(PAGE_SIZE, start);
                if (page == null) break;
                total = page.path("total").asLong(0);
                JsonNode users = page.path("users");
                if (!users.isArray() || users.isEmpty()) break;
                for (JsonNode u : users) {
                    notified += process(u, now, windowEnd) ? 1 : 0;
                }
                start += users.size();
            }
        } catch (Exception e) {
            log.warn("Expiry notifier: scan failed", e);
        }
        if (notified > 0) {
            log.info("Expiry notifier: sent {} expiry notice(s)", notified);
        }
    }

    private boolean process(JsonNode user, OffsetDateTime now, OffsetDateTime windowEnd) {
        try {
            long telegramId = user.path("telegramId").asLong(0);
            String expireRaw = user.path("expireAt").asText(null);
            if (telegramId == 0 || expireRaw == null || expireRaw.isBlank()) return false;
            OffsetDateTime expireAt = OffsetDateTime.parse(expireRaw);
            if (expireAt.isBefore(now) || expireAt.isAfter(windowEnd)) return false;

            LocalDate expireDate = expireAt.toLocalDate();
            if (!notices.tryInsert(telegramId, expireDate)) return false; // already notified

            Lang lang = locales.forUser(telegramId);
            notifier.sendMessage(telegramId,
                    messages.t(lang, "sub.expiring", Formatting.dateTime(expireAt)));
            return true;
        } catch (Exception e) {
            log.debug("Expiry notifier: skipping user record: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (executor != null) executor.shutdownNow();
    }
}