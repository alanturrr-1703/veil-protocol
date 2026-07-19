package com.veil.domain.action;

import com.veil.domain.player.Player;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;
import com.veil.phases.GamePhaseType;

/**
 * A player's vote to exile a suspect during the Voting phase. Reified as a {@link GameAction}
 * so every player input travels the same Command path (submit -> phase). Unlike night
 * commands, a vote is not batch-resolved: the {@code VotingPhase} records it on submit and
 * tallies at resolution, so {@link #execute} is intentionally a no-op.
 */
public class VoteAction implements GameAction {

    public static final int PRIORITY = 5;

    private final String actorId;
    private final String targetId;

    public VoteAction(String actorId, String targetId) {
        this.actorId = actorId;
        this.targetId = targetId;
    }

    @Override public String actorId() { return actorId; }
    @Override public GamePhaseType requiredPhase() { return GamePhaseType.VOTING; }
    @Override public int priority() { return PRIORITY; }

    public String targetId() { return targetId; }

    @Override
    public boolean validate(GameContext ctx) {
        Player voter = ctx.players().get(actorId);
        if (voter == null || !voter.status().isAlive()) return false;
        return ctx.players().containsKey(targetId);
    }

    /** No-op: the VotingPhase records the vote on submit and tallies it at resolution. */
    @Override
    public void execute(GameContext ctx, EventBus bus) {
        // Intentionally empty — see class javadoc.
    }
}
