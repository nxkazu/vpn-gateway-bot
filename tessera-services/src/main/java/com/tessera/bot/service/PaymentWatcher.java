package com.tessera.bot.service;

import com.tessera.bot.db.InvoiceRepository;
import com.tessera.bot.db.model.PaymentInvoice;
import com.tessera.i18n.Lang;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.i18n.Messages;
import com.tessera.bot.util.Formatting;
import com.tessera.core.api.TelegramNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background auto-confirmation of payments: periodically re-checks PENDING invoices
 * with their providers so the user does not have to press "I paid". On confirmation
 * the plan is activated (same idempotent path as the manual check) and the user is
 * notified in their language.
 */
@Slf4j
@RequiredArgsConstructor
public final class PaymentWatcher implements AutoCloseable {

    /** Only invoices younger than this are polled; older ones expire naturally. */
    private static final int MAX_AGE_HOURS = 24;

    private final InvoiceRepository invoices;
    private final PaymentService payments;
    private final LocaleService locales;
    private final Messages messages;
    private final TelegramNotifier notifier;

    private ScheduledExecutorService executor;

    /** Starts the watcher with the given polling period (seconds). */
    public void start(int periodSeconds) {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "payment-watcher");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, periodSeconds, periodSeconds, TimeUnit.SECONDS);
        log.info("Payment watcher started (every {}s)", periodSeconds);
    }

    private void tick() {
        List<PaymentInvoice> pending;
        List<PaymentInvoice> paidUnactivated;
        try {
            pending = invoices.findPending(MAX_AGE_HOURS);
            paidUnactivated = invoices.findPaidUnactivated(MAX_AGE_HOURS);
        } catch (Exception e) {
            log.warn("Payment watcher: failed to load invoices", e);
            return;
        }
        List<PaymentInvoice> toCheck = new java.util.ArrayList<>(pending);
        toCheck.addAll(paidUnactivated);

        for (PaymentInvoice inv : toCheck) {
            try {
                PaymentService.CheckOutcome outcome = payments.checkAndActivate(inv.telegramId(), inv.id());
                if (outcome instanceof PaymentService.CheckOutcome.Paid paid) {
                    Lang lang = locales.forUser(inv.telegramId());
                    notifier.sendMessage(inv.telegramId(), messages.t(lang, "pay.auto.confirmed",
                            paid.plan().title(), Formatting.dateTime(paid.user().expireAt)));
                    log.info("Payment watcher: invoice {} auto-confirmed for {}", inv.id(), inv.telegramId());
                } else if (outcome instanceof PaymentService.CheckOutcome.GiftReady gift) {
                    Lang lang = locales.forUser(inv.telegramId());
                    notifier.sendMessage(inv.telegramId(), messages.t(lang, "gift.auto.ready",
                            gift.plan().title(), gift.gift().code(), gift.shareLink()));
                    log.info("Payment watcher: gift invoice {} auto-confirmed for {}", inv.id(), inv.telegramId());
                }
            } catch (Exception e) {
                log.debug("Payment watcher: check failed for invoice {}: {}", inv.id(), e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (executor != null) executor.shutdownNow();
    }
}