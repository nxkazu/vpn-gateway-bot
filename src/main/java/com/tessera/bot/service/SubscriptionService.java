package com.tessera.bot.service;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.db.model.BotUser;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.remnawave.dto.CreateUserRequest;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates VPN account lifecycle by combining the local bot database with the
 * Remnawave panel. A "subscription" is a Remnawave user whose {@code expireAt} is in the future.
 */
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final AppConfig config;
    private final RemnawaveClient panel;
    private final UserRepository users;

    /** Returns the Remnawave account for a Telegram user, resolving via stored uuid or telegramId. */
    public Optional<RemnawaveUser> currentSubscription(long telegramId) {
        Optional<BotUser> local = users.find(telegramId);
        if (local.isPresent() && local.get().remnawaveUuid != null) {
            try {
                return Optional.ofNullable(panel.getByUuid(local.get().remnawaveUuid));
            } catch (Exception e) {
                log.warn("Stored uuid lookup failed for {}, falling back to telegramId", telegramId, e);
            }
        }
        RemnawaveUser byTg = panel.findByTelegramId(telegramId);
        if (byTg != null) {
            users.linkRemnawave(telegramId, byTg.uuid, byTg.username);
        }
        return Optional.ofNullable(byTg);
    }

    /** Grants the one-time free trial. Returns the created account, or empty if trial not eligible. */
    public Optional<RemnawaveUser> grantTrial(long telegramId) {
        if (config.trialDays() <= 0) return Optional.empty();
        BotUser local = users.find(telegramId).orElse(null);
        if (local != null && local.trialUsed) return Optional.empty();
        if (currentSubscription(telegramId).isPresent()) return Optional.empty();

        RemnawaveUser created = provision(
                telegramId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(config.trialDays()),
                config.trialTrafficBytes(),
                "trial");
        users.markTrialUsed(telegramId);
        return Optional.of(created);
    }

    /** Activates or extends a paid plan for the given number of days and traffic limit. */
    public RemnawaveUser activatePlan(long telegramId, int days, long trafficLimitBytes, String planCode) {
        Optional<RemnawaveUser> existing = currentSubscription(telegramId);
        if (existing.isPresent()) {
            RemnawaveUser u = existing.get();
            OffsetDateTime base = u.expireAt != null && u.expireAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))
                    ? u.expireAt
                    : OffsetDateTime.now(ZoneOffset.UTC);
            RemnawaveUser updated = panel.updateExpiry(u.uuid, base.plusDays(days));
            if (!updated.isActive()) panel.enableUser(u.uuid);
            return updated;
        }
        return provision(telegramId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(days),
                trafficLimitBytes, planCode);
    }

    /** Creates a fresh Remnawave user linked to this Telegram id. */
    private RemnawaveUser provision(long telegramId, OffsetDateTime expireAt, long trafficLimitBytes, String tag) {
        CreateUserRequest req = new CreateUserRequest();
        req.username = "tg_" + telegramId;
        req.status = "ACTIVE";
        req.expireAt = expireAt;
        req.telegramId = telegramId;
        req.trafficLimitBytes = trafficLimitBytes > 0 ? trafficLimitBytes : 0L;
        req.trafficLimitStrategy = config.defaultTrafficStrategy();
        req.tag = tag == null ? null : tag.toUpperCase();
        if (config.defaultSquadUuid() != null && !config.defaultSquadUuid().isBlank()) {
            req.activeInternalSquads = List.of(config.defaultSquadUuid());
        }
        RemnawaveUser created = panel.createUser(req);
        users.linkRemnawave(telegramId, created.uuid, created.username);
        log.info("Provisioned Remnawave user {} for telegram {}", created.uuid, telegramId);
        return created;
    }
}
