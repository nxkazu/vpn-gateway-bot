package com.tessera.bot.db;

import com.tessera.bot.service.Plans;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data-access for the {@code plans} table. */
public class PlanRepository {

    private final DataSource ds;

    public PlanRepository(DataSource ds) { this.ds = ds; }

    /** Enabled plans in display order. */
    public List<Plans.Plan> findEnabled() {
        String sql = "SELECT code, title, days, traffic_gb, price_rub FROM plans WHERE enabled ORDER BY sort_order, price_rub";
        List<Plans.Plan> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findEnabled plans failed", e);
        }
        return out;
    }

    /** Any plan (enabled or not) by code, or {@code null}. */
    public Plans.Plan findByCode(String code) {
        String sql = "SELECT code, title, days, traffic_gb, price_rub FROM plans WHERE code = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByCode plan failed", e);
        }
    }

    private Plans.Plan map(ResultSet rs) throws SQLException {
        return new Plans.Plan(
                rs.getString("code"),
                rs.getString("title"),
                rs.getInt("days"),
                rs.getLong("traffic_gb"),
                rs.getLong("price_rub"));
    }
}