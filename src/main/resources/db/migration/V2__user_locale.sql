-- Preferred UI language chosen explicitly by the user (overrides Telegram's language_code).
-- Two-letter code matching com.tessera.bot.i18n.Lang (en, ru, es, de, fr, zh).
ALTER TABLE bot_users ADD COLUMN locale TEXT;
