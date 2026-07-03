package com.tessera.bot.remnawave.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of {@code GET /api/system/stats} used by the admin dashboard. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemStats {
    public Users users;
    public Online onlineStats;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Users {
        public long totalUsers;
        public long statusCounts;
        public StatusCounts usersByStatus;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusCounts {
        public long ACTIVE;
        public long DISABLED;
        public long LIMITED;
        public long EXPIRED;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Online {
        public long onlineNow;
        public long lastDay;
        public long lastWeek;
        public long neverOnline;
    }
}
