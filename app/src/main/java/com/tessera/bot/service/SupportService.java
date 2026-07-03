package com.tessera.bot.service;

import com.tessera.bot.db.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bot-side support flow: a user's message opens (or continues) their ticket.
 * Admins read and answer tickets from the admin dashboard (AdminDashboard.jar);
 * replies are delivered back to the user via Telegram.
 */
@Slf4j
@RequiredArgsConstructor
public class SupportService {

    private final TicketRepository tickets;

    public record Submitted(long ticketId, boolean created) {}

    /** Appends the message to the user's open ticket, creating one if needed. */
    public Submitted submitUserMessage(long telegramId, String username, String text) {
        Long open = tickets.findOpenTicketId(telegramId);
        boolean created = false;
        if (open == null) {
            String subject = text.length() > 80 ? text.substring(0, 80) + "\u2026" : text;
            open = tickets.createTicket(telegramId, subject);
            created = true;
        }
        tickets.addMessage(open, "USER", username, text);
        log.info("Support message from {} -> ticket {}{}", telegramId, open, created ? " (new)" : "");
        return new Submitted(open, created);
    }
}