package com.tessera.bot.db;

import com.tessera.bot.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/** Owns the connection pool and runs Flyway migrations on startup. */
public class Database implements AutoCloseable {

    private final HikariDataSource dataSource;

    public Database(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.dbUser());
        hc.setPassword(config.dbPassword());
        hc.setMaximumPoolSize(10);
        hc.setPoolName("tessera-pool");
        this.dataSource = new HikariDataSource(hc);
    }

    /** Applies pending migrations from classpath:db/migration. */
    public void migrate() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public interface TxBlock<T> {
        T execute(java.sql.Connection conn) throws java.sql.SQLException;
    }

    public <T> T inTransaction(TxBlock<T> block) throws java.sql.SQLException {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = block.execute(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof java.sql.SQLException) {
                    throw (java.sql.SQLException) e;
                }
                throw new java.sql.SQLException("Transaction failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public DataSource dataSource() { return dataSource; }

    @Override
    public void close() {
        dataSource.close();
    }
}
