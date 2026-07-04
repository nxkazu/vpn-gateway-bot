package com.tessera.core.api;

/**
 * SPI for pluggable bot modules. Implementations are discovered from jars in the
 * modules directory via {@link java.util.ServiceLoader} (register the implementation
 * class in {@code META-INF/services/com.tessera.core.api.BotModule}).
 */
public interface BotModule {

    /** Stable identifier of this module (e.g. {@code tessera-payment}). */
    String id();

    /**
     * Called once when the module is loaded. The module should read its configuration
     * and register its capabilities (e.g. {@link PaymentProvider}s) with the context.
     */
    void init(ModuleContext context) throws Exception;

    /** Called on application shutdown. */
    default void shutdown() {}
}
