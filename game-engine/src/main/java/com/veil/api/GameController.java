package com.veil.api;

import com.veil.api.DTOs.DtoAssembler;
import com.veil.api.DTOs.PlayerView;
import com.veil.domain.action.GameAction;
import com.veil.engine.VeilEngine;

/**
 * Application-facing facade over the engine. In a real deployment this would be a
 * REST/WebSocket controller (e.g. Spring); kept framework-free here. Note it can only
 * ever hand clients a {@link PlayerView} produced by the redaction boundary.
 */
public class GameController {

    private final VeilEngine engine;

    public GameController(VeilEngine engine) {
        this.engine = engine;
    }

    public boolean submitAction(GameAction action) {
        return engine.submit(action);
    }

    public void vote(String voterId, String targetId) {
        engine.castVote(voterId, targetId);
    }

    public void advancePhase() {
        engine.advancePhase();
    }

    /** Returns a redacted, per-viewer snapshot — never raw domain/private state. */
    public PlayerView viewFor(String viewerId) {
        return DtoAssembler.forViewer(engine.context(), engine.currentPhase(), viewerId, java.util.Set.of(), 0L);
    }
}
