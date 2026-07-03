package com.tessera.bot.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.remnawave.RemnawaveException;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.remnawave.dto.SystemStats;
import com.tessera.bot.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/** Handles the administrative surface of the bot (admins only). */
public class AdminHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminHandler.class);

    private final AppConfig config;
    private final RemnawaveClient panel;
    private final UserRepository users;

    public AdminHandler(AppConfig config, RemnawaveClient panel, UserRepository users) {
        this.config = config;
        this.panel = panel;
        this.users = users;
    }

    public boolean isAdmin(long telegramId) {
        return config.isAdmin(telegramId);
    }

    public Reply menu() {
        return Reply.of("🛠 *Панель администратора Tessera*\n\nВыберите раздел:", Keyboards.adminMenu());
    }

    public Reply stats() {
        try {
            SystemStats s = panel.systemStats();
            long botUsers = users.count();
            StringBuilder sb = new StringBuilder("📊 *Статистика*\n\n");
            if (s != null && s.users != null) {
                sb.append("Всего пользователей в панели: *").append(s.users.totalUsers).append("*\n");
                if (s.users.usersByStatus != null) {
                    var bs = s.users.usersByStatus;
                    sb.append("• 🟢 Активны: ").append(bs.ACTIVE).append("\n");
                    sb.append("• 🔴 Истекли: ").append(bs.EXPIRED).append("\n");
                    sb.append("• 🟡 Лимит: ").append(bs.LIMITED).append("\n");
                    sb.append("• ⚪ Отключены: ").append(bs.DISABLED).append("\n");
                }
            }
            if (s != null && s.onlineStats != null) {
                sb.append("\n🌐 Онлайн сейчас: *").append(s.onlineStats.onlineNow).append("*\n");
                sb.append("За сутки: ").append(s.onlineStats.lastDay).append("\n");
            }
            sb.append("\n🤖 Пользователей бота: *").append(botUsers).append("*");
            return Reply.of(sb.toString(), Keyboards.backTo(Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            log.error("stats failed", e);
            return Reply.of("Не удалось получить статистику: " + e.getMessage(),
                    Keyboards.backTo(Callbacks.ADMIN_MAIN));
        }
    }

    public Reply health() {
        try {
            JsonNode h = panel.systemHealth();
            return Reply.of("❤️ *Здоровье системы*\n\n```\n" + pretty(h) + "\n```",
                    Keyboards.backTo(Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return Reply.of("Ошибка: " + e.getMessage(), Keyboards.backTo(Callbacks.ADMIN_MAIN));
        }
    }

    public Reply nodes() {
        try {
            JsonNode nodes = panel.getNodes();
            StringBuilder sb = new StringBuilder("🖥 *Ноды*\n\n");
            if (nodes != null && nodes.isArray()) {
                for (JsonNode n : nodes) {
                    String name = n.path("name").asText("?");
                    boolean connected = n.path("isConnected").asBoolean(false);
                    boolean disabled = n.path("isDisabled").asBoolean(false);
                    String icon = disabled ? "⚪" : (connected ? "🟢" : "🔴");
                    sb.append(icon).append(" ").append(name).append("\n");
                }
                if (!nodes.elements().hasNext()) sb.append("_Нет нод_");
            }
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(Keyboards.btn("♻️ Перезапустить все", Callbacks.ADMIN_NODE_RESTART_ALL)));
            rows.add(List.of(Keyboards.btn("⬅️ Назад", Callbacks.ADMIN_MAIN)));
            return Reply.of(sb.toString(), InlineKeyboardMarkup.builder().keyboard(rows).build());
        } catch (Exception e) {
            return Reply.of("Ошибка: " + e.getMessage(), Keyboards.backTo(Callbacks.ADMIN_MAIN));
        }
    }

    public Reply restartAllNodes() {
        try {
            panel.restartAllNodes();
            return Reply.of("♻️ Команда на перезапуск всех нод отправлена.",
                    Keyboards.backTo(Callbacks.ADMIN_NODES));
        } catch (Exception e) {
            return Reply.of("Ошибка: " + e.getMessage(), Keyboards.backTo(Callbacks.ADMIN_NODES));
        }
    }

    /** Resolves a free-form query (uuid / username / telegram id) to a user card with actions. */
    public Reply findUser(String query) {
        try {
            RemnawaveUser u = resolve(query.trim());
            if (u == null) {
                return Reply.of("Пользователь не найден: `" + query + "`",
                        Keyboards.backTo(Callbacks.ADMIN_MAIN));
            }
            return Reply.of(renderUserCard(u), userActionsKeyboard(u.uuid));
        } catch (Exception e) {
            return Reply.of("Ошибка поиска: " + e.getMessage(), Keyboards.backTo(Callbacks.ADMIN_MAIN));
        }
    }

    public Reply userAction(String action, String uuid) {
        try {
            switch (action) {
                case "enable" -> panel.enableUser(uuid);
                case "disable" -> panel.disableUser(uuid);
                case "revoke" -> panel.revokeUser(uuid);
                case "reset" -> panel.resetTraffic(uuid);
                case "delete" -> {
                    panel.deleteUser(uuid);
                    return Reply.of("🗑 Пользователь удалён.", Keyboards.backTo(Callbacks.ADMIN_MAIN));
                }
                default -> { return Reply.of("Неизвестное действие.", Keyboards.backTo(Callbacks.ADMIN_MAIN)); }
            }
            RemnawaveUser u = panel.getByUuid(uuid);
            return Reply.of("✅ Действие выполнено.\n\n" + renderUserCard(u), userActionsKeyboard(uuid));
        } catch (RemnawaveException e) {
            return Reply.of("Ошибка: " + e.getMessage(), Keyboards.backTo(Callbacks.ADMIN_MAIN));
        }
    }

    private RemnawaveUser resolve(String query) {
        // Try telegram id
        if (query.matches("\\d+")) {
            RemnawaveUser byTg = panel.findByTelegramId(Long.parseLong(query));
            if (byTg != null) return byTg;
        }
        // Try uuid
        if (query.matches("[0-9a-fA-F-]{36}")) {
            try { return panel.getByUuid(query); } catch (RemnawaveException ignored) {}
        }
        // Fallback: username
        try { return panel.getByUsername(query); } catch (RemnawaveException e) {
            if (e.isNotFound()) return null;
            throw e;
        }
    }

    private String renderUserCard(RemnawaveUser u) {
        long used = u.userTraffic != null ? u.userTraffic.usedTrafficBytes : 0L;
        String limit = (u.trafficLimitBytes == null || u.trafficLimitBytes == 0)
                ? "безлимит" : Formatting.bytes(u.trafficLimitBytes);
        return """
                👤 *Пользователь*

                Username: `%s`
                UUID: `%s`
                Telegram ID: `%s`
                Статус: %s
                До: `%s`
                Трафик: %s из %s
                """.formatted(
                u.username, u.uuid,
                u.telegramId == null ? "—" : u.telegramId,
                Formatting.status(u.status),
                Formatting.dateTime(u.expireAt),
                Formatting.bytes(used), limit);
    }

    private InlineKeyboardMarkup userActionsKeyboard(String uuid) {
        String p = Callbacks.ADMIN_USER_PREFIX;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                Keyboards.btn("🟢 Включить", p + "enable:" + uuid),
                Keyboards.btn("⚪ Отключить", p + "disable:" + uuid)));
        rows.add(List.of(
                Keyboards.btn("♻️ Сброс трафика", p + "reset:" + uuid),
                Keyboards.btn("🚫 Отозвать", p + "revoke:" + uuid)));
        rows.add(List.of(Keyboards.btn("🗑 Удалить", p + "delete:" + uuid)));
        rows.add(List.of(Keyboards.btn("⬅️ В админку", Callbacks.ADMIN_MAIN)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private String pretty(JsonNode node) {
        return node == null ? "нет данных" : node.toPrettyString();
    }
}
