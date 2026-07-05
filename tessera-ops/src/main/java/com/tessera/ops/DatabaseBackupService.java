package com.tessera.ops;

import com.tessera.bot.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Runs pg_dump backups and retention cleanup. */
public final class DatabaseBackupService implements AutoCloseable {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AppConfig config;
    private ScheduledExecutorService executor;

    public DatabaseBackupService(AppConfig config) {
        this.config = config;
    }

    public BackupResult backupNow() throws Exception {
        Path dir = Path.of(config.backupDirectory()).toAbsolutePath();
        Files.createDirectories(dir);
        Path file = dir.resolve("tessera_" + OffsetDateTime.now(ZoneOffset.UTC).format(TS) + ".dump");
        ProcessBuilder pb = new ProcessBuilder(
                config.pgDumpBin(),
                "--host", config.dbHost(),
                "--port", String.valueOf(config.dbPort()),
                "--username", config.dbUser(),
                "--dbname", config.dbName(),
                "--format", "custom",
                "--no-owner",
                "--no-acl",
                "--file", file.toString());
        Map<String, String> env = pb.environment();
        env.put("PGPASSWORD", config.dbPassword());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            Files.deleteIfExists(file);
            throw new IOException("pg_dump failed with code " + code + ": " + output);
        }
        cleanupRetention(dir);
        return new BackupResult(file, Files.size(file));
    }

    public void startScheduled(int hours, java.util.function.Consumer<String> onFailure) {
        if (hours <= 0) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                backupNow();
            } catch (Exception e) {
                onFailure.accept(e.getMessage());
            }
        }, hours, hours, TimeUnit.HOURS);
    }

    private void cleanupRetention(Path dir) throws IOException {
        int days = config.backupRetentionDays();
        if (days <= 0) return;
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().startsWith("tessera_"))
                    .filter(p -> p.getFileName().toString().endsWith(".dump"))
                    .filter(p -> modified(p) < cutoff)
                    .sorted(Comparator.naturalOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                    });
        }
    }

    private long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public void close() {
        if (executor != null) executor.shutdownNow();
    }

    public record BackupResult(Path file, long bytes) {}
}
