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
        rows.add(List.of(btn(m.t(lang, "admin.btn.dashboard"), Callbacks.ADMIN_DASHBOARD)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.stats"), Callbacks.ADMIN_STATS),
                         btn(m.t(lang, "admin.btn.health"), Callbacks.ADMIN_HEALTH)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.users"), Callbacks.ADMIN_USERS),
                         btn(m.t(lang, "admin.btn.find"), Callbacks.ADMIN_FIND_USER)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.nodes"), Callbacks.ADMIN_NODES),
                         btn(m.t(lang, "admin.btn.squads"), Callbacks.ADMIN_SQUADS)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.broadcast"), Callbacks.ADMIN_BROADCAST),
                         btn(m.t(lang, "admin.createsub.btn"), Callbacks.ADMIN_CREATE_SUB)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.audit"), Callbacks.ADMIN_AUDIT),
                         btn(m.t(lang, "admin.btn.settings"), Callbacks.ADMIN_SETTINGS)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Broadcast recipient-segment chooser. */
    public static InlineKeyboardMarkup broadcastSegments(Messages m, Lang lang) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn(m.t(lang, "admin.broadcast.seg.all"), Callbacks.ADMIN_BC_PREFIX + "all")));
        rows.add(List.of(
                btn(m.t(lang, "admin.broadcast.seg.active"), Callbacks.ADMIN_BC_PREFIX + "active"),
                btn(m.t(lang, "admin.broadcast.seg.inactive"), Callbacks.ADMIN_BC_PREFIX + "inactive")));
        rows.add(List.of(btn(m.t(lang, "admin.btn.back"), Callbacks.ADMIN_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Confirm / cancel keyboard shown before a broadcast is actually sent. */
    public static InlineKeyboardMarkup broadcastConfirm(Messages m, Lang lang) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                btn(m.t(lang, "admin.btn.confirm"), Callbacks.ADMIN_BC_CONFIRM),
                btn(m.t(lang, "admin.btn.cancel"), Callbacks.ADMIN_BC_CANCEL)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Paginated user list. Each entry opens the user card; nav row + back. */
    public static InlineKeyboardMarkup userList(Messages m, Lang lang,
                                                List<String[]> entries, int page, boolean hasPrev, boolean hasNext) {
        // entries: array of {label, uuid}
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String[] e : entries) {
            rows.add(List.of(btn(e[0], Callbacks.ADMIN_USER_PREFIX + "card:" + e[1])));
        }
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (hasPrev) nav.add(btn(m.t(lang, "admin.btn.prev"), Callbacks.ADMIN_USERS_PAGE_PREFIX + (page - 1)));
        if (hasNext) nav.add(btn(m.t(lang, "admin.btn.next"), Callbacks.ADMIN_USERS_PAGE_PREFIX + (page + 1)));
        if (!nav.isEmpty()) rows.add(nav);
        rows.add(List.of(btn(m.t(lang, "admin.btn.back"), Callbacks.ADMIN_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Rich per-user admin card: lifecycle + extend + traffic + devices + squads. */
    public static InlineKeyboardMarkup adminUserCard(Messages m, Lang lang, String uuid) {
        String p = Callbacks.ADMIN_USER_PREFIX;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                btn(m.t(lang, "admin.user.btn.enable"), p + "enable:" + uuid),
                btn(m.t(lang, "admin.user.btn.disable"), p + "disable:" + uuid)));
        rows.add(List.of(
                btn(m.t(lang, "admin.user.btn.reset"), p + "reset:" + uuid),
                btn(m.t(lang, "admin.user.btn.revoke"), p + "revoke:" + uuid)));
        rows.add(List.of(
                btn(m.t(lang, "admin.user.btn.add30"), p + "adddays:" + uuid + ":30"),
                btn(m.t(lang, "admin.user.btn.add90"), p + "adddays:" + uuid + ":90"),
                btn(m.t(lang, "admin.user.btn.add_custom"), p + "adddaysx:" + uuid)));
        rows.add(List.of(
                btn(m.t(lang, "admin.user.btn.traffic"), p + "traffic:" + uuid),
                btn(m.t(lang, "admin.user.btn.hwid"), p + "hwid:" + uuid),
                btn(m.t(lang, "admin.user.btn.squads"), p + "squads:" + uuid)));
        rows.add(List.of(btn(m.t(lang, "admin.user.btn.delete"), p + "delete:" + uuid)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.back"), Callbacks.ADMIN_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Device (HWID) list with per-device remove buttons. */
    public static InlineKeyboardMarkup hwidList(Messages m, Lang lang, String uuid, List<String[]> devices) {
        // devices: {label, hwid}
        String p = Callbacks.ADMIN_USER_PREFIX;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String[] d : devices) {
            rows.add(List.of(btn(m.t(lang, "admin.hwid.btn.del", d[0]), p + "hwiddel:" + uuid + ":" + d[1])));
        }
        rows.add(List.of(btn(m.t(lang, "admin.btn.back"), p + "card:" + uuid)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Squad membership toggler for a user. */
    public static InlineKeyboardMarkup userSquads(Messages m, Lang lang, String uuid, List<String[]> squads) {
        // squads: {label, squadUuid, isMember("1"/"0")}
        String p = Callbacks.ADMIN_USER_PREFIX;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String[] s : squads) {
            boolean member = "1".equals(s[2]);
            String label = m.t(lang, member ? "admin.usersquads.member" : "admin.usersquads.nonmember", s[0]);
            rows.add(List.of(btn(label, p + "squadtog:" + uuid + ":" + s[1])));
        }
        rows.add(List.of(btn(m.t(lang, "admin.btn.back"), p + "card:" + uuid)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Per-node action keyboard (enable/disable/restart) + back to node list. */
    public static InlineKeyboardMarkup nodeCard(Messages m, Lang lang, String uuid) {
        String p = Callbacks.ADMIN_NODE_PREFIX;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                btn(m.t(lang, "admin.node.btn.enable"), p + "enable:" + uuid),
                btn(m.t(lang, "admin.node.btn.disable"), p + "disable:" + uuid),
                btn(m.t(lang, "admin.node.btn.restart"), p + "restart:" + uuid)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.back"), Callbacks.ADMIN_NODES)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Node list where each node opens its own card. */
    public static InlineKeyboardMarkup nodeList(Messages m, Lang lang, List<String[]> nodes) {
        // nodes: {label, uuid}
        String p = Callbacks.ADMIN_NODE_PREFIX;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String[] n : nodes) {
            rows.add(List.of(btn(n[0], p + "card:" + n[1])));
        }
        rows.add(List.of(btn(m.t(lang, "admin.btn.restart_all"), Callbacks.ADMIN_NODE_RESTART_ALL)));
        rows.add(List.of(btn(m.t(lang, "admin.btn.back"), Callbacks.ADMIN_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardButton btn(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }
}
