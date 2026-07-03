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

    public DataSource dataSource() { return dataSource; }

    @Override
    public void close() {
        dataSource.close();
    }
}
