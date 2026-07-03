package com.tessera.bot.bot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the transient conversational state of admins/users for multi-step flows
 * (e.g. waiting for broadcast text or a username to look up). In-memory by design;
 * flows are short-lived and safe to lose on restart.
 */
public class UserState {

    public enum Await {
        NONE, BROADCAST_TEXT, FIND_USER_QUERY,
        ADD_DAYS, SET_TRAFFIC, CREATE_SUB, SUPPORT_TEXT
    }

    private final Map<Long, Await> awaiting = new ConcurrentHashMap<>();
    /** Optional payload carried alongside a pending state (e.g. the user uuid being edited,
     *  or the chosen broadcast segment). Cleared together with the state. */
    private final Map<Long, String> payloads = new ConcurrentHashMap<>();
    /** A composed-but-not-yet-sent broadcast, awaiting explicit confirmation: [segment, text]. */
    private final Map<Long, String[]> pendingBroadcast = new ConcurrentHashMap<>();

    public void set(long chatId, Await state) { awaiting.put(chatId, state); }
    public void set(long chatId, Await state, String payload) {
        awaiting.put(chatId, state);
        payloads.put(chatId, payload);
    }
    public Await get(long chatId) { return awaiting.getOrDefault(chatId, Await.NONE); }
    public String payload(long chatId) { return payloads.get(chatId); }
    public void putPayload(long chatId, String payload) { payloads.put(chatId, payload); }
    public void clear(long chatId) { awaiting.remove(chatId); payloads.remove(chatId); }

    public void setPendingBroadcast(long chatId, String segment, String text) {
        pendingBroadcast.put(chatId, new String[]{segment, text});
    }
    public String[] getPendingBroadcast(long chatId) { return pendingBroadcast.get(chatId); }
    public void clearPendingBroadcast(long chatId) { pendingBroadcast.remove(chatId); }
}
