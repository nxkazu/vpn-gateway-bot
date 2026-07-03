package com.tessera.bot.service;

import java.util.List;

/** Static catalogue of purchasable plans. Extend or move to DB as billing evolves. */
public final class Plans {

    public record Plan(String code, String title, int days, long trafficGb, long priceRub) {
        public long trafficBytes() { return trafficGb * 1024L * 1024L * 1024L; }
    }

    public static final List<Plan> ALL = List.of(
            new Plan("m1", "1 месяц", 30, 100, 149),
            new Plan("m3", "3 месяца", 90, 300, 399),
            new Plan("m6", "6 месяцев", 180, 600, 749),
            new Plan("m12", "1 год", 365, 1200, 1290)
    );

    private Plans() {}

    public static Plan byCode(String code) {
        return ALL.stream().filter(p -> p.code().equals(code)).findFirst().orElse(null);
    }
}
