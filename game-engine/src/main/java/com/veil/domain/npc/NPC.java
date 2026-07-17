package com.veil.domain.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A non-player witness. NPCs are knowledge holders, not chatbots: an answer is a
 * pure function of (memory, personality, trust, question). This guarantees two
 * players asking the same thing get consistent answers, and that an NPC only ever
 * reveals what it actually witnessed.
 */
public class NPC {

    private final String id;
    private final String displayName;
    private final Personality personality;
    private final MemoryBank memory = new MemoryBank();
    private final Map<String, Double> trust = new HashMap<>();

    private String locationId;
    private boolean alive = true;
    private boolean protectedThisNight = false;

    public NPC(String id, String displayName, Personality personality, String locationId) {
        this.id = id;
        this.displayName = displayName;
        this.personality = personality;
        this.locationId = locationId;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Personality personality() { return personality; }
    public MemoryBank memory() { return memory; }

    public String locationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }

    public boolean isAlive() { return alive; }
    public void eliminate() { this.alive = false; }

    public boolean isProtected() { return protectedThisNight; }
    public void setProtected(boolean value) { this.protectedThisNight = value; }

    public double trustIn(String askerId) {
        return trust.getOrDefault(askerId, 0.5);
    }

    public void setTrust(String askerId, double value) {
        trust.put(askerId, value);
    }

    /** Record a witnessed fact. */
    public void witness(Observation observation) {
        memory.add(observation);
    }

    /**
     * Answer a question, deterministically, using only memory + personality + trust.
     * Returns the subset of matching memories this NPC is willing to share with this asker.
     */
    public List<Observation> recall(String topic, String askerId) {
        double trustInAsker = trustIn(askerId);
        return memory.recall(o -> o.matchesTopic(topic) && personality.willShare(o, trustInAsker));
    }
}
