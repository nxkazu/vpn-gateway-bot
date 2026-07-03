package com.tessera.bot.db;

import com.tessera.bot.db.model.BotUser;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

/** Data-access for {@code bot_users}. */
public class UserRepository {

    private final DataSource ds;

    public UserRepository(DataSource ds) { this.ds = ds; }

    /** Inserts the user if absent, otherwise refreshes their Telegram profile fields. */
    public void upsert(long telegramId, String username, String firstName, String languageCode) {
        String sql = """
                INSERT INTO bot_users (telegram_id, username, first_name, language_code)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (telegram_id) DO UPDATE
                   SET username = EXCLUDED.username,
                       first_name = EXCLUDED.first_name,
                       language_code = EXCLUDED.language_code,
                       updated_at = now()
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            ps.setString(2, username);
            ps.setString(3, firstName);
            ps.setString(4, languageCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsert bot_user failed", e);
        }
    }

    public Optional<BotUser> find(long telegramId) {
        String sql = "SELECT * FROM bot_users WHERE telegram_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("find bot_user failed", e);
        }
    }

    public void linkRemnawave(long telegramId, String uuid, String username) {
        String sql = "UPDATE bot_users SET remnawave_uuid = ?::uuid, remnawave_username = ?, updated_at = now() WHERE telegram_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setLong(3, telegramId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("linkRemnawave failed", e);
        }
    }

    public void markTrialUsed(long telegramId) {
        update("UPDATE bot_users SET trial_used = TRUE, updated_at = now() WHERE telegram_id = ?", telegramId);
    }

    public void setBlocked(long telegramId, boolean blocked) {
        String sql = "UPDATE bot_users SET is_blocked = ?, updated_at = now() WHERE telegram_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, blocked);
            ps.setLong(2, telegramId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setBlocked failed", e);
        }
    }

    public long count() {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bot_users")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException("count bot_users failed", e);
        }
    }

    /** Streams every known Telegram id (used for admin broadcast). */
    public java.util.List<Long> allTelegramIds() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT telegram_id FROM bot_users WHERE is_blocked = FALSE")) {
            while (rs.next()) ids.add(rs.getLong(1));
        } catch (SQLException e) {
            throw new RuntimeException("allTelegramIds failed", e);
        }
        return ids;
    }

    private void update(String sql, long telegramId) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("update failed: " + sql, e);
        }
    }

    private BotUser map(ResultSet rs) throws SQLException {
        BotUser u = new BotUser();
        u.telegramId = rs.getLong("telegram_id");
        u.username = rs.getString("username");
        u.firstName = rs.getString("first_name");
        u.languageCode = rs.getString("language_code");
        u.remnawaveUuid = rs.getString("remnawave_uuid");
        u.remnawaveUsername = rs.getString("remnawave_username");
        u.blocked = rs.getBoolean("is_blocked");
        u.trialUsed = rs.getBoolean("trial_used");
        return u;
    }
}
