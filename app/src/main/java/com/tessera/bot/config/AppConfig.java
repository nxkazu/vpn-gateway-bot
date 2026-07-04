package com.tessera.bot.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable application configuration loaded from environment variables.
 * See {@code .env.example} for the full list of supported variables.
 */
public final class AppConfig {

    private final String botToken;
    private final String botUsername;
    private final Set<Long> adminIds;

    private final String remnawaveBaseUrl;
    private final String remnawaveToken;
    private final String defaultSquadUuid;

    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    private final int trialDays;
    private final int trialTrafficGb;
    private final int referralRewardDays;
    private final String defaultTrafficStrategy;
    private final boolean allowFreeActivation;

    private AppConfig(Builder b) {
        this.botToken = b.botToken;
        this.botUsername = b.botUsername;
        this.adminIds = b.adminIds;
        this.remnawaveBaseUrl = b.remnawaveBaseUrl;
        this.remnawaveToken = b.remnawaveToken;
        this.defaultSquadUuid = b.defaultSquadUuid;
        this.dbHost = b.dbHost;
        this.dbPort = b.dbPort;
        this.dbName = b.dbName;
        this.dbUser = b.dbUser;
        this.dbPassword = b.dbPassword;
        this.trialDays = b.trialDays;
        this.trialTrafficGb = b.trialTrafficGb;
        this.referralRewardDays = b.referralRewardDays;
        this.defaultTrafficStrategy = b.defaultTrafficStrategy;
        this.allowFreeActivation = b.allowFreeActivation;
    }

    public static AppConfig fromEnv() {
        Builder b = new Builder();
        b.botToken = required("BOT_TOKEN");
        b.botUsername = required("BOT_USERNAME");
        b.adminIds = Arrays.stream(env("ADMIN_IDS", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());

        b.remnawaveBaseUrl = stripTrailingSlash(required("REMNAWAVE_BASE_URL"));
        b.remnawaveToken = required("REMNAWAVE_TOKEN");
        b.defaultSquadUuid = env("REMNAWAVE_DEFAULT_SQUAD_UUID", "").trim();

        b.dbHost = env("DB_HOST", "localhost");
        b.dbPort = Integer.parseInt(env("DB_PORT", "5432"));
        b.dbName = env("DB_NAME", "tessera");
        b.dbUser = env("DB_USER", "tessera");
        b.dbPassword = required("DB_PASSWORD");

        b.trialDays = Integer.parseInt(env("TRIAL_DAYS", "3"));
        b.trialTrafficGb = Integer.parseInt(env("TRIAL_TRAFFIC_GB", "20"));
        b.referralRewardDays = Integer.parseInt(env("REFERRAL_REWARD_DAYS", "15"));
        b.defaultTrafficStrategy = env("DEFAULT_TRAFFIC_STRATEGY", "MONTH");
        b.allowFreeActivation = Boolean.parseBoolean(env("ALLOW_FREE_ACTIVATION", "false"));
        return new AppConfig(b);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String required(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return v;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public String botToken() { return botToken; }
    public String botUsername() { return botUsername; }
    public Set<Long> adminIds() { return adminIds; }
    public boolean isAdmin(long telegramId) { return adminIds.contains(telegramId); }

    public String remnawaveBaseUrl() { return remnawaveBaseUrl; }
    public String remnawaveToken() { return remnawaveToken; }
    public String defaultSquadUuid() { return defaultSquadUuid; }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }
    public String dbUser() { return dbUser; }
    public String dbPassword() { return dbPassword; }

    public int trialDays() { return trialDays; }
    public long trialTrafficBytes() { return (long) trialTrafficGb * 1024L * 1024L * 1024L; }
    public int referralRewardDays() { return referralRewardDays; }
    public String defaultTrafficStrategy() { return defaultTrafficStrategy; }
    public boolean allowFreeActivation() { return allowFreeActivation; }

    public static final class Builder {
        private String botToken;
        private String botUsername;
        private Set<Long> adminIds;
        private String remnawaveBaseUrl;
        private String remnawaveToken;
        private String defaultSquadUuid;
        private String dbHost;
        private int dbPort;
        private String dbName;
        private String dbUser;
        private String dbPassword;
        private int trialDays;
        private int trialTrafficGb;
        private int referralRewardDays;
        private String defaultTrafficStrategy;
        private boolean allowFreeActivation;
    }
}
