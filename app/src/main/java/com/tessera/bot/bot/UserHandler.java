package com.tessera.bot.bot;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.i18n.Lang;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.service.PaymentService;
import com.tessera.bot.service.Plans;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import com.tessera.bot.util.Formatting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Optional;

/** Handles all non-admin user interactions. Returns localized {@link Reply} objects. */
@Slf4j
@RequiredArgsConstructor
public class UserHandler {

    private final AppConfig config;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final ReferralService referrals;
    private final PaymentService payments;
    private final LocaleService locales;
    private final Messages m;

    private Lang lang(long telegramId) {
        return locales.forUser(telegramId);
    }

    public Reply start(long telegramId, String username, String firstName, String langCode, String payload) {
        users.upsert(telegramId, username, firstName, langCode);
        referrals.parseReferrerFromPayload(payload, telegramId)
                .ifPresent(refId -> {
                    users.upsert(refId, null, null, null); // ensure FK target exists
                    referrals.registerReferral(telegramId, refId);
                });
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "welcome"), mainMenu(telegramId, lang));
    }

    public Reply mainMenuReply(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "menu.main"), mainMenu(telegramId, lang));
    }

    private InlineKeyboardMarkup mainMenu(long telegramId, Lang lang) {
        boolean hasSub = subscriptions.currentSubscription(telegramId).isPresent();
        boolean trialAvailable = config.trialDays() > 0
                && users.find(telegramId).map(u -> !u.trialUsed).orElse(true)
                && !hasSub;
        return Keyboards.mainMenu(m, lang, hasSub, trialAvailable);
    }

    public Reply buyMenu(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "buy.choose"), Keyboards.plans(m, lang));
    }

    public Reply mySubscription(long telegramId) {
        Lang lang = lang(telegramId);
        Optional<RemnawaveUser> sub = subscriptions.currentSubscription(telegramId);
        if (sub.isEmpty()) {
            return Reply.of(m.t(lang, "sub.none"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        RemnawaveUser u = sub.get();
        long used = u.userTraffic != null ? u.userTraffic.usedTrafficBytes : 0L;
        String limit = (u.trafficLimitBytes == null || u.trafficLimitBytes == 0)
                ? m.t(lang, "traffic.unlimited") : Formatting.bytes(u.trafficLimitBytes);
        String text = m.t(lang, "sub.card",
                Formatting.status(m, lang, u.status),
                Formatting.dateTime(u.expireAt),
                Formatting.bytes(used), limit);
        return Reply.of(text, Keyboards.subscriptionActions(m, lang, u.subscriptionUrl));
    }

    public Reply trial(long telegramId) {
        Lang lang = lang(telegramId);
        Optional<RemnawaveUser> created = subscriptions.grantTrial(telegramId);
        if (created.isEmpty()) {
            return Reply.of(m.t(lang, "trial.unavailable"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        RemnawaveUser u = created.get();
        return Reply.of(m.t(lang, "trial.activated", Formatting.dateTime(u.expireAt)),
                Keyboards.subscriptionActions(m, lang, u.subscriptionUrl));
    }

    /**
     * Handles plan selection. With payment providers registered (PaymentModule.jar)
     * the user first chooses a payment method; without any providers the plan is
     * activated directly (legacy/dev behaviour).
     */
    public Reply buyPlan(long telegramId, String planCode) {
        Lang lang = lang(telegramId);
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return Reply.of(m.t(lang, "plan.unknown"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
        if (payments.hasProviders()) {
            return Reply.of(m.t(lang, "pay.choose", plan.title(), plan.priceRub()),
                    Keyboards.paymentMethods(m, lang, plan.code(), payments.providers()));
        }
        return activateDirectly(telegramId, lang, plan);
    }

    /** Creates an invoice with the chosen provider and shows pay / check buttons. */
    public Reply payWith(long telegramId, String providerId, String planCode) {
        Lang lang = lang(telegramId);
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return Reply.of(m.t(lang, "plan.unknown"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
        try {
            PaymentService.CreateResult res = payments.createInvoice(telegramId, providerId, plan);
            return Reply.of(m.t(lang, "pay.invoice", plan.title(), plan.priceRub()),
                    Keyboards.invoiceActions(m, lang, res.payUrl(), res.invoiceId()));
        } catch (Exception e) {
            log.error("createInvoice failed: user={} provider={} plan={}", telegramId, providerId, planCode, e);
            return Reply.of(m.t(lang, "pay.provider.unavailable"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
    }

    /** Re-checks an invoice; on confirmed payment the plan is activated. */
    public Reply checkPayment(long telegramId, long invoiceId) {
        Lang lang = lang(telegramId);
        try {
            PaymentService.CheckOutcome outcome = payments.checkAndActivate(telegramId, invoiceId);
            if (outcome instanceof PaymentService.CheckOutcome.Paid paid) {
                referralLog(telegramId);
                return Reply.of(m.t(lang, "plan.activated", paid.plan().title(),
                                Formatting.dateTime(paid.user().expireAt)),
                        Keyboards.subscriptionActions(m, lang, paid.user().subscriptionUrl));
            }
            if (outcome instanceof PaymentService.CheckOutcome.Pending pending) {
                return Reply.of(m.t(lang, "pay.pending"),
                        Keyboards.invoiceActions(m, lang, pending.invoice().payUrl(), pending.invoice().id()));
            }
            return Reply.of(m.t(lang, "pay.failed"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        } catch (Exception e) {
            log.error("checkPayment failed: user={} invoice={}", telegramId, invoiceId, e);
            return Reply.of(m.t(lang, "pay.provider.unavailable"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
    }

    private Reply activateDirectly(long telegramId, Lang lang, Plans.Plan plan) {
        RemnawaveUser u = subscriptions.activatePlan(telegramId, plan.days(), plan.trafficBytes(), plan.code());
        referrals.rewardOnActivation(telegramId).ifPresent(refId ->
                log.info("Referral reward granted to {} for activation by {}", refId, telegramId));
        return Reply.of(m.t(lang, "plan.activated", plan.title(), Formatting.dateTime(u.expireAt)),
                Keyboards.subscriptionActions(m, lang, u.subscriptionUrl));
    }

    private void referralLog(long telegramId) {
        // Referral reward is granted inside PaymentService on first confirmation.
    }

    public Reply referral(long telegramId) {
        Lang lang = lang(telegramId);
        String link = referrals.buildReferralLink(telegramId);
        long invited = referrals.countReferrals(telegramId);
        long earned = referrals.countRewardedDays(telegramId);
        return Reply.of(
                m.t(lang, "referral.card", config.referralRewardDays(), link, invited, earned),
                Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
    }

    public Reply help(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "help"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
    }

    public Reply languageMenu(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "lang.choose"), Keyboards.languages(lang));
    }

    public Reply setLanguage(long telegramId, String code) {
        Lang chosen = Lang.fromCode(code);
        locales.setLanguage(telegramId, chosen);
        return Reply.of(m.t(chosen, "lang.changed"), mainMenu(telegramId, chosen));
    }
}