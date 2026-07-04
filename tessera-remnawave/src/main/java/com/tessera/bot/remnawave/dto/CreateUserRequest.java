package com.tessera.bot.remnawave.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/** Request body for {@code POST /api/users}. Null fields are omitted from the JSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateUserRequest {
    public String username;
    public String status;
    public Long trafficLimitBytes;
    public String trafficLimitStrategy;
    public OffsetDateTime expireAt;
    public String description;
    public String tag;
    public Long telegramId;
    public String email;
    public Integer hwidDeviceLimit;
    public List<String> activeInternalSquads;

    public CreateUserRequest() {}
}
