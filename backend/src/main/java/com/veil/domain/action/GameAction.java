package com.veil.domain.action;

import com.veil.engine.GameContext;
import com.veil.events.EventBus;
import com.veil.phases.GamePhaseType;

/**
 * Command pattern. Every player action is reified as an object so it can be
 * queued (during Night), validated against phase/role, resolved in a deterministic
 * order by {@code GameResolver}, and replayed. The engine treats all actions uniformly.
 */
public interface GameAction {

    /** Id of the player issuing this command. */
    String actorId();

    /** The phase during which this action is legal. */
    GamePhaseType requiredPhase();

    /**
     * Resolution priority (lower resolves first). Shields must apply before attacks,
     * attacks before investigations, and movement last.
     */
    int priority();

    /** Pure check: is this action currently valid? Must not mutate state. */
    boolean validate(GameContext ctx);

    /** Apply the effect and emit resulting events. Only called after {@link #validate}. */
    void execute(GameContext ctx, EventBus bus);
}
