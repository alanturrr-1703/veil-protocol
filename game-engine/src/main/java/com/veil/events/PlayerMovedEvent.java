package com.veil.events;

import java.util.Set;

/**
 * A player moved between locations. Public map information.
 */
public class PlayerMovedEvent extends GameEvent {

    private final String playerId;
    private final String fromLocationId;
    private final String toLocationId;

    public PlayerMovedEvent(long tick, String playerId, String fromLocationId, String toLocationId) {
        super(tick, Visibility.PUBLIC, Set.of());
        this.playerId = playerId;
        this.fromLocationId = fromLocationId;
        this.toLocationId = toLocationId;
    }

    public String playerId() { return playerId; }
    public String fromLocationId() { return fromLocationId; }
    public String toLocationId() { return toLocationId; }

    @Override
    public String describe() {
        return playerId + " moved from " + fromLocationId + " to " + toLocationId + ".";
    }
}
