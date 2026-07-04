package com.tessera.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/** Data-access for {@code promo_codes} / {@code promo_redemptions}. */
public class PromoRepository {

    /** A raw promo-code row. {@code maxUses} 0 means unlimited. */
    public record PromoRow(String code, int discountPercent, int maxUses, int uses,
                           OffsetDateTime expiresAt, boolean enabled) {}

    private final DataSource ds;

    public PromoRepository(DataSource ds) { this.ds = ds; }

    public PromoRow find(String code) {
        String sql = "SELECT code, discount_percent, max_uses, uses, expires_at, enabled FROM promo_codes WHERE code = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                java.sql.Timestamp exp = rs.getTimestamp("expires_at");
                return new PromoRow(
                        rs.getString("code"),
                        rs.getInt("discount_percent"),
                        rs.getInt("max_uses"),
                        rs.getInt("uses"),
                        exp == null ? null : exp.toInstant().atOffset(java.time.ZoneOffset.UTC),
                        rs.getBoolean("enabled"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("promo find failed", e);
        }
    }

    public boolean alreadyRedeemed(String code, long telegramId) {
        String sql = "SELECT 1 FROM promo_redemptions WHERE code = ? AND telegram_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setLong(2, telegramId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("promo alreadyRedeemed failed", e);
        }
    }

    /** Inserts the redemption (idempotent) and bumps the usage counter. */
    public void redeem(String code, long telegramId, long invoiceId) {
        try (Connection c = ds.getConnection()) {
            int inserted;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO promo_redemptions (code, telegram_id, invoice_id) VALUES (?, ?, ?) "
                            + "ON CONFLICT (code, telegram_id) DO NOTHING")) {
                ps.setString(1, code);
                ps.setLong(2, telegramId);
                ps.setLong(3, invoiceId);
                inserted = ps.executeUpdate();
            }
            if (inserted > 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE promo_codes SET uses = uses + 1 WHERE code = ?")) {
                    ps.setString(1, code);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("promo redeem failed", e);
        }
    }
}