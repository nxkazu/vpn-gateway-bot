package com.tessera.bot.module;

import com.tessera.core.api.BotModule;
import com.tessera.core.api.ModuleContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Discovers and loads {@link BotModule}s from standalone jars in the modules directory.
 * Each jar gets its own {@link URLClassLoader} with the application classloader as parent,
 * so modules see the core API (and shared libs like OkHttp/Jackson) while shading their
 * own private dependencies.
 */
@Slf4j
public final class ModuleLoader implements AutoCloseable {

    private final List<BotModule> modules = new ArrayList<>();
    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    /** Scans {@code dir} for {@code *.jar} files and initializes every module found. */
    public void loadAll(Path dir, ModuleContext context) {
        if (!Files.isDirectory(dir)) {
            log.info("Module directory {} does not exist; running without modules.", dir.toAbsolutePath());
            return;
        }
        try (Stream<Path> jars = Files.list(dir)) {
            jars.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(jar -> loadJar(jar, context));
        } catch (IOException e) {
            log.error("Failed to scan module directory {}", dir, e);
        }
        log.info("Loaded {} module(s).", modules.size());
    }

    private void loadJar(Path jar, ModuleContext context) {
        try {
            URLClassLoader cl = new URLClassLoader(
                    new URL[]{jar.toUri().toURL()}, getClass().getClassLoader());
            classLoaders.add(cl);
            int found = 0;
            for (BotModule module : ServiceLoader.load(BotModule.class, cl)) {
                if (!isCompatible(module, context)) {
                    log.error("Skipping module '{}' from {}: API version {} is incompatible with host API {}",
                            module.id(), jar.getFileName(), module.apiVersion(), BotModule.API_VERSION);
                    continue;
                }
                module.init(context);
                modules.add(module);
                found++;
                log.info("Enabled module '{}' from {}", module.id(), jar.getFileName());
            }
            if (found == 0) {
                log.warn("No BotModule services found in {}", jar.getFileName());
            }
        } catch (Exception e) {
            log.error("Failed to load module jar {}", jar, e);
        }
    }

    private boolean isCompatible(BotModule module, ModuleContext context) {
        if (module.apiVersion() == BotModule.API_VERSION) {
            return true;
        }
        return Boolean.parseBoolean(context.env("MODULE_ALLOW_UNSUPPORTED_API", "false"));
    }

    public List<BotModule> modules() {
        return List.copyOf(modules);
    }

    @Override
    public void close() {
        for (BotModule m : modules) {
            try {
                m.shutdown();
            } catch (Exception e) {
                log.warn("Module '{}' shutdown failed", m.id(), e);
            }
        }
        for (URLClassLoader cl : classLoaders) {
            try {
                cl.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
