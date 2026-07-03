package com.tessera.bot.bot;

/** Callback-data constants shared between keyboards and handlers. */
public final class Callbacks {
    private Callbacks() {}

    // User menu
    public static final String MENU_MAIN = "menu:main";
    public static final String MENU_BUY = "menu:buy";
    public static final String MENU_SUB = "menu:sub";
    public static final String MENU_TRIAL = "menu:trial";
    public static final String MENU_REFERRAL = "menu:referral";
    public static final String MENU_HELP = "menu:help";
    public static final String MENU_LANGUAGE = "menu:lang";

    public static final String BUY_PREFIX = "buy:";     // buy:<planCode>
    public static final String LANG_PREFIX = "lang:";   // lang:<code>

    // Admin menu
    public static final String ADMIN_MAIN = "admin:main";
    public static final String ADMIN_STATS = "admin:stats";
    public static final String ADMIN_NODES = "admin:nodes";
    public static final String ADMIN_HEALTH = "admin:health";
    public static final String ADMIN_BROADCAST = "admin:broadcast";
    public static final String ADMIN_FIND_USER = "admin:finduser";
    public static final String ADMIN_NODE_RESTART_ALL = "admin:nodes:restartall";

    public static final String ADMIN_USER_PREFIX = "admin:user:";      // admin:user:<action>:<uuid>
}
