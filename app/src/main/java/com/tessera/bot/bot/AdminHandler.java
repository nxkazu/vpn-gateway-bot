package com.tessera.bot.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.AuditRepository;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.db.model.AdminAction;
import com.tessera.bot.i18n.Lang;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.remnawave.RemnawaveException;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import com.tessera.bot.remnawave.dto.SystemStats;
import com.tessera.bot.service.Plans;
import com.tessera.bot.service.SubscriptionService;
import com.tessera.bot.util.Formatting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Handles the administrative surface of the bot (admins only). */
@Slf4j
@RequiredArgsConstructor
public class AdminHandler {

    private static final int PAGE_SIZE = 8;

    private final AppConfig config;
    private final RemnawaveClient panel;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final AuditRepository audit;
    private final Messages m;

    // Short-lived caches so per-device / per-squad buttons carry a small index instead of a
    // 36-char uuid (Telegram limits callback_data to 64 bytes).
    private final Map<String, List<String>> hwidCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> squadCache = new ConcurrentHashMap<>();

    public boolean isAdmin(long telegramId) {
        return config.isAdmin(telegramId);
    }

    public Reply menu(Lang lang) {
        return Reply.of(m.t(lang, "admin.menu"), Keyboards.adminMenu(m, lang));
    }

    // ----------------------------------------------------------------- dashboard

