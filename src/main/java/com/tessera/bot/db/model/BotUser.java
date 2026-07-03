package com.tessera.bot.db.model;

/** Persistent bot-side user record. */
public class BotUser {
    public long telegramId;
    public String username;
    public String firstName;
    public String languageCode;
    public String remnawaveUuid;
    public String remnawaveUsername;
    public boolean blocked;
    public boolean trialUsed;
}
