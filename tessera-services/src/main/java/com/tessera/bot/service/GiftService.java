package com.tessera.bot.service;

import com.tessera.bot.db.GiftRepository;
import com.tessera.bot.db.Plans;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gift subscriptions: a paid plan converted into a one-time redeemable code
 * (e.g. {@code TSR-4F7K-9PQ2}) instead of an immediate activation for the buyer.
 *
 * <p>Lifecycle: the gift row is created PENDING together with its invoice; when the
 * invoice is confirmed paid, {@code PaymentService} flips it to ACTIVE and the buyer
 * receives the code plus a shareable {@code /start gift_<code>} deep link. Redemption
 * is a single atomic ACTIVE -> REDEEMED claim followed by the standard plan
 * activation for the redeemer, with compensation (release back to ACTIVE) if the
 * panel call fails.</p>
 */
@Slf4j
public class GiftService {

    /** Unambiguous alphabet: no 0/O, 1/I/L to keep codes easy to type from paper. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final String CODE_PREFIX = "TSR-";
    private static final int MAX_CODE_ATTEMPTS = 5;

    public sealed interface RedeemOutcome {
        record Redeemed(RemnawaveUser user, Plans.Plan plan) implements RedeemOutcome {}
        record Invalid() implements RedeemOutcome {}
        record NotPaidYet() implements RedeemOutcome {}
        record AlreadyRedeemed() implements RedeemOutcome {}
    }

    private final GiftRepository gifts;
    private final SubscriptionService subscriptions;
    private final SecureRandom random = new SecureRandom();

    public GiftService(GiftRepository gifts, SubscriptionService subscriptions) {
        this.gifts = gifts;
        this.subscriptions = subscriptions;
    }

    /** Creates a PENDING gift attached to a freshly created (unpaid) invoice. */
    public GiftRepository.Gift createPending(long buyerTelegramId, String planCode, long invoiceId) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generateCode();
            try {
                gifts.insertPending(code, buyerTelegramId, planCode, invoiceId);
                log.info("Gift created: buyer={} plan={} invoice={} code={}",
                        buyerTelegramId, planCode, invoiceId, code);
                return gifts.findByCode(code).orElseThrow();
            } catch (RuntimeException e) {
                last = e; // extremely unlikely code collision -> retry with a new code
            }
        }
        throw last != null ? last : new IllegalStateException("gift code generation failed");
    }

    /** Flips the gift of a paid invoice to ACTIVE and returns it (idempotent). */
    public Optional<GiftRepository.Gift> activateForInvoice(long invoiceId) {
        gifts.activateByInvoice(invoiceId);
        return gifts.findByInvoice(invoiceId);
    }

    /**
     * Redeems a gift code for {@code redeemerTelegramId}. The ACTIVE -> REDEEMED claim
     * is atomic, so a code can never activate two subscriptions; if the panel-side
     * activation fails after the claim, the gift is released back to ACTIVE.
     */
    public RedeemOutcome redeem(String rawCode, long redeemerTelegramId) {
        String code = normalize(rawCode);
        if (code.isBlank()) return new RedeemOutcome.Invalid();

        Optional<GiftRepository.Gift> found = gifts.findByCode(code);
        if (found.isEmpty()) return new RedeemOutcome.Invalid();
        GiftRepository.Gift gift = found.get();

        if ("PENDING".equals(gift.status())) return new RedeemOutcome.NotPaidYet();
        if (!"ACTIVE".equals(gift.status())) return new RedeemOutcome.AlreadyRedeemed();

        Plans.Plan plan = Plans.byCode(gift.planCode());
        if (plan == null) {
            log.error("Gift {} references unknown plan {}", code, gift.planCode());
            return new RedeemOutcome.Invalid();
        }

        if (!gifts.claim(code, redeemerTelegramId)) {
            return new RedeemOutcome.AlreadyRedeemed(); // lost the race to another redeemer
        }
        try {
            RemnawaveUser user = subscriptions.activatePlan(
                    redeemerTelegramId, plan.days(), plan.trafficBytes(), "gift:" + plan.code());
            log.info("Gift {} redeemed by {} (buyer={}, plan={})",
                    code, redeemerTelegramId, gift.buyerTelegramId(), plan.code());
            return new RedeemOutcome.Redeemed(user, plan);
        } catch (RuntimeException e) {
            gifts.release(code); // compensation: the code stays redeemable
            log.error("Gift {} activation failed for {}; code released", code, redeemerTelegramId, e);
            throw e;
        }
    }

    /** Most recent gifts bought by a user, newest first. */
    public List<GiftRepository.Gift> giftsOf(long buyerTelegramId, int limit) {
        return gifts.listByBuyer(buyerTelegramId, limit);
    }

    /** Deep link that opens the bot and redeems the code in one tap. */
    public static String shareLink(String botUsername, String code) {
        return "https://t.me/" + botUsername + "?start=gift_" + code;
    }

    /** Extracts a gift code from a {@code /start} payload, if present. */
    public static Optional<String> codeFromStartPayload(String payload) {
        if (payload == null) return Optional.empty();
        String trimmed = payload.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("gift_")) return Optional.empty();
        String code = trimmed.substring("gift_".length()).trim();
        return code.isBlank() ? Optional.empty() : Optional.of(code);
    }

    String generateCode() {
        return CODE_PREFIX + randomBlock() + "-" + randomBlock();
    }

    private String randomBlock() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String normalize(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    }
}
