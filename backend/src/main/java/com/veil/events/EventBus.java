package com.veil.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Subject in the Observer pattern. Decouples the resolver from consumers and keeps
 * a full append-only log — the same stream that powers replay and analytics.
 */
public class EventBus {

    private final List<GameEventListener> listeners = new ArrayList<>();
    private final List<GameEvent> log = new ArrayList<>();

    public void register(GameEventListener listener) {
        listeners.add(listener);
    }

    public void publish(GameEvent event) {
        log.add(event);
        for (GameEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    /** The complete replay log, in emission order. */
    public List<GameEvent> log() {
        return Collections.unmodifiableList(log);
    }
}
