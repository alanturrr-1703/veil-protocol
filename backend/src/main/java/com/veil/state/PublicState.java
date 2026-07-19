package com.veil.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Broadcast-safe state. Everything here MAY be sent to any client. By construction
 * it contains no roles, memories, private actions, or investigation results.
 */
public class PublicState {

    private final List<String> announcements = new ArrayList<>();
    private final Map<String, Integer> lastVoteTally = new LinkedHashMap<>();
    // Who fell in the most recent night — surfaced at dawn for the reveal, then cleared.
    private final List<String> lastNightVictims = new ArrayList<>();

    public void addAnnouncement(String text) {
        announcements.add(text);
    }

    public List<String> announcements() {
        return Collections.unmodifiableList(announcements);
    }

    public void setVoteTally(Map<String, Integer> tally) {
        lastVoteTally.clear();
        lastVoteTally.putAll(tally);
    }

    public Map<String, Integer> lastVoteTally() {
        return Collections.unmodifiableMap(lastVoteTally);
    }

    // --- Dawn death reveal --------------------------------------------------

    public void recordNightVictim(String playerId) {
        lastNightVictims.add(playerId);
    }

    public List<String> lastNightVictims() {
        return Collections.unmodifiableList(lastNightVictims);
    }

    /** Cleared at the start of each night so the reveal only shows once, at the following dawn. */
    public void clearNightVictims() {
        lastNightVictims.clear();
    }
}
