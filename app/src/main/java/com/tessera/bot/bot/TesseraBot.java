package com.tessera.bot.bot;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.AuditRepository;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.i18n.Lang;
import com.tessera.bot.i18n.LocaleService;
import com.tessera.bot.i18n.Messages;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.service.PaymentService;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * The Telegram long-polling bot. Acts as a thin router: it extracts intent from
 * updates and delegates to {@link UserHandler} / {@link AdminHandler}, then renders
 * the resulting {@link Reply}.
 */
@Slf4j
public class TesseraBot extends TelegramLongPollingBot {

    private final AppConfig config;
    private final UserHandler userHandler;
    private final AdminHandler adminHandler;
    private final UserRepository users;
    private final LocaleService locales;
    private final UserState state = new UserState();

    public TesseraBot(AppConfig config, RemnawaveClient panel, UserRepository users, AuditRepository audit,
                      SubscriptionService subscriptions, ReferralService referrals,
                      PaymentService payments,
                      LocaleService locales, Messages messages) {
        super(config.botToken());
        this.config = config;
        this.users = users;
        this.locales = locales;
        this.userHandler = new UserHandler(config, users, subscriptions, referrals, payments, locales, messages);
        this.adminHandler = new AdminHandler(config, panel, users, subscriptions, audit, messages);
    }

