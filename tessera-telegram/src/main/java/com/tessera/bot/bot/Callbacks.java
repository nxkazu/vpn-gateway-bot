package com.tessera.bot.bot;

/** Callback-data constants shared between keyboards and handlers. */
public final class Callbacks {
    private Callbacks() {}

    // User menu
    public static final String MENU_MAIN = "menu:main";
    public static final String MENU_BUY = "menu:buy";
    public static final String MENU_SUB = "menu:sub";
    public static final String MENU_TRIAL = "menu:trial";
    public static final String MENU_SETUP = "menu:setup";
    public static final String MENU_REFERRAL = "menu:referral";
    public static final String MENU_HELP = "menu:help";
    public static final String MENU_LANGUAGE = "menu:lang";
    public static final String MENU_SUPPORT = "menu:support";

    public static final String BUY_PREFIX = "buy:";     // buy:<planCode>
    public static final String LANG_PREFIX = "lang:";   // lang:<code>
    public static final String SETUP_PREFIX = "setup:"; // setup:<platform>

    // Payments
    public static final String PAY_PREFIX = "pay:";           // pay:<providerId>:<planCode>
    public static final String PAY_CHECK_PREFIX = "paychk:";  // paychk:<invoiceId>
    public static final String PROMO_PREFIX = "promo:";      // promo:<planCode>

    // Admin menu
    public static final String ADMIN_MAIN = "admin:main";
    public static final String ADMIN_DASHBOARD = "admin:dash";
    public static final String ADMIN_USERS = "admin:users";              // list, page 0
    public static final String ADMIN_USERS_PAGE_PREFIX = "admin:users:";  // admin:users:<page>
    public static final String ADMIN_SQUADS = "admin:squads";
    public static final String ADMIN_AUDIT = "admin:audit";
    public static final String ADMIN_SETTINGS = "admin:settings";
    public static final String ADMIN_CREATE_SUB = "admin:createsub";
    public static final String ADMIN_NODE_PREFIX = "admin:node:";        // admin:node:<action>:<uuid>
    public static final String ADMIN_BC_PREFIX = "admin:bc:";            // admin:bc:<segment>
    public static final String ADMIN_BC_CONFIRM = "admin:bcok";
    public static final String ADMIN_BC_CANCEL = "admin:bccancel";
    public static final String ADMIN_STATS = "admin:stats";
    public static final String ADMIN_NODES = "admin:nodes";
    public static final String ADMIN_HEALTH = "admin:health";
    public static final String ADMIN_BROADCAST = "admin:broadcast";
    public static final String ADMIN_FIND_USER = "admin:finduser";
    public static final String ADMIN_NODE_RESTART_ALL = "admin:nodes:restartall";

    public static final String ADMIN_USER_PREFIX = "admin:user:";      // admin:user:<action>:<uuid>
    // Extended per-user admin actions carried on ADMIN_USER_PREFIX:
    //   adddays:<uuid>:<n>, adddaysx:<uuid>, traffic:<uuid>, hwid:<uuid>,
    //   hwiddel:<uuid>:<hwid>, squads:<uuid>, squadtog:<uuid>:<squadUuid>, card:<uuid>
}