    public Reply dashboard(Lang lang) {
        try {
            StringBuilder sb = new StringBuilder(m.t(lang, "admin.dash.title")).append("\n\n");
            SystemStats s = panel.systemStats();
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
                sb.append(m.t(lang, "admin.stats.online", s.onlineStats.onlineNow)).append("\n");
            }
            int total = 0, online = 0;
            JsonNode nodes = panel.getNodes();
            if (nodes != null && nodes.isArray()) {
                for (JsonNode n : nodes) {
                    total++;
                    if (n.path("isConnected").asBoolean(false)) online++;
                }
            }
            sb.append(m.t(lang, "admin.dash.nodes", total, online)).append("\n");
            sb.append(m.t(lang, "admin.stats.bot_users", users.count()));
            return Reply.of(sb.toString(), Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- statistics

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
            return Reply.of(m.t(lang, "admin.stats.error", e.getMessage()),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
    }

    public Reply health(Lang lang) {
        try {
            JsonNode health = panel.systemHealth();
            return Reply.of(m.t(lang, "admin.health.title") + "\n\n```\n" + pretty(health) + "\n```",
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- users list

    public Reply listUsers(Lang lang, int page) {
        try {
            if (page < 0) page = 0;
            int start = page * PAGE_SIZE;
            JsonNode resp = panel.listUsers(PAGE_SIZE, start);
            long total = resp != null && resp.has("total") ? resp.get("total").asLong() : 0L;
            JsonNode arr = resp == null ? null : resp.get("users");
            List<String[]> entries = new ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode u : arr) {
                    String uname = u.path("username").asText("?");
                    String uuid = u.path("uuid").asText("");
                    String icon = statusIcon(u.path("status").asText(""));
                    entries.add(new String[]{icon + " " + trim(uname, 28), uuid});
                }
            }
            int totalPages = (int) Math.max(1, Math.ceil(total / (double) PAGE_SIZE));
            boolean hasPrev = page > 0;
            boolean hasNext = (page + 1) < totalPages;
            String header = m.t(lang, "admin.users.title", page + 1, totalPages, total);
            if (entries.isEmpty()) {
                return Reply.of(header + "\n\n" + m.t(lang, "admin.users.empty"),
                        Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
            }
            return Reply.of(header + "\n\n" + m.t(lang, "admin.users.hint"),
                    Keyboards.userList(m, lang, entries, page, hasPrev, hasNext));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- find + card

    /** Resolves a free-text query (Telegram id / UUID / username) to a user card. */
    public Reply findUser(Lang lang, String query) {
        try {
            RemnawaveUser u = resolve(query.trim());
            if (u == null) {
                return Reply.of(m.t(lang, "admin.find.not_found", query),
                        Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
            }
            return Reply.of(renderUserCard(lang, u), Keyboards.adminUserCard(m, lang, u.uuid));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply userCard(Lang lang, String uuid) {
        try {
            RemnawaveUser u = panel.getByUuid(uuid);
            if (u == null) {
                return Reply.of(m.t(lang, "admin.find.not_found", uuid),
                        Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
            }
            return Reply.of(renderUserCard(lang, u), Keyboards.adminUserCard(m, lang, u.uuid));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply userAction(Lang lang, long adminId, String action, String uuid) {
        try {
            switch (action) {
                case "enable" -> panel.enableUser(uuid);
                case "disable" -> panel.disableUser(uuid);
                case "revoke" -> panel.revokeUser(uuid);
                case "reset" -> panel.resetTraffic(uuid);
                case "delete" -> {
                    panel.deleteUser(uuid);
                    audit.record(adminId, "user_delete", uuid, null);
                    return Reply.of(m.t(lang, "admin.user.deleted"),
                            Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
                }
                default -> {
                    return Reply.of(m.t(lang, "admin.error", action),
                            Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
                }
            }
            audit.record(adminId, "user_" + action, uuid, null);
            RemnawaveUser u = panel.getByUuid(uuid);
            return Reply.of(m.t(lang, "admin.user.done") + "\n\n" + renderUserCard(lang, u),
                    Keyboards.adminUserCard(m, lang, uuid));
        } catch (RemnawaveException e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- extend / traffic

    public Reply addDaysPrompt(Lang lang, String uuid) {
        return Reply.of(m.t(lang, "admin.user.adddays.prompt"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_USER_PREFIX + "card:" + uuid));
    }

    public Reply applyAddDays(Lang lang, long adminId, String uuid, int days) {
        try {
            RemnawaveUser u = subscriptions.adminAddDays(uuid, days);
            audit.record(adminId, "add_days", uuid, days + "d");
            return Reply.of(m.t(lang, "admin.user.added_days", days) + "\n\n" + renderUserCard(lang, u),
                    Keyboards.adminUserCard(m, lang, uuid));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply trafficPrompt(Lang lang, String uuid) {
        return Reply.of(m.t(lang, "admin.user.traffic.prompt"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_USER_PREFIX + "card:" + uuid));
    }

    public Reply applyTraffic(Lang lang, long adminId, String uuid, long gb) {
        try {
            RemnawaveUser u = subscriptions.adminSetTrafficGb(uuid, gb);
            audit.record(adminId, "set_traffic", uuid, gb + "GB");
            return Reply.of(m.t(lang, "admin.user.traffic_set") + "\n\n" + renderUserCard(lang, u),
                    Keyboards.adminUserCard(m, lang, uuid));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- create subscription

    public Reply createSubPrompt(Lang lang) {
        return Reply.of(m.t(lang, "admin.createsub.prompt"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    public Reply applyCreateSub(Lang lang, long adminId, String text) {
        String[] parts = text.trim().split(" +");
        if (parts.length < 2) {
            return Reply.of(m.t(lang, "admin.createsub.bad"),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
        try {
            long tgId = Long.parseLong(parts[0]);
            int days = Integer.parseInt(parts[1]);
            long gb = parts.length >= 3 ? Long.parseLong(parts[2]) : 0L;
            RemnawaveUser u = subscriptions.adminCreateFor(tgId, days, gb);
            audit.record(adminId, "create_sub", String.valueOf(tgId), days + "d/" + gb + "GB");
            return Reply.of(m.t(lang, "admin.createsub.done", tgId, Formatting.dateTime(u.expireAt)),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (NumberFormatException e) {
            return Reply.of(m.t(lang, "admin.createsub.bad"),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- HWID devices

    public Reply hwid(Lang lang, String uuid) {
        try {
            JsonNode resp = panel.getUserHwidDevices(uuid);
            JsonNode arr = resp == null ? null : (resp.has("devices") ? resp.get("devices") : resp);
            List<String[]> devices = new ArrayList<>();
            List<String> cache = new ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode d : arr) {
                    String hwid = d.path("hwid").asText("");
                    String platform = d.path("platform").asText("");
                    String model = d.path("deviceModel").asText("");
                    String label = (platform.isBlank() ? "" : platform + " ")
                            + (model.isBlank() ? hwid : model);
                    if (label.isBlank()) label = hwid;
                    cache.add(hwid);
                    devices.add(new String[]{trim(label, 24), String.valueOf(cache.size() - 1)});
                }
            }
            hwidCache.put(uuid, cache);
            String title = m.t(lang, "admin.hwid.title", devices.size());
            if (devices.isEmpty()) {
                return Reply.of(title + "\n\n" + m.t(lang, "admin.hwid.empty"),
                        Keyboards.backTo(m, lang, Callbacks.ADMIN_USER_PREFIX + "card:" + uuid));
            }
            return Reply.of(title, Keyboards.hwidList(m, lang, uuid, devices));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply deleteHwid(Lang lang, long adminId, String uuid, int index) {
        try {
            List<String> list = hwidCache.get(uuid);
            if (list != null && index >= 0 && index < list.size()) {
                panel.deleteHwidDevice(uuid, list.get(index));
                audit.record(adminId, "hwid_del", uuid, list.get(index));
            }
            return hwid(lang, uuid);
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- squads (per user)

    public Reply userSquadsView(Lang lang, String uuid) {
        try {
            RemnawaveUser u = panel.getByUuid(uuid);
            Set<String> member = new HashSet<>();
            if (u != null && u.activeInternalSquads != null) {
                for (RemnawaveUser.Squad s : u.activeInternalSquads) {
                    if (s.uuid != null) member.add(s.uuid);
                }
            }
            JsonNode all = panel.getInternalSquads();
            JsonNode arr = all == null ? null : (all.has("internalSquads") ? all.get("internalSquads") : all);
            List<String[]> squads = new ArrayList<>();
            List<String> cache = new ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode sq : arr) {
                    String sUuid = sq.path("uuid").asText("");
                    String name = sq.path("name").asText(sUuid);
                    cache.add(sUuid);
                    squads.add(new String[]{trim(name, 24), String.valueOf(cache.size() - 1),
                            member.contains(sUuid) ? "1" : "0"});
                }
            }
            squadCache.put(uuid, cache);
            return Reply.of(m.t(lang, "admin.usersquads.title"),
                    Keyboards.userSquads(m, lang, uuid, squads));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply squadToggle(Lang lang, long adminId, String uuid, int index) {
        try {
            List<String> cache = squadCache.get(uuid);
            if (cache == null || index < 0 || index >= cache.size()) {
                return userSquadsView(lang, uuid);
            }
            String squadUuid = cache.get(index);
            RemnawaveUser u = panel.getByUuid(uuid);
            List<String> current = new ArrayList<>();
            if (u != null && u.activeInternalSquads != null) {
                for (RemnawaveUser.Squad s : u.activeInternalSquads) {
                    if (s.uuid != null) current.add(s.uuid);
                }
            }
            if (current.contains(squadUuid)) current.remove(squadUuid);
            else current.add(squadUuid);
            panel.setUserSquads(uuid, current);
            audit.record(adminId, "squad_toggle", uuid, squadUuid);
            return userSquadsView(lang, uuid);
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- squads (global)

    public Reply squads(Lang lang) {
        try {
            JsonNode all = panel.getInternalSquads();
            JsonNode arr = all == null ? null : (all.has("internalSquads") ? all.get("internalSquads") : all);
            StringBuilder sb = new StringBuilder(m.t(lang, "admin.btn.squads")).append("\n\n");
            boolean any = false;
            if (arr != null && arr.isArray()) {
                for (JsonNode sq : arr) {
                    any = true;
                    String name = sq.path("name").asText("?");
                    long members = sq.path("info").path("membersCount")
                            .asLong(sq.path("membersCount").asLong(0));
                    sb.append("\u2022 ").append(name).append(" (").append(members).append(")\n");
                }
            }
            if (!any) sb.append(m.t(lang, "admin.users.empty"));
            return Reply.of(sb.toString(), Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- nodes

    public Reply nodes(Lang lang) {
        try {
            JsonNode nodes = panel.getNodes();
            List<String[]> list = new ArrayList<>();
            StringBuilder sb = new StringBuilder(m.t(lang, "admin.nodes.title")).append("\n\n");
            if (nodes != null && nodes.isArray()) {
                for (JsonNode n : nodes) {
                    String name = n.path("name").asText("?");
                    String uuid = n.path("uuid").asText("");
                    boolean connected = n.path("isConnected").asBoolean(false);
                    boolean disabled = n.path("isDisabled").asBoolean(false);
                    String icon = disabled ? "\u26AA" : (connected ? "\uD83D\uDFE2" : "\uD83D\uDD34");
                    sb.append(icon).append(" ").append(name).append("\n");
                    if (!uuid.isBlank()) list.add(new String[]{icon + " " + trim(name, 26), uuid});
                }
            }
            if (list.isEmpty()) {
                sb.append(m.t(lang, "admin.nodes.empty"));
                return Reply.of(sb.toString(), Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
            }
            return Reply.of(sb.toString(), Keyboards.nodeList(m, lang, list));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply nodeCard(Lang lang, String uuid) {
        try {
            JsonNode n = panel.getNode(uuid);
            String name = n.path("name").asText("?");
            boolean connected = n.path("isConnected").asBoolean(false);
            boolean disabled = n.path("isDisabled").asBoolean(false);
            String status = disabled ? m.t(lang, "admin.node.disabled")
                    : (connected ? m.t(lang, "admin.node.connected") : m.t(lang, "admin.node.offline"));
            String address = n.path("address").asText("\u2014");
            String country = n.path("countryCode").asText("\u2014");
            return Reply.of(m.t(lang, "admin.node.card", name, status, address, country),
                    Keyboards.nodeCard(m, lang, uuid));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply nodeAction(Lang lang, long adminId, String action, String uuid) {
        try {
            String msg;
            switch (action) {
                case "enable" -> { panel.enableNode(uuid); msg = m.t(lang, "admin.node.enabled"); }
                case "disable" -> { panel.disableNode(uuid); msg = m.t(lang, "admin.node.disabled_ok"); }
                case "restart" -> { panel.restartNode(uuid); msg = m.t(lang, "admin.node.restarted"); }
                default -> { return nodeCard(lang, uuid); }
            }
            audit.record(adminId, "node_" + action, uuid, null);
            Reply card = nodeCard(lang, uuid);
            return Reply.of(msg + "\n\n" + card.text(), card.keyboard());
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply restartAllNodes(Lang lang) {
        try {
            panel.restartAllNodes();
            return Reply.of(m.t(lang, "admin.nodes.restarted"),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    // ----------------------------------------------------------------- broadcast

    public Reply findPrompt(Lang lang) {
        return Reply.of(m.t(lang, "admin.find.prompt"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    public Reply badNumber(Lang lang, String uuid) {
        return Reply.of(m.t(lang, "admin.user.bad_number"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_USER_PREFIX + "card:" + uuid));
    }

    public Reply broadcastMenu(Lang lang) {
        return Reply.of(m.t(lang, "admin.broadcast.segment"), Keyboards.broadcastSegments(m, lang));
    }

    public Reply broadcastPrompt(Lang lang) {
        return Reply.of(m.t(lang, "admin.broadcast.prompt"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    public Reply broadcastConfirm(Lang lang, int recipients, String preview) {
        if (recipients == 0) {
            return Reply.of(m.t(lang, "admin.broadcast.empty"),
                    Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        }
        return Reply.of(m.t(lang, "admin.broadcast.confirm", recipients, preview),
                Keyboards.broadcastConfirm(m, lang));
    }

    public Reply broadcastCancelled(Lang lang) {
        return Reply.of(m.t(lang, "admin.broadcast.cancelled"),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    public Reply broadcastResult(Lang lang, int recipients) {
        return Reply.of(m.t(lang, "admin.broadcast.sent", recipients),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    /** Resolves the Telegram-id recipient list for a named broadcast segment. */
    public List<Long> recipientsForSegment(String segment) {
        return switch (segment == null ? "all" : segment) {
            case "active" -> users.telegramIdsWithRemnawave(true);
            case "inactive" -> users.telegramIdsWithRemnawave(false);
            default -> users.allTelegramIds();
        };
    }

    // ----------------------------------------------------------------- audit + settings

    public Reply audit(Lang lang) {
        try {
            List<AdminAction> rows = audit.recent(15);
            StringBuilder sb = new StringBuilder(m.t(lang, "admin.audit.title", 15)).append("\n\n");
            if (rows.isEmpty()) {
                sb.append(m.t(lang, "admin.audit.empty"));
            } else {
                for (AdminAction a : rows) {
                    String when = Formatting.dateTime(a.createdAt);
                    String det = a.target == null ? "" : ("`" + a.target + "` " + (a.details == null ? "" : a.details));
                    sb.append(m.t(lang, "admin.audit.row", when, a.adminId, a.action, det)).append("\n");
                }
            }
            return Reply.of(sb.toString(), Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
        } catch (Exception e) {
            return err(lang, e);
        }
    }

    public Reply settings(Lang lang) {
        StringBuilder plans = new StringBuilder();
        for (Plans.Plan p : Plans.all()) {
            plans.append(m.t(lang, "admin.settings.plan", p.code(), p.days(), p.trafficGb(), p.priceRub()))
                 .append("\n");
        }
        String body = m.t(lang, "admin.settings.body",
                config.trialDays(),
                config.trialTrafficBytes() / (1024L * 1024L * 1024L),
                config.referralRewardDays(),
                config.defaultTrafficStrategy(),
                plans.toString().trim());
        return Reply.of(m.t(lang, "admin.settings.title") + "\n\n" + body,
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    // ----------------------------------------------------------------- helpers

    private Reply err(Lang lang, Exception e) {
        return Reply.of(m.t(lang, "admin.error", e.getMessage()),
                Keyboards.backTo(m, lang, Callbacks.ADMIN_MAIN));
    }

    private String statusIcon(String status) {
        return switch (status == null ? "" : status) {
            case "ACTIVE" -> "\uD83D\uDFE2";
            case "DISABLED" -> "\u26AA";
            case "LIMITED" -> "\uD83D\uDFE1";
            case "EXPIRED" -> "\uD83D\uDD34";
            default -> "\u2753";
        };
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "\u2026";
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

    private String pretty(JsonNode node) {
        return node == null ? "no data" : node.toPrettyString();
    }
}
