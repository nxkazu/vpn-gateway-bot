package com.tessera.ops;

import com.tessera.bot.config.AdminRole;
import com.tessera.bot.config.AppConfig;
import com.tessera.core.api.TelegramNotifier;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sends Telegram alerts to admins when health state changes. */
public final class OpsAlertService implements AutoCloseable {

    private final AppConfig config;
    private final OpsHealthService health;
    private final TelegramNotifier notifier;
    private ScheduledExecutorService executor;
    private String lastStatus = "UNKNOWN";

    public OpsAlertService(AppConfig config, OpsHealthService health, TelegramNotifier notifier) {
        this.config = config;
        this.health = health;
        this.notifier = notifier;
    }

    public void start() {
        if (config.opsAlertIntervalSeconds() <= 0 || config.adminIdsWithRole(AdminRole.OPS).isEmpty()) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ops-alerts");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, 30, config.opsAlertIntervalSeconds(), TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            HealthReport report = health.check();
            if (!report.status().equals(lastStatus)) {
                lastStatus = report.status();
                if (!report.healthy()) {
                    alert("⚠️ Tessera health is " + report.status() + "\n\n" + details(report));
                } else {
                    alert("✅ Tessera health recovered: UP");
                }
            }
        } catch (Exception e) {
            alert("⚠️ Tessera health check failed: " + e.getMessage());
        }
    }

    public void alert(String text) {
        for (long adminId : config.adminIdsWithRole(AdminRole.OPS)) {
            notifier.sendMessage(adminId, text);
        }
    }

    private String details(HealthReport report) {
        StringBuilder sb = new StringBuilder();
        for (HealthReport.Check check : report.checks()) {
            if (!"UP".equals(check.status())) {
                sb.append(check.name()).append(": ").append(check.status())
                        .append(" - ").append(check.message()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public void close() {
        if (executor != null) executor.shutdownNow();
    }
}
