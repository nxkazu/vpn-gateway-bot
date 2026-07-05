package com.tessera.ops;

import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.service.PaymentService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime health checks for DB, Remnawave and payment provider registration. */
public final class OpsHealthService {

    private final DataSource dataSource;
    private final RemnawaveClient panel;
    private final PaymentService payments;

    public OpsHealthService(DataSource dataSource, RemnawaveClient panel, PaymentService payments) {
        this.dataSource = dataSource;
        this.panel = panel;
        this.payments = payments;
    }

    public HealthReport check() {
        List<HealthReport.Check> checks = new ArrayList<>();
        checks.add(db());
        checks.add(remnawave());
        checks.add(payments());
        boolean up = checks.stream().allMatch(c -> "UP".equals(c.status()));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("paymentProviders", payments.providers().stream().map(p -> p.id()).toList());
        return new HealthReport(up ? "UP" : "DEGRADED", OffsetDateTime.now(ZoneOffset.UTC), checks, meta);
    }

    private HealthReport.Check db() {
        long start = System.nanoTime();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            return ok("database", start);
        } catch (Exception e) {
            return fail("database", start, e);
        }
    }

    private HealthReport.Check remnawave() {
        long start = System.nanoTime();
        try {
            panel.systemHealth();
            return ok("remnawave", start);
        } catch (Exception e) {
            return fail("remnawave", start, e);
        }
    }

    private HealthReport.Check payments() {
        long start = System.nanoTime();
        if (payments.hasProviders()) {
            return ok("payments", start);
        }
        return new HealthReport.Check("payments", "WARN", "no payment providers registered", elapsed(start));
    }

    private HealthReport.Check ok(String name, long start) {
        return new HealthReport.Check(name, "UP", "ok", elapsed(start));
    }

    private HealthReport.Check fail(String name, long start, Exception e) {
        return new HealthReport.Check(name, "DOWN", e.getMessage(), elapsed(start));
    }

    private long elapsed(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }
}
