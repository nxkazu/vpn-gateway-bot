package com.tessera.dashboard;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain-JDBC data access for the dashboard (tickets, plans, promos, stats, charts). */
final class DashboardDao {

    private final DataSource ds;

    DashboardDao(DataSource ds) { this.ds = ds; }

    // ------------------------------------------------------------- tickets

    List<Map<String, Object>> tickets(String status) {
        String sql = """
                SELECT t.id, t.telegram_id, t.status, t.subject, t.created_at, t.updated_at,
                       u.username, u.first_name,
                       (SELECT COUNT(*) FROM support_messages sm WHERE sm.ticket_id = t.id) AS msg_count
                FROM support_tickets t
                LEFT JOIN bot_users u ON u.telegram_id = t.telegram_id
                WHERE (? = 'ALL' OR t.status = ?)
                ORDER BY t.updated_at DESC
                LIMIT 200
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("tickets query failed", e);
        }
    }

    Map<String, Object> ticket(long id) {
        String sql = """
                SELECT t.id, t.telegram_id, t.status, t.subject, u.username, u.first_name
                FROM support_tickets t
                LEFT JOIN bot_users u ON u.telegram_id = t.telegram_id
                WHERE t.id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = rows(rs);
                return rows.isEmpty() ? null : rows.get(0);
            }
        } catch (SQLException e) {
            throw new RuntimeException("ticket query failed", e);
        }
    }

    List<Map<String, Object>> messages(long ticketId) {
        String sql = """
                SELECT id, sender, sender_name, body, attachment_file_id, attachment_name, created_at
                FROM support_messages WHERE ticket_id = ? ORDER BY id
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("messages query failed", e);
        }
    }

    void addAdminMessage(long ticketId, String adminName, String body) {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO support_messages (ticket_id, sender, sender_name, body) VALUES (?, 'ADMIN', ?, ?)")) {
                ps.setLong(1, ticketId);
                ps.setString(2, adminName);
                ps.setString(3, body);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE support_tickets SET updated_at = now() WHERE id = ?")) {
                ps.setLong(1, ticketId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("addAdminMessage failed", e);
        }
    }

    void setTicketStatus(long ticketId, String status) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE support_tickets SET status = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, ticketId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setTicketStatus failed", e);
        }
    }

    // --------------------------------------------------------------- plans

    List<Map<String, Object>> plans() {
        String sql = "SELECT code, title, days, traffic_gb, price_rub, enabled, sort_order FROM plans ORDER BY sort_order, price_rub";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rows(rs);
        } catch (SQLException e) {
            throw new RuntimeException("plans query failed", e);
        }
    }

    void upsertPlan(String code, String title, int days, long trafficGb, long priceRub,
                    boolean enabled, int sortOrder) {
        String sql = """
                INSERT INTO plans (code, title, days, traffic_gb, price_rub, enabled, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    title = EXCLUDED.title, days = EXCLUDED.days, traffic_gb = EXCLUDED.traffic_gb,
                    price_rub = EXCLUDED.price_rub, enabled = EXCLUDED.enabled,
                    sort_order = EXCLUDED.sort_order, updated_at = now()
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, title);
            ps.setInt(3, days);
            ps.setLong(4, trafficGb);
            ps.setLong(5, priceRub);
            ps.setBoolean(6, enabled);
            ps.setInt(7, sortOrder);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsertPlan failed", e);
        }
    }

    // -------------------------------------------------------------- promos

    List<Map<String, Object>> promos() {
        String sql = """
                SELECT code, discount_percent, max_uses, uses, expires_at, enabled, created_at
                FROM promo_codes ORDER BY created_at DESC LIMIT 500
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rows(rs);
        } catch (SQLException e) {
            throw new RuntimeException("promos query failed", e);
        }
    }

    void upsertPromo(String code, int discountPercent, int maxUses, String expiresAtIso, boolean enabled) {
        String sql = """
                INSERT INTO promo_codes (code, discount_percent, max_uses, expires_at, enabled)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    discount_percent = EXCLUDED.discount_percent, max_uses = EXCLUDED.max_uses,
                    expires_at = EXCLUDED.expires_at, enabled = EXCLUDED.enabled
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, discountPercent);
            ps.setInt(3, maxUses);
            if (expiresAtIso == null || expiresAtIso.isBlank()) {
                ps.setNull(4, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setObject(4, OffsetDateTime.parse(expiresAtIso.length() == 10
                        ? expiresAtIso + "T23:59:59Z" : expiresAtIso));
            }
            ps.setBoolean(5, enabled);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsertPromo failed", e);
        }
    }

    void deletePromo(String code) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM promo_codes WHERE code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deletePromo failed", e);
        }
    }

    // --------------------------------------------------------------- stats

    Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", scalarLong("SELECT COUNT(*) FROM bot_users"));
        out.put("openTickets", scalarLong("SELECT COUNT(*) FROM support_tickets WHERE status = 'OPEN'"));
        out.put("paidInvoices30d", scalarLong(
                "SELECT COUNT(*) FROM payment_invoices WHERE status = 'PAID' AND created_at > now() - interval '30 days'"));
        out.put("revenueRub30d", scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) / 100 FROM payment_invoices WHERE status = 'PAID' AND created_at > now() - interval '30 days'"));
        out.put("events30d", scalarLong(
                "SELECT COUNT(*) FROM user_events WHERE created_at > now() - interval '30 days'"));
        return out;
    }

    Map<String, Object> pulse(int days) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("funnel", List.of(
                pulseRow("Starts", "start", days),
                pulseRow("VPN Hub", "vpn_hub_opened", days),
                pulseRow("Setup", "setup_opened", days),
                pulseRow("Plan selected", "plan_selected", days),
                pulseRow("Invoices", "invoice_created", days),
                pulseRow("Paid", "payment_confirmed", days),
                pulseRow("Support", "support_created", days)
        ));
        out.put("topEvents", topEvents(days, 12));
        return out;
    }

    private Map<String, Object> pulseRow(String label, String eventType, int days) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("event", eventType);
        row.put("amount", countEvent(eventType, days));
        return row;
    }

    private long countEvent(String eventType, int days) {
        String sql = """
                SELECT COUNT(*) FROM user_events
                WHERE event_type = ? AND created_at > now() - make_interval(days => ?)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setInt(2, days);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            return 0L;
        }
    }

    private List<Map<String, Object>> topEvents(int days, int limit) {
        String sql = """
                SELECT event_type, COUNT(*) AS amount
                FROM user_events
                WHERE created_at > now() - make_interval(days => ?)
                GROUP BY event_type
                ORDER BY amount DESC, event_type
                LIMIT ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, days);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            return List.of();
        }
    }

    /** Daily revenue (rubles) from the payments ledger over the trailing window. */
    List<Map<String, Object>> revenueByDay(int days) {
        String sql = """
                SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS day,
                       COALESCE(SUM(amount_cents), 0) / 100 AS amount
                FROM payments
                WHERE created_at > now() - make_interval(days => ?)
                GROUP BY 1 ORDER BY 1
                """;
        return series(sql, days);
    }

    /** Daily new bot users over the trailing window. */
    List<Map<String, Object>> newUsersByDay(int days) {
        String sql = """
                SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS day,
                       COUNT(*) AS amount
                FROM bot_users
                WHERE created_at > now() - make_interval(days => ?)
                GROUP BY 1 ORDER BY 1
                """;
        return series(sql, days);
    }

    private List<Map<String, Object>> series(String sql, int days) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("timeseries query failed", e);
        }
    }

    private long scalarLong(String sql) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            return 0L;
        }
    }

    // ------------------------------------------------------------- helpers

    private List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        var meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                Object v = rs.getObject(i);
                if (v instanceof Timestamp ts) v = ts.toInstant().toString();
                row.put(meta.getColumnLabel(i).toLowerCase(), v);
            }
            out.add(row);
        }
        return out;
    }
}
