package com.tessera.bot.bot;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.GiftRepository;
import com.tessera.bot.db.InvoiceRepository;
import com.tessera.bot.db.UserRepository;
import com.tessera.i18n.Lang;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.i18n.Messages;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.service.GiftService;
import com.tessera.bot.service.PaymentService;
import com.tessera.bot.db.Plans;
import com.tessera.bot.service.AnalyticsService;
import com.tessera.bot.service.PromoService;
import com.tessera.bot.service.RateLimiter;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import com.tessera.bot.service.SupportService;
import com.tessera.bot.service.TelegramStarsProvider;
import com.tessera.bot.util.Formatting;
import com.tessera.bot.util.QRGenerator;
import com.tessera.experience.DevicePlatform;
import com.tessera.experience.ExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.time.Duration;

/** Handles all non-admin user interactions. Returns localized {@link Reply} objects. */
@Slf4j
@RequiredArgsConstructor
public class UserHandler {

    private final AppConfig config;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final ReferralService referrals;
    private final PaymentService payments;
    private final SupportService support;
    private final PromoService promos;
    private final LocaleService locales;
    private final Messages m;
    private final ExperienceService experience;
    private final AnalyticsService analytics;
    private final RateLimiter rateLimiter;
    private final GiftService gifts;

    private Lang lang(long telegramId) {
        return locales.forUser(telegramId);
    }

