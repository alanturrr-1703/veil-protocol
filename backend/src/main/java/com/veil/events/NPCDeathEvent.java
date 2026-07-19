package com.veil.events;

import java.util.Set;

/**
 * An NPC witness was eliminated by a Shadow hunt. Public: the city notices the NPC
 * is gone (and thus a source of information has been destroyed).
 */
public class NPCDeathEvent extends GameEvent {

    private final String hunterId;
    private final String npcId;
    private final String locationId;

    public NPCDeathEvent(long tick, String hunterId, String npcId, String locationId) {
        super(tick, Visibility.PUBLIC, Set.of());
        this.hunterId = hunterId;
        this.npcId = npcId;
        this.locationId = locationId;
    }

    public String hunterId() { return hunterId; }
    public String npcId() { return npcId; }
    public String locationId() { return locationId; }

    @Override
    public String describe() {
        return "Witness " + npcId + " was silenced at " + locationId + ".";
    }
}
