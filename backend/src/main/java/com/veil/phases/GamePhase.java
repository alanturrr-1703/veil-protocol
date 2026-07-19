package com.veil.phases;

import com.veil.domain.action.GameAction;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;

/**
 * State pattern. Each phase owns its own rules for accepting actions and for
 * resolving into the next phase. This removes phase {@code switch} statements from
 * the engine and prevents one phase's rules from bleeding into another.
 */
public interface GamePhase {

    GamePhaseType type();

    /** Called once when the phase becomes active. */
    void onEnter(GameContext ctx, EventBus bus);

    /** Accept an action: queue it (Night) or apply it immediately (Day). */
    void submit(GameAction action, GameContext ctx, EventBus bus);

    /** Resolve this phase and return the next phase to transition into. */
    GamePhase resolve(GameContext ctx, EventBus bus);
}