    public Reply start(long telegramId, String username, String firstName, String langCode, String payload) {
        users.upsert(telegramId, username, firstName, langCode);
        analytics.track(telegramId, AnalyticsService.Event.START,
                Map.of("payload", payload == null ? "" : payload));
        referrals.parseReferrerFromPayload(payload, telegramId)
                .ifPresent(refId -> {
                    users.upsert(refId, null, null, null); // ensure FK target exists
                    referrals.registerReferral(telegramId, refId);
                });
        // Gift deep link (t.me/Bot?start=gift_<code>): redeem immediately.
        Optional<String> giftCode = GiftService.codeFromStartPayload(payload);
        if (giftCode.isPresent()) {
            return giftRedeem(telegramId, giftCode.get());
        }
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "welcome"), mainMenu(telegramId, lang));
    }

    public Reply mainMenuReply(long telegramId) {
        analytics.track(telegramId, AnalyticsService.Event.MENU_OPENED);
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "menu.main"), mainMenu(telegramId, lang));
    }

    private InlineKeyboardMarkup mainMenu(long telegramId, Lang lang) {
        boolean hasSub = subscriptions.currentSubscription(telegramId).isPresent();
        boolean trialAvailable = config.trialDays() > 0
                && users.find(telegramId).map(u -> !u.trialUsed).orElse(true)
                && !hasSub;
        return Keyboards.mainMenu(m, lang, hasSub, trialAvailable, webAppUrl());
    }

    public Reply buyMenu(long telegramId) {
        analytics.track(telegramId, AnalyticsService.Event.BUY_OPENED);
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "buy.choose"), Keyboards.plans(m, lang));
    }

    public Reply mySubscription(long telegramId) {
        analytics.track(telegramId, AnalyticsService.Event.VPN_HUB_OPENED);
        Lang lang = lang(telegramId);
        Optional<RemnawaveUser> sub = subscriptions.currentSubscription(telegramId);
        if (sub.isEmpty()) {
            return Reply.of(m.t(lang, "sub.none"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        RemnawaveUser u = sub.get();
        ExperienceService.SubscriptionInsight insight = experience.insight(u);
        long used = u.userTraffic != null ? u.userTraffic.usedTrafficBytes : 0L;
        String limit = (u.trafficLimitBytes == null || u.trafficLimitBytes == 0)
                ? m.t(lang, "traffic.unlimited") : Formatting.bytes(u.trafficLimitBytes);
        String trafficLine = insight.unlimitedTraffic()
                ? m.t(lang, "sub.traffic.unlimited", Formatting.bytes(used))
                : m.t(lang, "sub.traffic.limited",
                        Formatting.bytes(used), limit, Formatting.bytes(insight.remainingBytes()),
                        insight.usagePercent(), insight.usageBar());
        String text = m.t(lang, "sub.card.v2",
                Formatting.status(m, lang, u.status),
                Formatting.dateTime(u.expireAt),
                insight.daysLeft(),
                trafficLine,
                m.t(lang, insight.advice().messageKey()));
        byte[] qrCode = null;
        try {
            if (u.subscriptionUrl != null && !u.subscriptionUrl.isBlank()) {
                qrCode = QRGenerator.generateQRCodeImage(u.subscriptionUrl, 512, 512);
            }
        } catch (Exception e) {
            log.warn("Failed to generate QR code for subscription {}", u.id, e);
        }
        return Reply.of(text, Keyboards.subscriptionActions(m, lang, u.subscriptionUrl, webAppUrl()), qrCode);
    }

    public Reply trial(long telegramId) {
        Lang lang = lang(telegramId);
        Optional<RemnawaveUser> created = subscriptions.grantTrial(telegramId);
        if (created.isEmpty()) {
            return Reply.of(m.t(lang, "trial.unavailable"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        RemnawaveUser u = created.get();
        analytics.track(telegramId, AnalyticsService.Event.TRIAL_ACTIVATED);
        return Reply.of(m.t(lang, "trial.activated", Formatting.dateTime(u.expireAt)),
                Keyboards.subscriptionActions(m, lang, u.subscriptionUrl, webAppUrl()));
    }

    /**
     * Handles plan selection. With payment providers registered (tessera-payment.jar)
     * the user first chooses a payment method; without any providers the plan is
     * activated directly (legacy/dev behaviour).
     */
    public Reply buyPlan(long telegramId, String planCode) {
        analytics.track(telegramId, AnalyticsService.Event.PLAN_SELECTED, Map.of("plan", planCode));
        Lang lang = lang(telegramId);
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return Reply.of(m.t(lang, "plan.unknown"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
        if (payments.hasProviders()) {
            return Reply.of(m.t(lang, "pay.choose", plan.title(), plan.priceRub()),
                    Keyboards.paymentMethods(m, lang, plan.code(), payments.providers(), true));
        }
        if (!config.allowFreeActivation()) {
            log.warn("Purchase blocked for user {}: no payment providers are registered", telegramId);
            return Reply.of(m.t(lang, "pay.provider.unavailable"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
        return activateDirectly(telegramId, lang, plan);
    }

    /** Creates an invoice with the chosen provider and shows pay / check buttons. */
    public Reply payWith(long telegramId, String providerId, String planCode, String promoCode) {
        Lang lang = lang(telegramId);
        if (!config.isAdmin(telegramId)
                && !rateLimiter.allow("invoice:" + telegramId,
                        config.rateLimitInvoicesPerHour(), Duration.ofHours(1))) {
            return Reply.of(m.t(lang, "rate.invoice"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return Reply.of(m.t(lang, "plan.unknown"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
        }
        try {
            PaymentService.CreateResult res = payments.createInvoice(telegramId, providerId, plan, promoCode);
            String text;
            if (TelegramStarsProvider.ID.equals(providerId)) {
                long stars = TelegramStarsProvider.starsAmount(res.priceRub() * 100L, config.starsRubPerStar());
                text = m.t(lang, "pay.invoice.stars", plan.title(), stars);
            } else {
                text = m.t(lang, "pay.invoice", plan.title(), res.priceRub());
            }
            return Reply.of(text, Keyboards.invoiceActions(m, lang, res.payUrl(), res.invoiceId()));
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
                return Reply.of(m.t(lang, "plan.activated", paid.plan().title(),
                                Formatting.dateTime(paid.user().expireAt)),
                        Keyboards.subscriptionActions(m, lang, paid.user().subscriptionUrl, webAppUrl()));
            }
            if (outcome instanceof PaymentService.CheckOutcome.GiftReady gift) {
                return Reply.of(m.t(lang, "gift.ready",
                                gift.plan().title(), gift.gift().code(), gift.shareLink()),
                        Keyboards.giftReady(m, lang, gift.shareLink()));
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
                Keyboards.subscriptionActions(m, lang, u.subscriptionUrl, webAppUrl()));
    }

    // --------------------------------------------------------------- gifts

    /** Gift hub: buy a plan as a gift or redeem a received code; lists the user's own gifts. */
    public Reply giftMenu(long telegramId) {
        analytics.track(telegramId, AnalyticsService.Event.GIFT_OPENED);
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "gift.menu") + renderMyGifts(telegramId, lang),
                Keyboards.giftMenu(m, lang));
    }

    /** Gift plan selected: choose a payment method (gifts always require payment). */
    public Reply giftPlan(long telegramId, String planCode) {
        analytics.track(telegramId, AnalyticsService.Event.PLAN_SELECTED,
                Map.of("plan", planCode, "kind", "gift"));
        Lang lang = lang(telegramId);
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return Reply.of(m.t(lang, "plan.unknown"), Keyboards.backTo(m, lang, Callbacks.MENU_GIFT));
        }
        if (!payments.hasProviders()) {
            return Reply.of(m.t(lang, "pay.provider.unavailable"),
                    Keyboards.backTo(m, lang, Callbacks.MENU_GIFT));
        }
        return Reply.of(m.t(lang, "gift.pay.choose", plan.title(), plan.priceRub()),
                Keyboards.giftPaymentMethods(m, lang, plan.code(), payments.providers()));
    }

    /** Creates a gift invoice with the chosen provider and shows pay / check buttons. */
    public Reply giftPayWith(long telegramId, String providerId, String planCode) {
        Lang lang = lang(telegramId);
        if (!config.isAdmin(telegramId)
                && !rateLimiter.allow("invoice:" + telegramId,
                        config.rateLimitInvoicesPerHour(), Duration.ofHours(1))) {
            return Reply.of(m.t(lang, "rate.invoice"), Keyboards.backTo(m, lang, Callbacks.MENU_GIFT));
        }
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return Reply.of(m.t(lang, "plan.unknown"), Keyboards.backTo(m, lang, Callbacks.MENU_GIFT));
        }
        try {
            PaymentService.CreateResult res = payments.createGiftInvoice(telegramId, providerId, plan, null);
            String text;
            if (TelegramStarsProvider.ID.equals(providerId)) {
                long stars = TelegramStarsProvider.starsAmount(res.priceRub() * 100L, config.starsRubPerStar());
                text = m.t(lang, "pay.invoice.stars", plan.title(), stars);
            } else {
                text = m.t(lang, "pay.invoice", plan.title(), res.priceRub());
            }
            return Reply.of(text, Keyboards.invoiceActions(m, lang, res.payUrl(), res.invoiceId()));
        } catch (Exception e) {
            log.error("createGiftInvoice failed: user={} provider={} plan={}", telegramId, providerId, planCode, e);
            return Reply.of(m.t(lang, "pay.provider.unavailable"), Keyboards.backTo(m, lang, Callbacks.MENU_GIFT));
        }
    }

    /** Asks the user to type the gift code they received. */
    public Reply giftRedeemPrompt(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "gift.redeem.prompt"), Keyboards.backTo(m, lang, Callbacks.MENU_GIFT));
    }

    /** Redeems a gift code (typed or from a deep link) and activates the plan for the redeemer. */
    public Reply giftRedeem(long telegramId, String rawCode) {
        Lang lang = lang(telegramId);
        GiftService.RedeemOutcome outcome;
        try {
            outcome = gifts.redeem(rawCode, telegramId);
        } catch (Exception e) {
            log.error("gift redeem failed: user={} code={}", telegramId, rawCode, e);
            return Reply.of(m.t(lang, "gift.error"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        if (outcome instanceof GiftService.RedeemOutcome.Redeemed redeemed) {
            analytics.track(telegramId, AnalyticsService.Event.GIFT_REDEEMED,
                    Map.of("plan", redeemed.plan().code()));
            return Reply.of(m.t(lang, "gift.redeemed",
                            redeemed.plan().title(), Formatting.dateTime(redeemed.user().expireAt)),
                    Keyboards.subscriptionActions(m, lang, redeemed.user().subscriptionUrl, webAppUrl()));
        }
        if (outcome instanceof GiftService.RedeemOutcome.NotPaidYet) {
            return Reply.of(m.t(lang, "gift.not_paid"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        if (outcome instanceof GiftService.RedeemOutcome.AlreadyRedeemed) {
            return Reply.of(m.t(lang, "gift.already_redeemed"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        return Reply.of(m.t(lang, "gift.invalid"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
    }

    private String renderMyGifts(long telegramId, Lang lang) {
        List<GiftRepository.Gift> mine;
        try {
            mine = gifts.giftsOf(telegramId, 5);
        } catch (Exception e) {
            log.warn("giftsOf failed for {}: {}", telegramId, e.getMessage());
            return "";
        }
        if (mine.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n").append(m.t(lang, "gift.list.header"));
        for (GiftRepository.Gift g : mine) {
            Plans.Plan plan = Plans.byCode(g.planCode());
            String title = plan != null ? plan.title() : g.planCode();
            sb.append('\n').append(m.t(lang, "gift.list.row",
                    g.code(), title, m.t(lang, giftStatusKey(g.status()))));
        }
        return sb.toString();
    }

    private static String giftStatusKey(String status) {
        return switch (status == null ? "" : status) {
            case "ACTIVE" -> "gift.status.active";
            case "REDEEMED" -> "gift.status.redeemed";
            default -> "gift.status.pending";
        };
    }

    // --------------------------------------------------------------- promo

    /** Outcome of applying a promo code: the reply to render, plus the validated code (if any). */
    public record PromoOutcome(Reply reply, boolean valid, String code) {}

    /** Asks the user to type their promo code. */
    public Reply promoPrompt(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "promo.prompt"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY));
    }

    /** Validates the typed promo code against the selected plan and re-renders the method chooser. */
    public PromoOutcome applyPromo(long telegramId, String planCode, String rawCode) {
        Lang lang = lang(telegramId);
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return new PromoOutcome(
                    Reply.of(m.t(lang, "plan.unknown"), Keyboards.backTo(m, lang, Callbacks.MENU_BUY)),
                    false, null);
        }
        Optional<PromoService.Promo> promo = promos.validate(rawCode, telegramId);
        if (promo.isEmpty()) {
            return new PromoOutcome(
                    Reply.of(m.t(lang, "promo.invalid"),
                            Keyboards.paymentMethods(m, lang, plan.code(), payments.providers(), true)),
                    false, null);
        }
        long discounted = PromoService.discountedRub(plan.priceRub(), promo.get().discountPercent());
        return new PromoOutcome(
                Reply.of(m.t(lang, "promo.applied",
                                promo.get().code(), promo.get().discountPercent(), discounted),
                        Keyboards.paymentMethods(m, lang, plan.code(), payments.providers(), false)),
                true, promo.get().code());
    }

    // ------------------------------------------------------------- support

    /** True when the user has an open support ticket (their plain messages continue it). */
    public boolean hasOpenTicket(long telegramId) {
        return support.hasOpenTicket(telegramId);
    }

    /** Asks the user to describe their issue (next text message goes into a ticket). */
    public Reply supportPrompt(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "support.prompt"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
    }

    /** Stores the user's message in their (new or open) ticket. */
    public Reply supportSubmit(long telegramId, String username, String text) {
        return supportSubmit(telegramId, username, text, null, null);
    }

    /** Stores the user's message (optionally with an attachment) in their (new or open) ticket. */
    public Reply supportSubmit(long telegramId, String username, String text,
                               String attachmentFileId, String attachmentName) {
        Lang lang = lang(telegramId);
        if (!config.isAdmin(telegramId)
                && !rateLimiter.allow("support:" + telegramId,
                        config.rateLimitSupportPerHour(), Duration.ofHours(1))) {
            return Reply.of(m.t(lang, "rate.support"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        SupportService.Submitted s = support.submitUserMessage(
                telegramId, username, text, attachmentFileId, attachmentName);
        analytics.track(telegramId, AnalyticsService.Event.SUPPORT_CREATED,
                Map.of("ticket", s.ticketId()));
        return Reply.of(m.t(lang, "support.sent", s.ticketId()),
                Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
    }

    // ---------------------------------------------------------- payment history

    /** Shows the user's most recent confirmed payments (trust & self-service). */
    public Reply paymentHistory(long telegramId) {
        analytics.track(telegramId, AnalyticsService.Event.PAYMENTS_VIEWED);
        Lang lang = lang(telegramId);
        List<InvoiceRepository.LedgerEntry> entries;
        try {
            entries = payments.history(telegramId, 10);
        } catch (Exception e) {
            log.warn("paymentHistory failed for {}: {}", telegramId, e.getMessage());
            entries = List.of();
        }
        if (entries.isEmpty()) {
            return Reply.of(m.t(lang, "payments.empty"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
        }
        StringBuilder sb = new StringBuilder(m.t(lang, "payments.title")).append("\n");
        for (InvoiceRepository.LedgerEntry e : entries) {
            Plans.Plan plan = Plans.byCode(e.planCode());
            String title = plan != null ? plan.title() : e.planCode();
            sb.append('\n').append(m.t(lang, "payments.row",
                    Formatting.date(e.createdAt()), title, formatAmount(e), providerLabel(e.provider())));
        }
        return Reply.of(sb.toString(), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
    }

    private String formatAmount(InvoiceRepository.LedgerEntry e) {
        long whole = e.amountCents() / 100;
        long frac = Math.abs(e.amountCents() % 100);
        String amount = frac == 0 ? String.valueOf(whole) : String.format("%d.%02d", whole, frac);
        return "RUB".equalsIgnoreCase(e.currency()) ? amount + "₽" : amount + " " + e.currency();
    }

    private String providerLabel(String providerId) {
        if (providerId == null || providerId.isBlank()) return "—";
        return payments.byId(providerId)
                .map(com.tessera.core.api.PaymentProvider::displayName)
                .orElse(providerId);
    }

    public Reply referral(long telegramId) {
        analytics.track(telegramId, AnalyticsService.Event.REFERRAL_OPENED);
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

    public Reply rateLimited(long telegramId) {
        Lang lang = lang(telegramId);
        return Reply.of(m.t(lang, "rate.limited"), Keyboards.backTo(m, lang, Callbacks.MENU_MAIN));
    }

    public Reply setupMenu(long telegramId) {
        analytics.track(telegramId, AnalyticsService.Event.SETUP_OPENED);
        Lang lang = lang(telegramId);
        boolean hasSub = subscriptions.currentSubscription(telegramId).isPresent();
        return Reply.of(m.t(lang, hasSub ? "setup.menu" : "setup.menu.no_sub"),
                Keyboards.setupPlatforms(m, lang));
    }

    public Reply setupGuide(long telegramId, String platformCode) {
        DevicePlatform platform = DevicePlatform.fromCode(platformCode);
        analytics.track(telegramId, AnalyticsService.Event.SETUP_GUIDE_OPENED,
                Map.of("platform", platform.code()));
        Lang lang = lang(telegramId);
        String subscriptionUrl = subscriptions.currentSubscription(telegramId)
                .map(u -> u.subscriptionUrl)
                .filter(url -> url != null && !url.isBlank())
                .orElse(null);
        String text = m.t(lang, "setup.guide.title", m.t(lang, platform.titleKey()))
                + "\n\n" + m.t(lang, platform.guideKey())
                + "\n\n" + m.t(lang, subscriptionUrl == null ? "setup.no_subscription" : "setup.open_hint");
        return Reply.of(text, Keyboards.setupGuide(m, lang, subscriptionUrl));
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

    private String webAppUrl() {
        return config.webappEnabled() ? config.webappPublicUrl() : "";
    }
}
