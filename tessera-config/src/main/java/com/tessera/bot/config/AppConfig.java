package com.tessera.bot.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final Map<Long, Set<AdminRole>> adminRoles;

    private final Long supportGroupId;
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
    private final boolean starsEnabled;
    private final double starsRubPerStar;

    private final String opsBind;
    private final boolean opsEnabled;
    private final int opsPort;
    private final String opsToken;
    private final boolean opsHealthPublic;
    private final String paymentWebhookSecret;
    private final int opsAlertIntervalSeconds;
    private final int backupScheduleHours;
    private final int backupRetentionDays;
    private final String backupDirectory;
    private final String pgDumpBin;

    private final int rateLimitMessagesPerMinute;
    private final int rateLimitCallbacksPerMinute;
    private final int rateLimitInvoicesPerHour;
    private final int rateLimitSupportPerHour;

    private final boolean webappEnabled;
    private final String webappBind;
    private final int webappPort;
    private final String webappPublicUrl;
    private final int webappInitDataMaxAgeSeconds;
    private final int webappDeviceResetLimitPerDay;

    private AppConfig(Builder b) {
        this.botToken = b.botToken;
        this.botUsername = b.botUsername;
        this.adminIds = b.adminIds;
        this.adminRoles = b.adminRoles;
        this.remnawaveBaseUrl = b.remnawaveBaseUrl;
        this.remnawaveToken = b.remnawaveToken;
        this.supportGroupId = b.supportGroupId;
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
        this.starsEnabled = b.starsEnabled;
        this.starsRubPerStar = b.starsRubPerStar;
        this.opsBind = b.opsBind;
        this.opsEnabled = b.opsEnabled;
        this.opsPort = b.opsPort;
        this.opsToken = b.opsToken;
        this.opsHealthPublic = b.opsHealthPublic;
        this.paymentWebhookSecret = b.paymentWebhookSecret;
        this.opsAlertIntervalSeconds = b.opsAlertIntervalSeconds;
        this.backupScheduleHours = b.backupScheduleHours;
        this.backupRetentionDays = b.backupRetentionDays;
        this.backupDirectory = b.backupDirectory;
        this.pgDumpBin = b.pgDumpBin;
        this.rateLimitMessagesPerMinute = b.rateLimitMessagesPerMinute;
        this.rateLimitCallbacksPerMinute = b.rateLimitCallbacksPerMinute;
        this.rateLimitInvoicesPerHour = b.rateLimitInvoicesPerHour;
        this.rateLimitSupportPerHour = b.rateLimitSupportPerHour;
        this.webappEnabled = b.webappEnabled;
        this.webappBind = b.webappBind;
        this.webappPort = b.webappPort;
        this.webappPublicUrl = b.webappPublicUrl;
        this.webappInitDataMaxAgeSeconds = b.webappInitDataMaxAgeSeconds;
        this.webappDeviceResetLimitPerDay = b.webappDeviceResetLimitPerDay;
    }

    public static AppConfig fromEnv() {
        Builder b = new Builder();
        b.botToken = required("BOT_TOKEN");
        b.botUsername = required("BOT_USERNAME");
        b.adminRoles = parseAdminRoles();
        b.adminIds = b.adminRoles.keySet();

        b.remnawaveBaseUrl = stripTrailingSlash(required("REMNAWAVE_BASE_URL"));
        b.remnawaveToken = required("REMNAWAVE_TOKEN");
        String sgi = env("SUPPORT_GROUP_ID", null);
        b.supportGroupId = sgi != null ? Long.parseLong(sgi) : null;
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
        b.starsEnabled = Boolean.parseBoolean(env("STARS_ENABLED", "true"));
        b.starsRubPerStar = Double.parseDouble(env("STARS_RUB_PER_STAR", "1.6"));
        b.opsBind = env("OPS_BIND", "0.0.0.0");
        b.opsEnabled = Boolean.parseBoolean(env("OPS_ENABLED", "true"));
        b.opsPort = Integer.parseInt(env("OPS_PORT", "9090"));
        b.opsToken = env("OPS_TOKEN", "");
        b.opsHealthPublic = Boolean.parseBoolean(env("OPS_HEALTH_PUBLIC", "true"));
        b.paymentWebhookSecret = env("PAYMENT_WEBHOOK_SECRET", "");
        b.opsAlertIntervalSeconds = Integer.parseInt(env("OPS_ALERT_INTERVAL_SECONDS", "300"));
        b.backupScheduleHours = Integer.parseInt(env("BACKUP_SCHEDULE_HOURS", "24"));
        b.backupRetentionDays = Integer.parseInt(env("BACKUP_RETENTION_DAYS", "7"));
        b.backupDirectory = env("BACKUP_DIR", "backups");
        b.pgDumpBin = env("PG_DUMP_BIN", "pg_dump");
        b.rateLimitMessagesPerMinute = Integer.parseInt(env("RATE_LIMIT_MESSAGES_PER_MINUTE", "30"));
        b.rateLimitCallbacksPerMinute = Integer.parseInt(env("RATE_LIMIT_CALLBACKS_PER_MINUTE", "60"));
        b.rateLimitInvoicesPerHour = Integer.parseInt(env("RATE_LIMIT_INVOICES_PER_HOUR", "10"));
        b.rateLimitSupportPerHour = Integer.parseInt(env("RATE_LIMIT_SUPPORT_PER_HOUR", "20"));
        b.webappEnabled = Boolean.parseBoolean(env("WEBAPP_ENABLED", "true"));
        b.webappBind = env("WEBAPP_BIND", "0.0.0.0");
        b.webappPort = Integer.parseInt(env("WEBAPP_PORT", "8090"));
        b.webappPublicUrl = stripTrailingSlash(env("WEBAPP_PUBLIC_URL", ""));
        b.webappInitDataMaxAgeSeconds = Integer.parseInt(env("WEBAPP_INIT_DATA_MAX_AGE_SECONDS", "86400"));
        b.webappDeviceResetLimitPerDay = Integer.parseInt(env("WEBAPP_DEVICE_RESET_LIMIT_PER_DAY", "5"));
        return new AppConfig(b);
    }

    private static Map<Long, Set<AdminRole>> parseAdminRoles() {
        Map<Long, Set<AdminRole>> roles = new HashMap<>();
        addRoleList(roles, env("ADMIN_IDS", ""), AdminRole.SUPERADMIN);
        addRoleList(roles, env("ADMIN_SUPERADMIN_IDS", ""), AdminRole.SUPERADMIN);
        addRoleList(roles, env("ADMIN_SUPPORT_IDS", ""), AdminRole.SUPPORT);
        addRoleList(roles, env("ADMIN_BILLING_IDS", ""), AdminRole.BILLING);
        addRoleList(roles, env("ADMIN_OPS_IDS", ""), AdminRole.OPS);

        String matrix = env("ADMIN_ROLES", "");
        for (String entry : matrix.split(";")) {
            String item = entry.trim();
            if (item.isBlank()) continue;
            int sep = item.indexOf(':');
            if (sep < 1) continue;
            long telegramId = Long.parseLong(item.substring(0, sep).trim());
            for (String rawRole : item.substring(sep + 1).split(",")) {
                String normalized = rawRole.trim().toUpperCase().replace('-', '_');
                if (normalized.isBlank()) continue;
                roles.computeIfAbsent(telegramId, ignored -> new HashSet<>())
                        .add(AdminRole.valueOf(normalized));
            }
        }

        Map<Long, Set<AdminRole>> out = new HashMap<>();
        roles.forEach((id, rs) -> out.put(id, Set.copyOf(rs)));
        return Map.copyOf(out);
    }

    private static void addRoleList(Map<Long, Set<AdminRole>> roles, String csv, AdminRole role) {
        for (String raw : csv.split(",")) {
            String item = raw.trim();
            if (item.isEmpty()) continue;
            long telegramId = Long.parseLong(item);
            roles.computeIfAbsent(telegramId, ignored -> new HashSet<>()).add(role);
        }
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
    public Map<Long, Set<AdminRole>> adminRoles() { return adminRoles; }
    public Long supportGroupId() { return supportGroupId; }
    public boolean isAdmin(long telegramId) { return adminRoles.containsKey(telegramId); }
    public boolean hasRole(long telegramId, AdminRole role) {
        Set<AdminRole> roles = adminRoles.getOrDefault(telegramId, Set.of());
        return roles.contains(AdminRole.SUPERADMIN) || roles.contains(role);
    }
    public Set<Long> adminIdsWithRole(AdminRole role) {
        return adminRoles.keySet().stream()
                .filter(id -> hasRole(id, role))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String remnawaveBaseUrl() { return remnawaveBaseUrl; }
    public String remnawaveToken() { return remnawaveToken; }
    public String defaultSquadUuid() { return defaultSquadUuid; }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }
    public String dbHost() { return dbHost; }
    public int dbPort() { return dbPort; }
    public String dbName() { return dbName; }
    public String dbUser() { return dbUser; }
    public String dbPassword() { return dbPassword; }

    public int trialDays() { return trialDays; }
    public long trialTrafficBytes() { return (long) trialTrafficGb * 1024L * 1024L * 1024L; }
    public int referralRewardDays() { return referralRewardDays; }
    public String defaultTrafficStrategy() { return defaultTrafficStrategy; }
    public boolean allowFreeActivation() { return allowFreeActivation; }
    public boolean starsEnabled() { return starsEnabled; }
    public double starsRubPerStar() { return starsRubPerStar; }
    public String opsBind() { return opsBind; }
    public boolean opsEnabled() { return opsEnabled; }
    public int opsPort() { return opsPort; }
    public String opsToken() { return opsToken; }
    public boolean opsHealthPublic() { return opsHealthPublic; }
    public String paymentWebhookSecret() { return paymentWebhookSecret; }
    public int opsAlertIntervalSeconds() { return opsAlertIntervalSeconds; }
    public int backupScheduleHours() { return backupScheduleHours; }
    public int backupRetentionDays() { return backupRetentionDays; }
    public String backupDirectory() { return backupDirectory; }
    public String pgDumpBin() { return pgDumpBin; }
    public int rateLimitMessagesPerMinute() { return rateLimitMessagesPerMinute; }
    public int rateLimitCallbacksPerMinute() { return rateLimitCallbacksPerMinute; }
    public int rateLimitInvoicesPerHour() { return rateLimitInvoicesPerHour; }
    public int rateLimitSupportPerHour() { return rateLimitSupportPerHour; }
    public boolean webappEnabled() { return webappEnabled; }
    public String webappBind() { return webappBind; }
    public int webappPort() { return webappPort; }
    public String webappPublicUrl() { return webappPublicUrl; }
    public int webappInitDataMaxAgeSeconds() { return webappInitDataMaxAgeSeconds; }
    public int webappDeviceResetLimitPerDay() { return webappDeviceResetLimitPerDay; }

    public static final class Builder {
        private String botToken;
        private String botUsername;
        private Set<Long> adminIds;
        private Map<Long, Set<AdminRole>> adminRoles;
        private String remnawaveBaseUrl;
        private String remnawaveToken;
        private Long supportGroupId;
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
        private boolean starsEnabled;
        private double starsRubPerStar;
        private String opsBind;
        private boolean opsEnabled;
        private int opsPort;
        private String opsToken;
        private boolean opsHealthPublic;
        private String paymentWebhookSecret;
        private int opsAlertIntervalSeconds;
        private int backupScheduleHours;
        private int backupRetentionDays;
        private String backupDirectory;
        private String pgDumpBin;
        private int rateLimitMessagesPerMinute;
        private int rateLimitCallbacksPerMinute;
        private int rateLimitInvoicesPerHour;
        private int rateLimitSupportPerHour;
        private boolean webappEnabled;
        private String webappBind;
        private int webappPort;
        private String webappPublicUrl;
        private int webappInitDataMaxAgeSeconds;
        private int webappDeviceResetLimitPerDay;
    }
}
