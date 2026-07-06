package com.tessera.bot.service;

import com.tessera.bot.db.TicketRepository;
import com.tessera.core.api.TelegramNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Bot-side support flow: a user's message opens (or continues) their ticket; while a
 * ticket is open, further messages (including photos/documents) are appended to it.
 * Admins read and answer tickets from the admin dashboard (tessera-dashboard.jar);
 * replies are delivered back to the user via Telegram. Admins are also notified in
 * Telegram whenever a ticket is opened or receives a new user message.
 */
@Slf4j
@RequiredArgsConstructor
public class SupportService {

    private final TicketRepository tickets;
    private final Set<Long> adminIds;
    private final Long supportGroupId;
    private final TelegramNotifier notifier;

    public record Submitted(long ticketId, boolean created) {}

    /** True when the user currently has an OPEN ticket. */
    public boolean hasOpenTicket(long telegramId) {
        return tickets.findOpenTicketId(telegramId) != null;
    }

    /** Appends the message to the user's open ticket, creating one if needed. */
    public Submitted submitUserMessage(long telegramId, String username, String text) {
        return submit(telegramId, username, text, null, null);
    }

    /** Same as {@link #submitUserMessage} but with a Telegram attachment (photo/document). */
    public Submitted submitUserMessage(long telegramId, String username, String text,
                                       String attachmentFileId, String attachmentName) {
        return submit(telegramId, username, text, attachmentFileId, attachmentName);
    }

    private Submitted submit(long telegramId, String username, String text,
                             String fileId, String fileName) {
        String body = (text == null || text.isBlank())
                ? (fileName != null ? "\uD83D\uDCCE " + fileName : "")
                : text;
        Long open = tickets.findOpenTicketId(telegramId);
        boolean created = false;
        if (open == null) {
            String subject = body.length() > 80 ? body.substring(0, 80) + "\u2026" : body;
            open = tickets.createTicket(telegramId, subject);
            created = true;
        }
        tickets.addMessage(open, "USER", username, body, fileId, fileName);
        log.info("Support message from {} -> ticket {}{}", telegramId, open, created ? " (new)" : "");
        notifyAdmins(open, created, telegramId, username, body, fileName != null);
        return new Submitted(open, created);
    }

    /**
     * Adds an admin reply to a ticket and forwards it to the user.
     * Throws an exception if the ticket is not found or not open.
     */
    public void adminReply(long ticketId, String adminUsername, String text) {
        Long telegramId = tickets.getTicketUser(ticketId);
        if (telegramId == null) {
            throw new IllegalArgumentException("Ticket not found or no user associated");
        }
        tickets.addMessage(ticketId, "ADMIN", adminUsername, text, null, null);
        log.info("Admin {} reply to ticket {} (user {})", adminUsername, ticketId, telegramId);
        if (notifier != null) {
            try {
                String msg = "\uD83D\uDCAC \u041E\u0442\u0432\u0435\u0442 \u043F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0438 (\u0442\u0438\u043A\u0435\u0442 #" + ticketId + "):\n\n" + text;
                notifier.sendMessage(telegramId, msg);
            } catch (Exception e) {
                log.warn("Failed to deliver admin reply to user {}: {}", telegramId, e.getMessage());
            }
        }
    }


    private void notifyAdmins(long ticketId, boolean created, long telegramId,
                              String username, String body, boolean hasAttachment) {
        if (notifier == null) return;
        if ((adminIds == null || adminIds.isEmpty()) && supportGroupId == null) return;
        String who = username != null && !username.isBlank() ? "@" + username : String.valueOf(telegramId);
        String preview = body.length() > 300 ? body.substring(0, 300) + "\u2026" : body;
        String head = created
                ? "\uD83C\uDD95 \u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 #" + ticketId
                : "\uD83D\uDCAC \u0421\u043E\u043E\u0431\u0449\u0435\u043D\u0438\u0435 \u0432 \u0442\u0438\u043A\u0435\u0442\u0435 #" + ticketId;
        String attach = hasAttachment ? " [+\u0432\u043B\u043E\u0436\u0435\u043D\u0438\u0435]" : "";
        String msg = head + " \u043E\u0442 " + who + attach + ":\n\n" + preview;
        if (supportGroupId != null) {
            try {
                notifier.sendMessage(supportGroupId, msg);
            } catch (Exception e) {
                log.debug("group notify failed: {}", e.getMessage());
            }
        } else if (adminIds != null) {
            for (long adminId : adminIds) {
                try {
                    notifier.sendMessage(adminId, msg);
                } catch (Exception e) {
                    log.debug("admin notify {} failed: {}", adminId, e.getMessage());
                }
            }
        }
    }
}