    @Override
    public String getBotUsername() {
        return config.botUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Error handling update", e);
        }
    }

    // ------------------------------------------------------------- messages

    private void handleMessage(Message msg) throws TelegramApiException {
        long chatId = msg.getChatId();
        User from = msg.getFrom();
        String text = msg.getText().trim();

        UserState.Await awaiting = state.get(chatId);
        if (awaiting != UserState.Await.NONE && adminHandler.isAdmin(from.getId())) {
            handleAdminStatefulInput(chatId, from.getId(), text, awaiting);
            return;
        }

        if (text.startsWith("/start")) {
            String payload = text.length() > 6 ? text.substring(6).trim() : "";
            send(chatId, userHandler.start(from.getId(),
                    from.getUserName(), from.getFirstName(),
                    from.getLanguageCode(), payload));
        } else if (text.equals("/menu")) {
            send(chatId, userHandler.mainMenuReply(from.getId()));
        } else if (text.equals("/admin")) {
            if (adminHandler.isAdmin(from.getId())) {
                send(chatId, adminHandler.menu(locales.forUser(from.getId())));
            } else {
                send(chatId, userHandler.help(from.getId()));
            }
        } else if (text.equals("/help")) {
            send(chatId, userHandler.help(from.getId()));
        } else {
            send(chatId, userHandler.mainMenuReply(from.getId()));
        }
    }

    private void handleAdminStatefulInput(long chatId, long adminId, String text, UserState.Await awaiting)
            throws TelegramApiException {
        String payload = state.payload(chatId);
        state.clear(chatId);
        Lang lang = locales.forUser(adminId);
        switch (awaiting) {
            case BROADCAST_TEXT -> {
                String segment = payload == null ? "all" : payload;
                int count = adminHandler.recipientsForSegment(segment).size();
                state.setPendingBroadcast(chatId, segment, text);
                String preview = text.length() > 200 ? text.substring(0, 200) + "\u2026" : text;
                send(chatId, adminHandler.broadcastConfirm(lang, count, preview));
            }
            case FIND_USER_QUERY -> send(chatId, adminHandler.findUser(lang, text));
            case ADD_DAYS -> {
                try {
                    int days = Integer.parseInt(text.trim());
                    send(chatId, adminHandler.applyAddDays(lang, adminId, payload, days));
                } catch (NumberFormatException e) {
                    send(chatId, adminHandler.badNumber(lang, payload));
                }
            }
            case SET_TRAFFIC -> {
                try {
                    long gb = Long.parseLong(text.trim());
                    send(chatId, adminHandler.applyTraffic(lang, adminId, payload, gb));
                } catch (NumberFormatException e) {
                    send(chatId, adminHandler.badNumber(lang, payload));
                }
            }
            case CREATE_SUB -> send(chatId, adminHandler.applyCreateSub(lang, adminId, text));
            default -> { /* no-op */ }
        }
    }

    private int broadcast(java.util.List<Long> recipients, String text) {
        int sent = 0;
        for (long id : recipients) {
            try {
                execute(SendMessage.builder().chatId(id).text(text)
                        .parseMode(ParseMode.MARKDOWN).build());
                sent++;
                Thread.sleep(35); // stay within Telegram rate limits (~30 msg/s)
            } catch (Exception e) {
                log.debug("broadcast to {} failed: {}", id, e.getMessage());
            }
        }
        return sent;
    }

    // ------------------------------------------------------------ callbacks

    private void handleCallback(CallbackQuery cq) throws TelegramApiException {
        String data = cq.getData();
        long chatId = cq.getMessage().getChatId();
        int messageId = cq.getMessage().getMessageId();
        User from = cq.getFrom();
        long userId = from.getId();

        Reply reply = route(data, userId, chatId);
        if (reply != null) {
            edit(chatId, messageId, reply);
        }
        execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
    }

    private Reply route(String data, long userId, long chatId) {
        switch (data) {
            case Callbacks.MENU_MAIN -> { return userHandler.mainMenuReply(userId); }
            case Callbacks.MENU_BUY -> { return userHandler.buyMenu(userId); }
            case Callbacks.MENU_SUB -> { return userHandler.mySubscription(userId); }
            case Callbacks.MENU_TRIAL -> { return userHandler.trial(userId); }
            case Callbacks.MENU_REFERRAL -> { return userHandler.referral(userId); }
            case Callbacks.MENU_HELP -> { return userHandler.help(userId); }
            case Callbacks.MENU_LANGUAGE -> { return userHandler.languageMenu(userId); }
            default -> { /* fall through */ }
        }
        if (data.startsWith(Callbacks.BUY_PREFIX)) {
            return userHandler.buyPlan(userId, data.substring(Callbacks.BUY_PREFIX.length()));
        }
        if (data.startsWith(Callbacks.PAY_PREFIX)) {
            String rest = data.substring(Callbacks.PAY_PREFIX.length());
            int sep = rest.indexOf(':');
            if (sep > 0) {
                return userHandler.payWith(userId, rest.substring(0, sep), rest.substring(sep + 1));
            }
        }
        if (data.startsWith(Callbacks.PAY_CHECK_PREFIX)) {
            try {
                return userHandler.checkPayment(userId,
                        Long.parseLong(data.substring(Callbacks.PAY_CHECK_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // fall through to null
            }
        }
        if (data.startsWith(Callbacks.LANG_PREFIX)) {
            return userHandler.setLanguage(userId, data.substring(Callbacks.LANG_PREFIX.length()));
        }

        if (data.startsWith("admin:")) {
            if (!adminHandler.isAdmin(userId)) {
                return userHandler.mainMenuReply(userId);
            }
            return routeAdmin(data, userId, chatId);
        }
        return null;
    }

    private Reply routeAdmin(String data, long userId, long chatId) {
        Lang lang = locales.forUser(userId);
        switch (data) {
            case Callbacks.ADMIN_MAIN -> { return adminHandler.menu(lang); }
            case Callbacks.ADMIN_DASHBOARD -> { return adminHandler.dashboard(lang); }
            case Callbacks.ADMIN_STATS -> { return adminHandler.stats(lang); }
            case Callbacks.ADMIN_HEALTH -> { return adminHandler.health(lang); }
            case Callbacks.ADMIN_USERS -> { return adminHandler.listUsers(lang, 0); }
            case Callbacks.ADMIN_SQUADS -> { return adminHandler.squads(lang); }
            case Callbacks.ADMIN_AUDIT -> { return adminHandler.audit(lang); }
            case Callbacks.ADMIN_SETTINGS -> { return adminHandler.settings(lang); }
            case Callbacks.ADMIN_NODES -> { return adminHandler.nodes(lang); }
            case Callbacks.ADMIN_NODE_RESTART_ALL -> { return adminHandler.restartAllNodes(lang); }
            case Callbacks.ADMIN_BROADCAST -> { return adminHandler.broadcastMenu(lang); }
            case Callbacks.ADMIN_CREATE_SUB -> {
                state.set(chatId, UserState.Await.CREATE_SUB);
                return adminHandler.createSubPrompt(lang);
            }
            case Callbacks.ADMIN_BC_CONFIRM -> {
                String[] pending = state.getPendingBroadcast(chatId);
                state.clearPendingBroadcast(chatId);
                if (pending == null) return adminHandler.menu(lang);
                int sent = broadcast(adminHandler.recipientsForSegment(pending[0]), pending[1]);
                return adminHandler.broadcastResult(lang, sent);
            }
            case Callbacks.ADMIN_BC_CANCEL -> {
                state.clearPendingBroadcast(chatId);
                return adminHandler.broadcastCancelled(lang);
            }
            case Callbacks.ADMIN_FIND_USER -> {
                state.set(chatId, UserState.Await.FIND_USER_QUERY);
                return adminHandler.findPrompt(lang);
            }
            default -> { /* fall through */ }
        }
        // Broadcast segment chosen -> ask for the message text.
        if (data.startsWith(Callbacks.ADMIN_BC_PREFIX)) {
            String seg = data.substring(Callbacks.ADMIN_BC_PREFIX.length());
            state.set(chatId, UserState.Await.BROADCAST_TEXT, seg);
            return adminHandler.broadcastPrompt(lang);
        }
        // Paginated user list.
        if (data.startsWith(Callbacks.ADMIN_USERS_PAGE_PREFIX)) {
            String p = data.substring(Callbacks.ADMIN_USERS_PAGE_PREFIX.length());
            try { return adminHandler.listUsers(lang, Integer.parseInt(p)); }
            catch (NumberFormatException e) { return adminHandler.listUsers(lang, 0); }
        }
        // Per-node actions.
        if (data.startsWith(Callbacks.ADMIN_NODE_PREFIX)) {
            String rest = data.substring(Callbacks.ADMIN_NODE_PREFIX.length());
            int idx = rest.indexOf(':');
            if (idx > 0) {
                String action = rest.substring(0, idx);
                String uuid = rest.substring(idx + 1);
                return action.equals("card")
                        ? adminHandler.nodeCard(lang, uuid)
                        : adminHandler.nodeAction(lang, userId, action, uuid);
            }
        }
        // Per-user actions (rich card).
        if (data.startsWith(Callbacks.ADMIN_USER_PREFIX)) {
            String rest = data.substring(Callbacks.ADMIN_USER_PREFIX.length());
            String[] parts = rest.split(":");
            if (parts.length >= 2) {
                String action = parts[0];
                String uuid = parts[1];
                String extra = parts.length >= 3 ? parts[2] : null;
                switch (action) {
                    case "card" -> { return adminHandler.userCard(lang, uuid); }
                    case "adddaysx" -> {
                        state.set(chatId, UserState.Await.ADD_DAYS, uuid);
                        return adminHandler.addDaysPrompt(lang, uuid);
                    }
                    case "adddays" -> {
                        try { return adminHandler.applyAddDays(lang, userId, uuid, Integer.parseInt(extra)); }
                        catch (NumberFormatException e) { return adminHandler.userCard(lang, uuid); }
                    }
                    case "traffic" -> {
                        state.set(chatId, UserState.Await.SET_TRAFFIC, uuid);
                        return adminHandler.trafficPrompt(lang, uuid);
                    }
                    case "hwid" -> { return adminHandler.hwid(lang, uuid); }
                    case "hwiddel" -> {
                        try { return adminHandler.deleteHwid(lang, userId, uuid, Integer.parseInt(extra)); }
                        catch (NumberFormatException e) { return adminHandler.hwid(lang, uuid); }
                    }
                    case "squads" -> { return adminHandler.userSquadsView(lang, uuid); }
                    case "squadtog" -> {
                        try { return adminHandler.squadToggle(lang, userId, uuid, Integer.parseInt(extra)); }
                        catch (NumberFormatException e) { return adminHandler.userSquadsView(lang, uuid); }
                    }
                    default -> { return adminHandler.userAction(lang, userId, action, uuid); }
                }
            }
        }
        return adminHandler.menu(lang);
    }

    // -------------------------------------------------------------- sending

    private void send(long chatId, Reply reply) throws TelegramApiException {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId)
                .text(reply.text())
                .parseMode(ParseMode.MARKDOWN);
        if (reply.keyboard() != null) {
            builder.replyMarkup(reply.keyboard());
        }
        execute(builder.build());
    }

    private void edit(long chatId, int messageId, Reply reply) {
        try {
            EditMessageText.EditMessageTextBuilder builder = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(reply.text())
                    .parseMode(ParseMode.MARKDOWN);
            if (reply.keyboard() != null) {
                builder.replyMarkup((InlineKeyboardMarkup) reply.keyboard());
            }
            execute(builder.build());
        } catch (TelegramApiException e) {
            try {
                send(chatId, reply);
            } catch (TelegramApiException ex) {
                log.error("edit + send fallback failed", ex);
            }
        }
    }
}
