package com.tessera.bot.db;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

/** Data-access for the referral graph and reward ledger. */
public class ReferralRepository {

    private final DataSource ds;

    public ReferralRepository(DataSource ds) { this.ds = ds; }

    /** Records that {@code referredId} was invited by {@code referrerId} (idempotent, first wins). */
    public void recordReferral(long referredId, long referrerId) {
        if (referredId == referrerId) return;
        String sql = """
                INSERT INTO referrals (referred_id, referrer_id)
                VALUES (?, ?)
                ON CONFLICT (referred_id) DO NOTHING
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, referredId);
            ps.setLong(2, referrerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("recordReferral failed", e);
        }
    }

    public Optional<Long> findReferrer(long referredId) {
        String sql = "SELECT referrer_id FROM referrals WHERE referred_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, referredId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("findReferrer failed", e);
        }
    }

    public long countReferrals(long referrerId) {
        String sql = "SELECT COUNT(*) FROM referrals WHERE referrer_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, referrerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("countReferrals failed", e);
        }
    }

    public long countRewardedDays(long referrerId) {
        String sql = "SELECT COALESCE(SUM(reward_days),0) FROM referral_rewards WHERE referrer_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, referrerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("countRewardedDays failed", e);
        }
    }

    /**
     * Atomically claims the referral reward for a referred user.
     * Returns the referrer id if this call is the one that granted the reward, else empty
     * (already rewarded or no referrer). Guarantees each referral pays out at most once.
     */
    public Optional<Long> claimReward(long referredId, int rewardDays) {
        String select = "SELECT referrer_id, rewarded FROM referrals WHERE referred_id = ? FOR UPDATE";
        String markRewarded = "UPDATE referrals SET rewarded = TRUE WHERE referred_id = ?";
        String insertLedger = """
                INSERT INTO referral_rewards (referrer_id, referred_id, reward_days)
                VALUES (?, ?, ?)
                ON CONFLICT (referrer_id, referred_id) DO NOTHING
                """;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long referrerId;
                try (PreparedStatement ps = c.prepareStatement(select)) {
                    ps.setLong(1, referredId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); return Optional.empty(); }
                        if (rs.getBoolean("rewarded")) { c.rollback(); return Optional.empty(); }
                        referrerId = rs.getLong("referrer_id");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(markRewarded)) {
                    ps.setLong(1, referredId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(insertLedger)) {
                    ps.setLong(1, referrerId);
                    ps.setLong(2, referredId);
                    ps.setInt(3, rewardDays);
                    ps.executeUpdate();
                }
                c.commit();
                return Optional.of(referrerId);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("claimReward failed", e);
        }
    }
}
