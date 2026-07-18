package com.veil.api;

import com.veil.api.DTOs.DtoAssembler;
import com.veil.api.DTOs.PlayerView;
import com.veil.engine.VeilEngine;
import com.veil.events.GameEvent;

import java.util.function.BiConsumer;

/**
 * Push side of the transport. Subscribes to the engine's event stream and, on each
 * change, pushes a freshly redacted {@link PlayerView} to each connected viewer.
 * The actual socket send is injected so this stays framework-free and testable.
 */
public class WebSocketHandler {

    private final VeilEngine engine;
    private final BiConsumer<String, PlayerView> sendToClient;

    public WebSocketHandler(VeilEngine engine, BiConsumer<String, PlayerView> sendToClient) {
        this.engine = engine;
        this.sendToClient = sendToClient;
        engine.addListener(this::onEvent);
    }

    private void onEvent(GameEvent event) {
        // Fan out redacted snapshots. Each viewer only ever receives their own PlayerView;
        // private events are already scoped by GameEvent#isVisibleTo at the source.
        for (String viewerId : engine.context().players().keySet()) {
            if (event.isVisibleTo(viewerId)) {
                PlayerView view = DtoAssembler.forViewer(engine.context(), engine.currentPhase(), viewerId, java.util.Set.of());
                sendToClient.accept(viewerId, view);
            }
        }
    }
}
