package com.veil.events;

import java.util.Collections;
import java.util.Set;

/**
 * Base type for every world change. Events are the single append-only stream that
 * feeds the narrator, spectator mode, replay, and analytics. Each event carries its
 * own visibility so consumers on the public path can never accidentally read a
 * private event's payload.
 */
public abstract class GameEvent {

    private final long tick;
    private final Visibility visibility;
    private final Set<String> audience;

    protected GameEvent(long tick, Visibility visibility, Set<String> audience) {
        this.tick = tick;
        this.visibility = visibility;
        this.audience = audience == null ? Set.of() : Set.copyOf(audience);
    }

    public long tick() { return tick; }
    public Visibility visibility() { return visibility; }

    /** For PRIVATE events, the ids allowed to receive it. Empty for PUBLIC events. */
    public Set<String> audience() { return Collections.unmodifiableSet(audience); }

    public boolean isVisibleTo(String viewerId) {
        return visibility == Visibility.PUBLIC || audience.contains(viewerId);
    }

    /** Human-readable line for narration / logs. */
    public abstract String describe();
}
