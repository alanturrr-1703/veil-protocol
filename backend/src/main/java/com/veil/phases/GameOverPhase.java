package com.veil.phases;

import com.veil.domain.action.GameAction;
import com.veil.domain.player.Faction;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;

/**
 * Terminal phase. Reached when the confidential referee resolves a winning faction. It
 * accepts no further actions and always resolves to itself, so the match is frozen on its
 * final public state (roster, last vote, dawn reveal) for clients to display.
 */
public class GameOverPhase implements GamePhase {

    private final Faction winner;

    public GameOverPhase(Faction winner) {
        this.winner = winner;
    }

    public Faction winner() {
        return winner;
    }

    @Override
    public GamePhaseType type() {
        return GamePhaseType.GAME_OVER;
    }

    @Override
    public void onEnter(GameContext ctx, EventBus bus) {
        String who = winner == Faction.SHADOW ? "The Shadows" : "The City";
        ctx.publicState().addAnnouncement(who + " have won. The operation is over.");
    }

    @Override
    public void submit(GameAction action, GameContext ctx, EventBus bus) {
        // The match is over; ignore any late input.
    }

    @Override
    public GamePhase resolve(GameContext ctx, EventBus bus) {
        return this; // terminal
    }
}
