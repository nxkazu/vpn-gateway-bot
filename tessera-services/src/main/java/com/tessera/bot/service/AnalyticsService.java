package com.tessera.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tessera.bot.db.UserEventRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Best-effort product analytics. Event recording must never break user flows,
 * so persistence errors are logged and swallowed.
 */
@Slf4j
public final class AnalyticsService {

    private final UserEventRepository events;
    private final ObjectMapper json = new ObjectMapper();

    public AnalyticsService(UserEventRepository events) {
        this.events = events;
    }

    public void track(long telegramId, Event event) {
        track(telegramId, event, Map.of());
    }

    public void track(long telegramId, Event event, Map<String, ?> metadata) {
        try {
            events.record(telegramId, event.key(), toJson(metadata));
        } catch (Exception e) {
            log.debug("analytics event {} for {} was not recorded: {}", event.key(), telegramId, e.getMessage());
        }
    }

    private String toJson(Map<String, ?> metadata) throws JsonProcessingException {
        return metadata == null || metadata.isEmpty() ? "{}" : json.writeValueAsString(metadata);
    }

    public enum Event {
        START("start"),
        MENU_OPENED("menu_opened"),
        VPN_HUB_OPENED("vpn_hub_opened"),
        SETUP_OPENED("setup_opened"),
        SETUP_GUIDE_OPENED("setup_guide_opened"),
        BUY_OPENED("buy_opened"),
        PLAN_SELECTED("plan_selected"),
        INVOICE_CREATED("invoice_created"),
        PAYMENT_CONFIRMED("payment_confirmed"),
        TRIAL_ACTIVATED("trial_activated"),
        SUPPORT_CREATED("support_created"),
        REFERRAL_OPENED("referral_opened");

        private final String key;

        Event(String key) {
            this.key = key;
        }

        public String key() { return key; }
    }
}
