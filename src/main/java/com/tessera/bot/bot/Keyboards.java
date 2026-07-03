package com.tessera.bot.bot;

import com.tessera.bot.i18n.Lang;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.service.Plans;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/** Builders for the inline keyboards used across the bot. All labels are localized. */
public final class Keyboards {

    private Keyboards() {}

    public static InlineKeyboardMarkup mainMenu(Messages m, Lang lang,
                                                boolean hasSubscription, boolean trialAvailable) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn(m.t(lang, "btn.buy"), Callbacks.MENU_BUY)));
        if (hasSubscription) {
            rows.add(List.of(btn(m.t(lang, "btn.sub"), Callbacks.MENU_SUB)));
        } else if (trialAvailable) {
            rows.add(List.of(btn(m.t(lang, "btn.trial"), Callbacks.MENU_TRIAL)));
        }
        rows.add(List.of(btn(m.t(lang, "btn.referral"), Callbacks.MENU_REFERRAL)));
        rows.add(List.of(
                btn(m.t(lang, "btn.help"), Callbacks.MENU_HELP),
                btn(m.t(lang, "btn.language"), Callbacks.MENU_LANGUAGE)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup plans(Messages m, Lang lang) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Plans.Plan p : Plans.ALL) {
            rows.add(List.of(btn(
                    m.t(lang, "plan.button", p.title(), p.priceRub(), p.trafficGb()),
                    Callbacks.BUY_PREFIX + p.code())));
        }
        rows.add(List.of(btn(m.t(lang, "btn.back"), Callbacks.MENU_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup languages(Lang current) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Lang l : Lang.values()) {
            String label = (l == current ? "\u2705 " : "") + l.label();
            row.add(btn(label, Callbacks.LANG_PREFIX + l.code()));
            if (row.size() == 2) { rows.add(new ArrayList<>(row)); row.clear(); }
        }
        if (!row.isEmpty()) rows.add(row);
        rows.add(List.of(btn("\u2B05\uFE0F", Callbacks.MENU_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup backTo(Messages m, Lang lang, String callback) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(btn(m.t(lang, "btn.back"), callback)))
                .build();
    }

    public static InlineKeyboardMarkup subscriptionActions(Messages m, Lang lang, String subscriptionUrl) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (subscriptionUrl != null && !subscriptionUrl.isBlank()) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(m.t(lang, "btn.open_sub")).url(subscriptionUrl).build()));
        }
        rows.add(List.of(btn(m.t(lang, "btn.extend"), Callbacks.MENU_BUY)));
        rows.add(List.of(btn(m.t(lang, "btn.back"), Callbacks.MENU_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ---- admin (English labels via bundle; admin keys fall back to English) ----

    public static InlineKeyboardMarkup adminMenu(Messages m, Lang lang) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn(m.t(lang, "admin.btn.stats"), Callbacks.ADMIN_STATS),
                         btn(m.t(lang, "admin.btn.health"), Callbacks.ADMIN_HEALTH)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.nodes"), Callbacks.ADMIN_NODES)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.find"), Callbacks.ADMIN_FIND_USER)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.broadcast"), Callbacks.ADMIN_BROADCAST)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardButton btn(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }
}
