package com.veil.phases;

import com.veil.domain.action.GameAction;
import com.veil.domain.player.Player;
import com.veil.domain.world.Location;
import com.veil.engine.GameContext;
import com.veil.engine.GameResolver;
import com.veil.events.AttackEvent;
import com.veil.events.EventBus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Voting: players cast votes; the target with the most votes is eliminated. The
 * tally and outcome are public. Resolves back into Night for the next cycle.
 */
public class VotingPhase implements GamePhase {

    private final Map<String, String> votes = new LinkedHashMap<>();
    private final GameResolver resolver;

    public VotingPhase() {
        this(new GameResolver());
    }

    public VotingPhase(GameResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public GamePhaseType type() {
        return GamePhaseType.VOTING;
    }

    @Override
    public void onEnter(GameContext ctx, EventBus bus) {
        ctx.advanceTick();
        ctx.publicState().addAnnouncement("The vote begins.");
    }

    @Override
    public void submit(GameAction action, GameContext ctx, EventBus bus) {
        // Voting takes votes, not world actions; ignore stray commands here.
    }

    /** Record a vote from a living player against a target. */
    public void castVote(String voterId, String targetId, GameContext ctx) {
        Player voter = ctx.players().get(voterId);
        if (voter != null && voter.status().isAlive() && ctx.players().containsKey(targetId)) {
            votes.put(voterId, targetId);
        }
    }

    @Override
    public GamePhase resolve(GameContext ctx, EventBus bus) {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (String target : votes.values()) {
            tally.merge(target, 1, Integer::sum);
        }
        ctx.publicState().setVoteTally(tally);

        String eliminated = null;
        int best = 0;
        for (Map.Entry<String, Integer> e : tally.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                eliminated = e.getKey();
            }
        }

        if (eliminated != null) {
            Player target = ctx.players().get(eliminated);
            target.status().kill();
            Location loc = ctx.city().location(target.locationId());
            if (loc != null) loc.removePlayer(eliminated);
            ctx.publicState().addAnnouncement(eliminated + " was exiled by the city.");
            bus.publish(new AttackEvent(ctx.tick(), "city-vote", eliminated, false));
        } else {
            ctx.publicState().addAnnouncement("The vote was inconclusive.");
        }

        return new NightPhase(resolver);
    }
}
