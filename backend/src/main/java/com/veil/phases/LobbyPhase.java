package com.veil.phases;

import com.veil.domain.action.GameAction;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;

/**
 * Lobby: the pre-match waiting room. Players join by room code and are free to walk the
 * city, but no roles are dealt and no night/vote actions are accepted yet. The room's host
 * starts the match, which deals roles (committed to the confidential referee) and resolves
 * the lobby into the first Night.
 */
public class LobbyPhase implements GamePhase {

    @Override
    public GamePhaseType type() {
        return GamePhaseType.LOBBY;
    }

    @Override
    public void onEnter(GameContext ctx, EventBus bus) {
        ctx.publicState().addAnnouncement("A new operation is forming. Operatives are gathering in Neon City.");
    }

    @Override
    public void submit(GameAction action, GameContext ctx, EventBus bus) {
        // No queued or immediate game actions in the lobby; movement is handled directly
        // by the engine (free-roam) so players can mill about before the match begins.
    }

    @Override
    public GamePhase resolve(GameContext ctx, EventBus bus) {
        // Roles are dealt by the room service just before this transition; the match opens
        // at Night. A fresh GameResolver is handed to Night so its batch resolution is isolated.
        return new NightPhase(new com.veil.engine.GameResolver());
    }
}
