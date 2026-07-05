package com.tessera.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access for the {@code gifts} table (gift subscriptions redeemable by code). */
public class GiftRepository {

    /** Lifecycle: PENDING (invoice unpaid) -> ACTIVE (paid, redeemable) -> REDEEMED. */
    public record Gift(long id, String code, long buyerTelegramId, String planCode,
                       Long invoiceId, String status, Long redeemedBy,
                       OffsetDateTime createdAt, OffsetDateTime redeemedAt) {}

    private final DataSource ds;

    public GiftRepository(DataSource ds) { this.ds = ds; }

    /** Inserts a PENDING gift tied to an unpaid invoice. Throws on code collision. */
    public long insertPending(String code, long buyerTelegramId, String planCode, long invoiceId) {
        String sql = """
                INSERT INTO gifts (code, buyer_telegram_id, plan_code, invoice_id)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setLong(2, buyerTelegramId);
            ps.setString(3, planCode);
            ps.setLong(4, invoiceId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert gift failed", e);
        }
    }

    /** Marks the gift of a paid invoice as redeemable. Idempotent PENDING -> ACTIVE transition. */
    public boolean activateByInvoice(long invoiceId) {
        String sql = "UPDATE gifts SET status = 'ACTIVE' WHERE invoice_id = ? AND status = 'PENDING'";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("activate gift failed", e);
        }
    }

    public Optional<Gift> findByInvoice(long invoiceId) {
        return findOne("SELECT * FROM gifts WHERE invoice_id = ?", ps -> ps.setLong(1, invoiceId));
    }

    public Optional<Gift> findByCode(String code) {
        return findOne("SELECT * FROM gifts WHERE code = ?", ps -> ps.setString(1, code));
    }

    /**
     * Atomically claims an ACTIVE gift for a redeemer. Returns {@code true} only for the
     * caller that actually performed the transition, so concurrent redemption attempts
     * of the same code can never double-activate a plan.
     */
    public boolean claim(String code, long redeemerTelegramId) {
        String sql = """
                UPDATE gifts SET status = 'REDEEMED', redeemed_by = ?, redeemed_at = now()
                WHERE code = ? AND status = 'ACTIVE'
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, redeemerTelegramId);
            ps.setString(2, code);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("claim gift failed", e);
        }
    }

    /** Reverts a claimed gift back to ACTIVE (compensation when plan activation fails). */
    public void release(String code) {
        String sql = """
                UPDATE gifts SET status = 'ACTIVE', redeemed_by = NULL, redeemed_at = NULL
                WHERE code = ? AND status = 'REDEEMED'
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("release gift failed", e);
        }
    }

    /** Most recent gifts bought by a user, newest first. */
    public List<Gift> listByBuyer(long buyerTelegramId, int limit) {
        String sql = "SELECT * FROM gifts WHERE buyer_telegram_id = ? ORDER BY id DESC LIMIT ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, buyerTelegramId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Gift> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("listByBuyer failed", e);
        }
    }

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private Optional<Gift> findOne(String sql, Binder binder) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("find gift failed", e);
        }
    }

    private Gift map(ResultSet rs) throws SQLException {
        long invoiceId = rs.getLong("invoice_id");
        Long invoice = rs.wasNull() ? null : invoiceId;
        long redeemedBy = rs.getLong("redeemed_by");
        Long redeemer = rs.wasNull() ? null : redeemedBy;
        return new Gift(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getLong("buyer_telegram_id"),
                rs.getString("plan_code"),
                invoice,
                rs.getString("status"),
                redeemer,
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("redeemed_at", OffsetDateTime.class));
    }
}
