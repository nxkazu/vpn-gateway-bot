package com.tessera.bot.service;

import java.util.List;

/** Static catalogue of purchasable plans. Extend or move to DB as billing evolves. */
public final class Plans {

    public record Plan(String code, String title, int days, long trafficGb, long priceRub) {
        public long trafficBytes() { return trafficGb * 1024L * 1024L * 1024L; }
    }

    public static final List<Plan> ALL = List.of(
            new Plan("m1", "1 \u043C\u0435\u0441\u044F\u0446 / 1 month", 30, 100, 149),
            new Plan("m3", "3 \u043C\u0435\u0441\u044F\u0446\u0430 / 3 months", 90, 300, 399),
            new Plan("m6", "6 \u043C\u0435\u0441\u044F\u0446\u0435\u0432 / 6 months", 180, 600, 749),
            new Plan("m12", "1 \u0433\u043E\u0434 / 1 year", 365, 1200, 1290)
    );

    private Plans() {}

    public static Plan byCode(String code) {
        return ALL.stream().filter(p -> p.code().equals(code)).findFirst().orElse(null);
    }
}
