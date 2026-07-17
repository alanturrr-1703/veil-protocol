package com.veil.engine;

import com.veil.domain.action.GameAction;
import com.veil.domain.player.Player;
import com.veil.events.EventBus;
import com.veil.events.GameEventListener;
import com.veil.phases.GamePhase;
import com.veil.phases.NightPhase;
import com.veil.phases.VotingPhase;

/**
 * Orchestrator and single writer for one match. All commands funnel through here,
 * which enforces phase + role legality before delegating to the current phase state.
 * Keeping one authoritative engine per match makes resolution deterministic and
 * lock-free.
 */
public class VeilEngine {

    private final GameContext context;
    private final EventBus eventBus = new EventBus();
    private final GameResolver resolver = new GameResolver();

    private GamePhase currentPhase;

    public VeilEngine(GameContext context) {
        this.context = context;
    }

    public GameContext context() { return context; }
    public EventBus eventBus() { return eventBus; }
    public GamePhase currentPhase() { return currentPhase; }

    public void addListener(GameEventListener listener) {
        eventBus.register(listener);
    }

    /** Begin the match at Night. */
    public void start() {
        currentPhase = new NightPhase(resolver);
        currentPhase.onEnter(context, eventBus);
    }

    /**
     * Submit a player command. Rejected unless it is legal for the current phase and
     * the player's role permits it.
     */
    public boolean submit(GameAction action) {
        if (currentPhase == null) return false;
        if (action.requiredPhase() != currentPhase.type()) return false;

        Player player = context.players().get(action.actorId());
        if (player == null || !player.status().isAlive()) return false;
        if (!player.role().canPerform(action, currentPhase.type())) return false;

        currentPhase.submit(action, context, eventBus);
        return true;
    }

    /** Cast a vote (only meaningful during the Voting phase). */
    public void castVote(String voterId, String targetId) {
        if (currentPhase instanceof VotingPhase voting) {
            voting.castVote(voterId, targetId, context);
        }
    }

    /** Resolve the current phase and transition to the next. */
    public void advancePhase() {
        currentPhase = currentPhase.resolve(context, eventBus);
        currentPhase.onEnter(context, eventBus);
    }
}
