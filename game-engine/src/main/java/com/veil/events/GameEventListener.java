package com.veil.events;

/**
 * Observer. Implemented by the narrator, spectator feed, replay log, analytics, etc.
 */
@FunctionalInterface
public interface GameEventListener {
    void onEvent(GameEvent event);
}
