package com.tessera.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Data-access for {@code support_tickets} / {@code support_messages} (bot side). */
public class TicketRepository {

    private final DataSource ds;

    public TicketRepository(DataSource ds) { this.ds = ds; }

    /** Id of the user's currently open ticket, or {@code null}. */
    public Long findOpenTicketId(long telegramId) {
        String sql = "SELECT id FROM support_tickets WHERE telegram_id = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findOpenTicketId failed", e);
        }
    }

    /** Gets the telegram_id associated with a ticket, or null if not found. */
    public Long getTicketUser(long ticketId) {
        String sql = "SELECT telegram_id FROM support_tickets WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTicketUser failed", e);
        }
    }


    /** Creates a new OPEN ticket and returns its id. */
    public long createTicket(long telegramId, String subject) {
        String sql = "INSERT INTO support_tickets (telegram_id, subject) VALUES (?, ?)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, telegramId);
            ps.setString(2, subject);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createTicket failed", e);
        }
    }

    /** Appends a message (optionally with an attachment) and bumps the ticket's {@code updated_at}. */
    public void addMessage(long ticketId, String sender, String senderName, String body,
                           String attachmentFileId, String attachmentName) {
        String insert = """
                INSERT INTO support_messages (ticket_id, sender, sender_name, body, attachment_file_id, attachment_name)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        String touch = "UPDATE support_tickets SET updated_at = now() WHERE id = ?";
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setLong(1, ticketId);
                ps.setString(2, sender);
                ps.setString(3, senderName);
                ps.setString(4, body);
                ps.setString(5, attachmentFileId);
                ps.setString(6, attachmentName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(touch)) {
                ps.setLong(1, ticketId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("addMessage failed", e);
        }
    }

    /** Backwards-compatible overload without an attachment. */
    public void addMessage(long ticketId, String sender, String senderName, String body) {
        addMessage(ticketId, sender, senderName, body, null, null);
    }
}