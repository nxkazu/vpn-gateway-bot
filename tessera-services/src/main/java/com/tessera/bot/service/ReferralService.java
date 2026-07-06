package com.tessera.bot.service;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.ReferralRepository;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Referral program. Referral links are Telegram deep-links of the form
 * {@code https://t.me/<bot>?start=ref_<referrerId>}. A referrer earns
 * {@code REFERRAL_REWARD_DAYS} extra VPN days the first time a user they invited
 * activates a (paid) plan.
 */
@Slf4j
@RequiredArgsConstructor
public class ReferralService {

    private static final String PAYLOAD_PREFIX = "ref_";

    private final AppConfig config;
    private final ReferralRepository referrals;
    private final RemnawaveClient panel;
    private final SubscriptionService subscriptions;

    public String buildReferralLink(long telegramId) {
        return "https://t.me/" + config.botUsername() + "?start=" + PAYLOAD_PREFIX + telegramId;
    }

    /** Parses the {@code /start} payload and returns the referrer id if present and valid. */
    public Optional<Long> parseReferrerFromPayload(String payload, long newUserId) {
        if (payload == null || !payload.startsWith(PAYLOAD_PREFIX)) return Optional.empty();
        try {
            long referrer = Long.parseLong(payload.substring(PAYLOAD_PREFIX.length()));
            if (referrer == newUserId) return Optional.empty();
            return Optional.of(referrer);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Registers a pending referral link (before any reward is due). */
    public void registerReferral(long newUserId, long referrerId) {
        referrals.recordReferral(newUserId, referrerId);
        log.info("Registered referral: {} invited by {}", newUserId, referrerId);
    }

    /**
     * Called when a referred user activates a paid plan. If a not-yet-rewarded referrer exists,
     * grants the reward days to the referrer's VPN account and returns the rewarded referrer id.
     */
    public Optional<Long> rewardOnActivation(long referredId) {
        return rewardOnActivation(null, referredId);
    }

    public Optional<Long> rewardOnActivation(java.sql.Connection conn, long referredId) {
        int rewardDays = config.referralRewardDays();
        if (rewardDays <= 0) return Optional.empty();

        Optional<Long> referrer = referrals.claimReward(conn, referredId, rewardDays);
        if (referrer.isEmpty()) return Optional.empty();

        long referrerId = referrer.get();
        try {
            RemnawaveUser refAcc = subscriptions.currentSubscription(referrerId).orElse(null);
            if (refAcc != null) {
                OffsetDateTime base = refAcc.expireAt != null
                        && refAcc.expireAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))
                        ? refAcc.expireAt : OffsetDateTime.now(ZoneOffset.UTC);
                panel.updateExpiry(refAcc.uuid, base.plusDays(rewardDays));
                if (!refAcc.isActive()) panel.enableUser(refAcc.uuid);
            }
        } catch (Exception e) {
            log.warn("Failed to apply referral reward +{}d on Remnawave for referrer {}", rewardDays, referrerId, e);
        }
        return referrer;
    }

    public long countReferrals(long referrerId) { return referrals.countReferrals(referrerId); }
    public long countRewardedDays(long referrerId) { return referrals.countRewardedDays(referrerId); }
}
