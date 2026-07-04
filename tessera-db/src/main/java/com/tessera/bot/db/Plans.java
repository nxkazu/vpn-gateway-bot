package com.tessera.bot.db;

import com.tessera.bot.db.PlanRepository;

import java.util.List;

/**
 * Catalogue of purchasable plans, backed by the {@code plans} table so that plans can be
 * created/edited from the admin dashboard without redeploys. Falls back to the built-in
 * defaults until {@link #init} is called (or if the database is unavailable).
 */
public final class Plans {

    public record Plan(String code, String title, int days, long trafficGb, long priceRub) {
        public long trafficBytes() { return trafficGb * 1024L * 1024L * 1024L; }
    }

    private static final List<Plan> DEFAULTS = List.of(
            new Plan("m1", "1 \u043C\u0435\u0441\u044F\u0446 / 1 month", 30, 100, 149),
            new Plan("m3", "3 \u043C\u0435\u0441\u044F\u0446\u0430 / 3 months", 90, 300, 399),
            new Plan("m6", "6 \u043C\u0435\u0441\u044F\u0446\u0435\u0432 / 6 months", 180, 600, 749),
            new Plan("m12", "1 \u0433\u043E\u0434 / 1 year", 365, 1200, 1290)
    );

    private static volatile PlanRepository repo;

    private Plans() {}

    /** Wires the DB-backed repository; called once at startup. */
    public static void init(PlanRepository repository) {
        repo = repository;
    }

    /** Enabled plans in display order (DB-backed; defaults when the table is empty/unreachable). */
    public static List<Plan> all() {
        PlanRepository r = repo;
        if (r != null) {
            try {
                List<Plan> fromDb = r.findEnabled();
                if (!fromDb.isEmpty()) return fromDb;
            } catch (Exception ignored) {
                // fall through to defaults
            }
        }
        return DEFAULTS;
    }

    /** Resolves a plan by code, including disabled ones (old invoices must stay resolvable). */
    public static Plan byCode(String code) {
        PlanRepository r = repo;
        if (r != null) {
            try {
                Plan p = r.findByCode(code);
                if (p != null) return p;
            } catch (Exception ignored) {
                // fall through to defaults
            }
        }
        return DEFAULTS.stream().filter(p -> p.code().equals(code)).findFirst().orElse(null);
    }
}