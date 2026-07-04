package com.tessera.bot.db;

import com.tessera.bot.db.model.PaymentInvoice;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access for {@code payment_invoices} and the {@code payments} ledger. */
public class InvoiceRepository {

    private final DataSource ds;

    public InvoiceRepository(DataSource ds) { this.ds = ds; }

    /** Inserts a PENDING invoice and returns its database id. */
    public long insert(long telegramId, String planCode, String provider,
                       String externalId, String payUrl, long amountCents, String currency,
                       String promoCode) {
        String sql = """
                INSERT INTO payment_invoices
                    (telegram_id, plan_code, provider, external_id, pay_url, amount_cents, currency, promo_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, telegramId);
            ps.setString(2, planCode);
            ps.setString(3, provider);
            ps.setString(4, externalId);
            ps.setString(5, payUrl);
            ps.setLong(6, amountCents);
            ps.setString(7, currency);
            ps.setString(8, promoCode);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert payment_invoice failed", e);
        }
    }

    public Optional<PaymentInvoice> find(long id) {
        String sql = "SELECT * FROM payment_invoices WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("find payment_invoice failed", e);
        }
    }

    /** PENDING invoices younger than {@code maxAgeHours}, oldest first (for the background watcher). */
    public List<PaymentInvoice> findPending(int maxAgeHours) {
        String sql = """
                SELECT * FROM payment_invoices
                WHERE status = 'PENDING' AND created_at > now() - make_interval(hours => ?)
                ORDER BY id
                LIMIT 200
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, maxAgeHours);
            try (ResultSet rs = ps.executeQuery()) {
                List<PaymentInvoice> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findPending failed", e);
        }
    }

    public void markStatus(long id, String status) {
        String sql = "UPDATE payment_invoices SET status = ?, updated_at = now() WHERE id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("markStatus failed", e);
        }
    }

    /**
     * Atomically transitions a PENDING invoice to PAID, returning {@code true} only for the
     * caller that actually performed the transition. This is the concurrency guard that makes
     * plan activation (and the referral reward) happen exactly once when the manual "I paid"
     * check races the background PaymentWatcher.
     */
    public boolean markPaidIfPending(long id) {
        String sql = "UPDATE payment_invoices SET status = 'PAID', updated_at = now() WHERE id = ? AND status = 'PENDING'";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("markPaidIfPending failed", e);
        }
    }

    /** Writes a row into the pre-existing {@code payments} ledger table. */
    public void recordPayment(long telegramId, String planCode, long amountCents, String currency,
                              int days, String provider, String providerRef) {
        String sql = """
                INSERT INTO payments (telegram_id, plan_code, amount_cents, currency, days, status, provider, provider_ref)
                VALUES (?, ?, ?, ?, ?, 'PAID', ?, ?)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            ps.setString(2, planCode);
            ps.setLong(3, amountCents);
            ps.setString(4, currency);
            ps.setInt(5, days);
            ps.setString(6, provider);
            ps.setString(7, providerRef);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("recordPayment failed", e);
        }
    }

    private PaymentInvoice map(ResultSet rs) throws SQLException {
        return new PaymentInvoice(
                rs.getLong("id"),
                rs.getLong("telegram_id"),
                rs.getString("plan_code"),
                rs.getString("provider"),
                rs.getString("external_id"),
                rs.getString("pay_url"),
                rs.getLong("amount_cents"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("promo_code"));
    }
}