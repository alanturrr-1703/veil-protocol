package com.veil.phases;

import com.veil.domain.action.GameAction;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;

/**
 * Day: public discussion and information gathering. Movement and NPC queries are
 * applied immediately (they are public / non-secret). Resolves into Voting.
 */
public class DayPhase implements GamePhase {

    @Override
    public GamePhaseType type() {
        return GamePhaseType.DAY;
    }

    @Override
    public void onEnter(GameContext ctx, EventBus bus) {
        ctx.advanceTick();
        ctx.publicState().addAnnouncement("Day breaks. The city talks.");
    }

    @Override
    public void submit(GameAction action, GameContext ctx, EventBus bus) {
        if (action.validate(ctx)) {
            action.execute(ctx, bus);
        }
    }

    @Override
    public GamePhase resolve(GameContext ctx, EventBus bus) {
        return new VotingPhase();
    }
}
