package com.tessera.bot.bot;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.UserRepository;
import com.tessera.bot.remnawave.RemnawaveClient;
import com.tessera.bot.service.ReferralService;
import com.tessera.bot.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class TesseraBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TesseraBot.class);

    private final AppConfig config;
    private final UserHandler userHandler;
    private final AdminHandler adminHandler;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final ReferralService referrals;
    private final UserState state = new UserState();

    public TesseraBot(AppConfig config, RemnawaveClient panel, UserRepository users,
                      SubscriptionService subscriptions, ReferralService referrals) {
        super(config.botToken());
        this.config = config;
        this.users = users;
        this.subscriptions = subscriptions;
        this.referrals = referrals;
        this.userHandler = new UserHandler(config, users, subscriptions, referrals);
        this.adminHandler = new AdminHandler(config, panel, users);
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

        // Stateful admin flows first.
        UserState.Await awaiting = state.get(chatId);
        if (awaiting != UserState.Await.NONE && adminHandler.isAdmin(from.getId())) {
            handleAdminStatefulInput(chatId, from.getId(), text, awaiting);
            return;
        }

        if (text.startsWith("/start")) {
            String payload = text.length() > 6 ? text.substring(6).trim() : "";
            Reply r = userHandler.start(from.getId(),
                    from.getUserName(), from.getFirstName(),
                    from.getLanguageCode(), payload);
            send(chatId, r);
        } else if (text.equals("/menu")) {
            send(chatId, userHandler.mainMenuReply(from.getId()));
        } else if (text.equals("/admin")) {
            if (adminHandler.isAdmin(from.getId())) {
                send(chatId, adminHandler.menu());
            } else {
                send(chatId, Reply.of("⛔ У вас нет доступа к админ-панели."));
            }
        } else if (text.equals("/help")) {
            send(chatId, userHandler.help());
        } else {
            send(chatId, userHandler.mainMenuReply(from.getId()));
        }
    }

    private void handleAdminStatefulInput(long chatId, long adminId, String text, UserState.Await awaiting)
            throws TelegramApiException {
        state.clear(chatId);
        switch (awaiting) {
            case BROADCAST_TEXT -> {
                int sent = broadcast(text);
                send(chatId, Reply.of("📢 Рассылка отправлена: *" + sent + "* получателей.",
                        Keyboards.backTo(Callbacks.ADMIN_MAIN)));
            }
            case FIND_USER_QUERY -> send(chatId, adminHandler.findUser(text));
            default -> { /* no-op */ }
        }
    }

    private int broadcast(String text) {
        int sent = 0;
        for (long id : users.allTelegramIds()) {
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

        Reply reply = route(data, userId, from, chatId);
        if (reply != null) {
            edit(chatId, messageId, reply);
        }
        execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
    }

    private Reply route(String data, long userId, User from, long chatId) {
        // ---- user routes
        switch (data) {
            case Callbacks.MENU_MAIN -> { return userHandler.mainMenuReply(userId); }
            case Callbacks.MENU_BUY -> { return userHandler.buyMenu(); }
            case Callbacks.MENU_SUB -> { return userHandler.mySubscription(userId); }
            case Callbacks.MENU_TRIAL -> { return userHandler.trial(userId); }
            case Callbacks.MENU_REFERRAL -> { return userHandler.referral(userId); }
            case Callbacks.MENU_HELP -> { return userHandler.help(); }
            default -> { /* fall through */ }
        }
        if (data.startsWith(Callbacks.BUY_PREFIX)) {
            return userHandler.buyPlan(userId, data.substring(Callbacks.BUY_PREFIX.length()));
        }

        // ---- admin routes (guarded)
        if (data.startsWith("admin:")) {
            if (!adminHandler.isAdmin(userId)) {
                return Reply.of("⛔ Нет доступа.");
            }
            return routeAdmin(data, chatId);
        }
        return null;
    }

    private Reply routeAdmin(String data, long chatId) {
        switch (data) {
            case Callbacks.ADMIN_MAIN -> { return adminHandler.menu(); }
            case Callbacks.ADMIN_STATS -> { return adminHandler.stats(); }
            case Callbacks.ADMIN_HEALTH -> { return adminHandler.health(); }
            case Callbacks.ADMIN_NODES -> { return adminHandler.nodes(); }
            case Callbacks.ADMIN_NODE_RESTART_ALL -> { return adminHandler.restartAllNodes(); }
            case Callbacks.ADMIN_BROADCAST -> {
                state.set(chatId, UserState.Await.BROADCAST_TEXT);
                return Reply.of("✍️ Отправьте текст рассылки одним сообщением.",
                        Keyboards.backTo(Callbacks.ADMIN_MAIN));
            }
            case Callbacks.ADMIN_FIND_USER -> {
                state.set(chatId, UserState.Await.FIND_USER_QUERY);
                return Reply.of("🔍 Отправьте username, Telegram ID или UUID пользователя.",
                        Keyboards.backTo(Callbacks.ADMIN_MAIN));
            }
            default -> { /* fall through */ }
        }
        if (data.startsWith(Callbacks.ADMIN_USER_PREFIX)) {
            String rest = data.substring(Callbacks.ADMIN_USER_PREFIX.length());
            int idx = rest.indexOf(':');
            if (idx > 0) {
                String action = rest.substring(0, idx);
                String uuid = rest.substring(idx + 1);
                return adminHandler.userAction(action, uuid);
            }
        }
        return adminHandler.menu();
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
            // Editing can fail if content is identical; fall back to a new message.
            try {
                send(chatId, reply);
            } catch (TelegramApiException ex) {
                log.error("edit + send fallback failed", ex);
            }
        }
    }
}
