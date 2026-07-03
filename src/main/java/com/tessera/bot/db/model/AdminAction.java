package com.tessera.bot.db.model;

import java.time.OffsetDateTime;

/** A single recorded administrative action (audit trail row). */
public class AdminAction {
    public long id;
    public long adminId;
    public String action;
    public String target;
    public String details;
    public OffsetDateTime createdAt;
}
