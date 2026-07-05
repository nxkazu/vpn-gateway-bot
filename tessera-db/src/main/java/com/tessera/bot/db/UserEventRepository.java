package com.tessera.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Append-only product analytics events used by Tessera Pulse. */
public class UserEventRepository {

    private final DataSource ds;

    public UserEventRepository(DataSource ds) {
        this.ds = ds;
    }

    public void record(Long telegramId, String eventType, String metadataJson) {
        String sql = """
                INSERT INTO user_events (telegram_id, event_type, metadata)
                VALUES (?, ?, COALESCE(?::jsonb, '{}'::jsonb))
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (telegramId == null) ps.setNull(1, java.sql.Types.BIGINT);
            else ps.setLong(1, telegramId);
            ps.setString(2, eventType);
            ps.setString(3, metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("record user_event failed", e);
        }
    }

    public long countSince(String eventType, int days) {
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
            throw new RuntimeException("count user_events failed", e);
        }
    }

    public List<EventCount> topSince(int days, int limit) {
        String sql = """
                SELECT event_type, COUNT(*) AS amount
                FROM user_events
                WHERE created_at > now() - make_interval(days => ?)
                GROUP BY event_type
                ORDER BY amount DESC, event_type
                LIMIT ?
                """;
        List<EventCount> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, days);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new EventCount(rs.getString("event_type"), rs.getLong("amount")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("top user_events failed", e);
        }
    }

    public record EventCount(String eventType, long amount) {}
}
