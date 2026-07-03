package com.tessera.bot.db;

import com.tessera.bot.db.model.AdminAction;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Data-access for the {@code admin_actions} audit trail. */
public class AuditRepository {

    private final DataSource ds;

    public AuditRepository(DataSource ds) { this.ds = ds; }

    /** Records an administrative action. Never throws to the caller \u2014 auditing must not break the action. */
    public void record(long adminId, String action, String target, String details) {
        String sql = "INSERT INTO admin_actions (admin_id, action, target, details) VALUES (?, ?, ?, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            ps.setString(2, action);
            ps.setString(3, target);
            ps.setString(4, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Best-effort: log via stderr, don't propagate.
            System.err.println("audit record failed: " + e.getMessage());
        }
    }

    /** Returns the most recent audit entries, newest first. */
    public List<AdminAction> recent(int limit) {
        String sql = "SELECT id, admin_id, action, target, details, created_at FROM admin_actions ORDER BY created_at DESC LIMIT ?";
        List<AdminAction> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminAction a = new AdminAction();
                    a.id = rs.getLong("id");
                    a.adminId = rs.getLong("admin_id");
                    a.action = rs.getString("action");
                    a.target = rs.getString("target");
                    a.details = rs.getString("details");
                    Timestamp ts = rs.getTimestamp("created_at");
                    a.createdAt = ts == null ? null : ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
                    out.add(a);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("audit recent failed", e);
        }
        return out;
    }
}
