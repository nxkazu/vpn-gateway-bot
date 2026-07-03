package com.tessera.bot.bot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the transient conversational state of admins/users for multi-step flows
 * (e.g. waiting for broadcast text or a username to look up). In-memory by design;
 * flows are short-lived and safe to lose on restart.
 */
public class UserState {

    public enum Await { NONE, BROADCAST_TEXT, FIND_USER_QUERY }

    private final Map<Long, Await> awaiting = new ConcurrentHashMap<>();

    public void set(long chatId, Await state) { awaiting.put(chatId, state); }
    public Await get(long chatId) { return awaiting.getOrDefault(chatId, Await.NONE); }
    public void clear(long chatId) { awaiting.remove(chatId); }
}
