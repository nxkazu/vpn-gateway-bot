package com.tessera.dashboard;

import com.tessera.core.api.BotModule;
import com.tessera.core.api.ModuleContext;

/**
 * Admin dashboard module: embedded web UI for support tickets (user opens a ticket in
 * the bot -> admin replies here -> reply is delivered to the user in Telegram), plan
 * management (the {@code plans} table) and basic stats.
 *
 * Configuration (env): {@code DASHBOARD_TOKEN} (required; the UI login token),
 * {@code DASHBOARD_PORT} (default 8080), {@code DASHBOARD_BIND} (default 0.0.0.0).
 */
public final class DashboardModule implements BotModule {

    private static final System.Logger LOG = System.getLogger(DashboardModule.class.getName());

    private DashboardServer server;

    @Override
    public String id() {
        return "admin-dashboard";
    }

    @Override
    public void init(ModuleContext ctx) throws Exception {
        String token = ctx.env("DASHBOARD_TOKEN", "");
        if (token.isBlank()) {
            LOG.log(System.Logger.Level.WARNING,
                    "Admin dashboard disabled: DASHBOARD_TOKEN is not set");
            return;
        }
        int port = Integer.parseInt(ctx.env("DASHBOARD_PORT", "8080"));
        String bind = ctx.env("DASHBOARD_BIND", "0.0.0.0");
        server = new DashboardServer(bind, port, token, ctx.dataSource(), ctx.notifier());
        server.start();
        LOG.log(System.Logger.Level.INFO, "Admin dashboard listening on {0}:{1}", bind, port);
    }

    @Override
    public void shutdown() {
        if (server != null) {
            server.stop();
        }
    }
}