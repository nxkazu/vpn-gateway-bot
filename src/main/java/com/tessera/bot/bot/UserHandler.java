package com.tessera.bot.bot;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.db.model.BotUser;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.service.Plans;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import com.tessera.bot.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Handles all non-admin user interactions. Returns rendered {@link Reply} objects. */
public class UserHandler {

    private static final Logger log = LoggerFactory.getLogger(UserHandler.class);

    private final AppConfig config;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final ReferralService referrals;

    public UserHandler(AppConfig config, UserRepository users,
                       SubscriptionService subscriptions, ReferralService referrals) {
        this.config = config;
        this.users = users;
        this.subscriptions = subscriptions;
        this.referrals = referrals;
    }

    public Reply start(long telegramId, String username, String firstName, String lang, String payload) {
        users.upsert(telegramId, username, firstName, lang);
        referrals.parseReferrerFromPayload(payload, telegramId)
                .ifPresent(refId -> {
                    users.upsert(refId, null, null, null); // ensure FK target exists
                    referrals.registerReferral(telegramId, refId);
                });

        String text = """
                👋 Добро пожаловать в *Tessera VPN*!

                Быстрый, стабильный и безопасный VPN.
                Выберите действие в меню ниже.
                """;
        return Reply.of(text, mainMenu(telegramId));
    }

    public Reply mainMenuReply(long telegramId) {
        return Reply.of("🏠 *Главное меню*", mainMenu(telegramId));
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup mainMenu(long telegramId) {
        boolean hasSub = subscriptions.currentSubscription(telegramId).isPresent();
        boolean trialAvailable = config.trialDays() > 0
                && users.find(telegramId).map(u -> !u.trialUsed).orElse(true)
                && !hasSub;
        return Keyboards.mainMenu(hasSub, trialAvailable);
    }

    public Reply buyMenu() {
        return Reply.of("🛒 *Выберите тариф:*", Keyboards.plans());
    }

    public Reply mySubscription(long telegramId) {
        Optional<RemnawaveUser> sub = subscriptions.currentSubscription(telegramId);
        if (sub.isEmpty()) {
            return Reply.of("У вас пока нет активной подписки. Оформите её в меню 🛒 *Купить*.",
                    Keyboards.backTo(Callbacks.MENU_MAIN));
        }
        RemnawaveUser u = sub.get();
        long used = u.userTraffic != null ? u.userTraffic.usedTrafficBytes : 0L;
        String limit = (u.trafficLimitBytes == null || u.trafficLimitBytes == 0)
                ? "безлимит" : Formatting.bytes(u.trafficLimitBytes);
        String text = """
                📱 *Ваша подписка*

                Статус: %s
                Действует до: `%s`
                Трафик: %s из %s
                """.formatted(
                Formatting.status(u.status),
                Formatting.dateTime(u.expireAt),
                Formatting.bytes(used),
                limit);
        return Reply.of(text, Keyboards.subscriptionActions(u.subscriptionUrl));
    }

    public Reply trial(long telegramId) {
        Optional<RemnawaveUser> created = subscriptions.grantTrial(telegramId);
        if (created.isEmpty()) {
            return Reply.of("Пробный период недоступен — возможно, вы уже им воспользовались.",
                    Keyboards.backTo(Callbacks.MENU_MAIN));
        }
        RemnawaveUser u = created.get();
        String text = """
                🎁 *Пробный период активирован!*

                Действует до: `%s`
                Нажмите кнопку ниже, чтобы получить конфигурацию.
                """.formatted(Formatting.dateTime(u.expireAt));
        return Reply.of(text, Keyboards.subscriptionActions(u.subscriptionUrl));
    }

    /**
     * Handles a plan "purchase". Payment integration is intentionally pluggable —
     * here the plan is activated directly; wire a payment provider in front of this
     * call to gate it behind a successful charge.
     */
    public Reply buyPlan(long telegramId, String planCode) {
        Plans.Plan plan = Plans.byCode(planCode);
        if (plan == null) {
            return Reply.of("Неизвестный тариф.", Keyboards.backTo(Callbacks.MENU_BUY));
        }
        RemnawaveUser u = subscriptions.activatePlan(telegramId, plan.days(), plan.trafficBytes(), plan.code());

        // Referral reward on (first) activation.
        referrals.rewardOnActivation(telegramId).ifPresent(refId ->
                log.info("Referral reward granted to {} for activation by {}", refId, telegramId));

        String text = """
                ✅ *Тариф «%s» активирован!*

                Действует до: `%s`
                """.formatted(plan.title(), Formatting.dateTime(u.expireAt));
        return Reply.of(text, Keyboards.subscriptionActions(u.subscriptionUrl));
    }

    public Reply referral(long telegramId) {
        String link = referrals.buildReferralLink(telegramId);
        long invited = referrals.countReferrals(telegramId);
        long earned = referrals.countRewardedDays(telegramId);
        String text = """
                👥 *Реферальная программа*

                Приглашайте друзей и получайте *%d дней* VPN за каждого,
                кто оформит подписку!

                Ваша ссылка:
                `%s`

                Приглашено: *%d*
                Начислено бонусных дней: *%d*
                """.formatted(config.referralRewardDays(), link, invited, earned);
        return Reply.of(text, Keyboards.backTo(Callbacks.MENU_MAIN));
    }

    public Reply help() {
        String text = """
                ℹ️ *Помощь*

                • 🛒 *Купить / Продлить* — оформить или продлить подписку
                • 📱 *Моя подписка* — статус, срок и трафик
                • 👥 *Реферальная программа* — пригласить друзей
                • 🎁 *Пробный период* — бесплатный доступ для новых пользователей

                По вопросам поддержки свяжитесь с администратором.
                """;
        return Reply.of(text, Keyboards.backTo(Callbacks.MENU_MAIN));
    }
}
