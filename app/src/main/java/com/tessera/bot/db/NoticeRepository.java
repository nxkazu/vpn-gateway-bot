package com.tessera.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/** Data-access for {@code expiry_notices} (one warning per user + expiry date). */
public class NoticeRepository {

    private final DataSource ds;

    public NoticeRepository(DataSource ds) { this.ds = ds; }

    /** Records the notice; returns {@code true} if it is new (i.e. the user should be warned now). */
    public boolean tryInsert(long telegramId, LocalDate expireDate) {
        String sql = "INSERT INTO expiry_notices (telegram_id, expire_date) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            ps.setObject(2, expireDate);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("expiry notice insert failed", e);
        }
    }
}