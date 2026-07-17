package com.veil.events;

import java.util.Set;

/**
 * An attack was attempted on a player. Public: the city learns someone was struck
 * (or that an attack was thwarted), but not who ordered it.
 */
public class AttackEvent extends GameEvent {

    private final String attackerId;
    private final String targetId;
    private final boolean blocked;

    public AttackEvent(long tick, String attackerId, String targetId, boolean blocked) {
        super(tick, Visibility.PUBLIC, Set.of());
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.blocked = blocked;
    }

    public String attackerId() { return attackerId; }
    public String targetId() { return targetId; }
    public boolean blocked() { return blocked; }

    @Override
    public String describe() {
        if (blocked) {
            return "An attack on " + targetId + " was thwarted.";
        }
        if ("city-vote".equals(attackerId)) {
            return targetId + " was exiled by the city vote.";
        }
        return targetId + " was eliminated in the night.";
    }
}
