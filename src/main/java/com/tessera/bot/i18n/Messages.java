package com.tessera.bot.i18n;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads localized message bundles from {@code classpath:i18n/messages[_xx].properties} and
 * resolves keys with positional {@code {0}, {1}, ...} placeholder substitution.
 *
 * Lookup order for a key: requested language bundle -> English base bundle -> the key itself.
 * This gives every language a guaranteed English fallback for any missing key.
 */
@Slf4j
public class Messages {

    private final Map<Lang, Properties> bundles = new EnumMap<>(Lang.class);
    private final Properties base = new Properties();

    public Messages() {
        load("/i18n/messages.properties", base);
        for (Lang lang : Lang.values()) {
            Properties p = new Properties();
            load("/i18n/messages_" + lang.code() + ".properties", p);
            bundles.put(lang, p);
        }
    }

    private void load(String resource, Properties into) {
        try (InputStream in = Messages.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("i18n resource not found: {}", resource);
                return;
            }
            into.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Failed to load i18n resource {}", resource, e);
        }
    }

    /** Translates {@code key} for {@code lang}, substituting positional arguments. */
    public String t(Lang lang, String key, Object... args) {
        Properties p = bundles.getOrDefault(lang, base);
        String value = p.getProperty(key);
        if (value == null) value = base.getProperty(key);
        if (value == null) return key;
        return substitute(value, args);
    }

    private String substitute(String template, Object... args) {
        if (args == null || args.length == 0) return template;
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", args[i] == null ? "" : args[i].toString());
        }
        return result;
    }
}
