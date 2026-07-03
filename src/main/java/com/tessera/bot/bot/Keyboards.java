package com.tessera.bot.bot;

import com.tessera.bot.service.Plans;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/** Builders for the inline keyboards used across the bot. */
public final class Keyboards {

    private Keyboards() {}

    public static InlineKeyboardMarkup mainMenu(boolean hasSubscription, boolean trialAvailable) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🛒 Купить / Продлить", Callbacks.MENU_BUY)));
        if (hasSubscription) {
            rows.add(List.of(btn("📱 Моя подписка", Callbacks.MENU_SUB)));
        } else if (trialAvailable) {
            rows.add(List.of(btn("🎁 Пробный период", Callbacks.MENU_TRIAL)));
        }
        rows.add(List.of(btn("👥 Реферальная программа", Callbacks.MENU_REFERRAL)));
        rows.add(List.of(btn("ℹ️ Помощь", Callbacks.MENU_HELP)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup plans() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Plans.Plan p : Plans.ALL) {
            rows.add(List.of(btn(
                    "%s — %d₽ (%dГБ)".formatted(p.title(), p.priceRub(), p.trafficGb()),
                    Callbacks.BUY_PREFIX + p.code())));
        }
        rows.add(List.of(btn("⬅️ Назад", Callbacks.MENU_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup backTo(String callback) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(btn("⬅️ Назад", callback)))
                .build();
    }

    public static InlineKeyboardMarkup subscriptionActions(String subscriptionUrl) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (subscriptionUrl != null && !subscriptionUrl.isBlank()) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("🔗 Открыть подписку").url(subscriptionUrl).build()));
        }
        rows.add(List.of(btn("🛒 Продлить", Callbacks.MENU_BUY)));
        rows.add(List.of(btn("⬅️ Назад", Callbacks.MENU_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup adminMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("📊 Статистика", Callbacks.ADMIN_STATS),
                         btn("❤️ Здоровье", Callbacks.ADMIN_HEALTH)));
        rows.add(List.of(btn("🖥 Ноды", Callbacks.ADMIN_NODES)));
        rows.add(List.of(btn("🔍 Найти пользователя", Callbacks.ADMIN_FIND_USER)));
        rows.add(List.of(btn("📢 Рассылка", Callbacks.ADMIN_BROADCAST)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardButton btn(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }
}
