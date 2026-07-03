package com.tessera.bot.remnawave.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/** Subset of the Remnawave user object relevant to the bot. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemnawaveUser {
    public String uuid;
    public long id;
    public String shortUuid;
    public String username;
    public String status;
    public Long trafficLimitBytes;
    public String trafficLimitStrategy;
    public OffsetDateTime expireAt;
    public Long telegramId;
    public String email;
    public String description;
    public String tag;
    public String subscriptionUrl;
    public UserTraffic userTraffic;
    public java.util.List<Squad> activeInternalSquads;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Squad {
        public String uuid;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserTraffic {
        public long usedTrafficBytes;
        public long lifetimeUsedTrafficBytes;
        public OffsetDateTime onlineAt;
        public OffsetDateTime firstConnectedAt;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
}
