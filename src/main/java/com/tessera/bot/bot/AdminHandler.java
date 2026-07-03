package com.tessera.bot.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.i18n.Lang;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.remnawave.RemnawaveException;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.remnawave.dto.SystemStats;
import com.tessera.bot.util.Formatting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/** Handles the administrative surface of the bot (admins only). */
@Slf4j
@RequiredArgsConstructor
public class AdminHandler {

    private final AppConfig config;
    private final RemnawaveClient panel;
    private final UserRepository users;
    private final Messages m;

    public boolean isAdmin(long telegramId) {
        return config.isAdmin(telegramId);
    }

    public Reply menu(Lang lang) {
        return Reply.of(m.t(lang, "admin.menu"), Keyboards.adminMenu(m, lang));
    }

    public Reply broadcastPrompt(Lang lang) {
        return Reply.of(m.t(lang, "admin.broadcast.prompt"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    public Reply findPrompt(Lang lang) {
        return Reply.of(m.t(lang, "admin.find.prompt"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    public Reply broadcastResult(Lang lang, int recipients) {
        return Reply.of(m.t(lang, "admin.broadcast.sent", recipients),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    public Reply stats(Lang lang) {
        try {
            SystemStats s = panel.systemStats();
            long botUsers = users.count();
            StringBuilder sb = new StringBuilder(m.t(lang, "admin.stats.title")).append("\n\n");
            if (s != null && s.users != null) {
                sb.append(m.t(lang, "admin.stats.total", s.users.totalUsers)).append("\n");
                if (s.users.usersByStatus != null) {
                    var bs = s.users.usersByStatus;
                    sb.append(m.t(lang, "admin.stats.active", bs.ACTIVE)).append("\n");
                    sb.append(m.t(lang, "admin.stats.expired", bs.EXPIRED)).append("\n");
                    sb.append(m.t(lang, "admin.stats.limited", bs.LIMITED)).append("\n");
                    sb.append(m.t(lang, "admin.stats.disabled", bs.DISABLED)).append("\n");
                }
            }
            if (s != null && s.onlineStats != null) {
                sb.append("\n").append(m.t(lang, "admin.stats.online", s.onlineStats.onlineNow)).append("\n");
                sb.append(m.t(lang, "admin.stats.online_day", s.onlineStats.lastDay)).append("\n");
            }
            sb.append("\n").append(m.t(lang, "admin.stats.bot_users", botUsers));
            return Reply.of(sb.toString(), Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            log.error("stats failed", e);
            return Reply.of(m.t(lang, "admin.stats.error", e.getMessage()),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
    }

    public Reply health(Lang lang) {
        try {
            JsonNode h = panel.systemHealth();
            return Reply.of(m.t(lang, "admin.health.title") + "\n\n```\n" + pretty(h) + "\n```",
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return Reply.of(m.t(lang, "admin.error", e.getMessage()),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
    }

    public Reply nodes(Lang lang) {
        try {
            JsonNode nodes = panel.getNodes();
            StringBuilder sb = new StringBuilder(m.t(lang, "admin.nodes.title")).append("\n\n");
            boolean any = false;
            if (nodes != null && nodes.isArray()) {
                for (JsonNode n : nodes) {
                    any = true;
                    String name = n.path("name").asText("?");
                    boolean connected = n.path("isConnected").asBoolean(false);
                    boolean disabled = n.path("isDisabled").asBoolean(false);
                    String icon = disabled ? "\u26AA" : (connected ? "\uD83D\uDFE2" : "\uD83D\uDD34");
                    sb.append(icon).append(" ").append(name).append("\n");
                }
            }
            if (!any) sb.append(m.t(lang, "admin.nodes.empty"));
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(Keyboards.btn(m.t(lang, "admin.btn.restart_all"), Callbacks.ADMIN_NODE_RESTART_ALL)));
            rows.add(List.of(Keyboards.btn(m.t(lang, "admin.btn.back"), Callbacks.ADMIN_MAIN)));
            return Reply.of(sb.toString(), InlineKeyboardMarkup.builder().keyboard(rows).build());
        } catch (Exception e) {
            return Reply.of(m.t(lang, "admin.error", e.getMessage()),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
    }

    public Reply restartAllNodes(Lang lang) {
        try {
            panel.restartAllNodes();
            return Reply.of(m.t(lang, "admin.nodes.restarted"),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_NODES));
        } catch (Exception e) {
            return Reply.of(m.t(lang, "admin.error", e.getMessage()),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_NODES));
        }
    }

    /** Resolves a free-form query (uuid / username / telegram id) to a user card with actions. */
    public Reply findUser(Lang lang, String query) {
        try {
            RemnawaveUser u = resolve(query.trim());
            if (u == null) {
                return Reply.of(m.t(lang, "admin.find.not_found", query),
                        Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
            }
            return Reply.of(renderUserCard(lang, u), userActionsKeyboard(lang, u.uuid));
        } catch (Exception e) {
            return Reply.of(m.t(lang, "admin.error", e.getMessage()),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
    }

    public Reply userAction(Lang lang, String action, String uuid) {
        try {
            switch (action) {
                case "enable" -> panel.enableUser(uuid);
                case "disable" -> panel.disableUser(uuid);
                case "revoke" -> panel.revokeUser(uuid);
                case "reset" -> panel.resetTraffic(uuid);
                case "delete" -> {
                    panel.deleteUser(uuid);
                    return Reply.of(m.t(lang, "admin.user.deleted"),
                            Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
                }
                default -> {
                    return Reply.of(m.t(lang, "admin.error", action),
                            Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
                }
            }
            RemnawaveUser u = panel.getByUuid(uuid);
            return Reply.of(m.t(lang, "admin.user.done") + "\n\n" + renderUserCard(lang, u),
                    userActionsKeyboard(lang, uuid));
        } catch (RemnawaveException e) {
            return Reply.of(m.t(lang, "admin.error", e.getMessage()),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
    }

    private RemnawaveUser resolve(String query) {
        if (query.matches("\\d+")) {
            RemnawaveUser byTg = panel.findByTelegramId(Long.parseLong(query));
            if (byTg != null) return byTg;
        }
        if (query.matches("[0-9a-fA-F-]{36}")) {
            try { return panel.getByUuid(query); } catch (RemnawaveException ignored) {}
        }
        try { return panel.getByUsername(query); } catch (RemnawaveException e) {
            if (e.isNotFound()) return null;
            throw e;
        }
    }

    private String renderUserCard(Lang lang, RemnawaveUser u) {
        long used = u.userTraffic != null ? u.userTraffic.usedTrafficBytes : 0L;
        String limit = (u.trafficLimitBytes == null || u.trafficLimitBytes == 0)
                ? m.t(lang, "traffic.unlimited") : Formatting.bytes(u.trafficLimitBytes);
        return m.t(lang, "admin.user.card",
                u.username, u.uuid,
                u.telegramId == null ? "\u2014" : u.telegramId,
                Formatting.status(m, lang, u.status),
                Formatting.dateTime(u.expireAt),
                Formatting.bytes(used), limit);
    }

    private InlineKeyboardMarkup userActionsKeyboard(Lang lang, String uuid) {
        String p = Callbacks.ADMIN_USER_PREFIX;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                Keyboards.btn(m.t(lang, "admin.user.btn.enable"), p + "enable:" + uuid),
                Keyboards.btn(m.t(lang, "admin.user.btn.disable"), p + "disable:" + uuid)));
        rows.add(List.of(
                Keyboards.btn(m.t(lang, "admin.user.btn.reset"), p + "reset:" + uuid),
                Keyboards.btn(m.t(lang, "admin.user.btn.revoke"), p + "revoke:" + uuid)));
        rows.add(List.of(Keyboards.btn(m.t(lang, "admin.user.btn.delete"), p + "delete:" + uuid)));
        rows.add(List.of(Keyboards.btn(m.t(lang, "admin.btn.back"), Callbacks.ADMIN_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private String pretty(JsonNode node) {
        return node == null ? "no data" : node.toPrettyString();
    }
}
