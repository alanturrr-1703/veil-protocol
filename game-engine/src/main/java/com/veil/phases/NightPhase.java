package com.veil.phases;

import com.veil.domain.action.GameAction;
import com.veil.domain.npc.NPC;
import com.veil.domain.player.Player;
import com.veil.engine.GameContext;
import com.veil.engine.GameResolver;
import com.veil.events.EventBus;

/**
 * Night: actions are submitted secretly and queued into confidential state. Nothing
 * is applied until the phase resolves, at which point the resolver executes the batch
 * in priority order. Protection flags are cleared afterwards.
 */
public class NightPhase implements GamePhase {

    private final GameResolver resolver;

    public NightPhase(GameResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public GamePhaseType type() {
        return GamePhaseType.NIGHT;
    }

    @Override
    public void onEnter(GameContext ctx, EventBus bus) {
        ctx.advanceTick();
        ctx.publicState().addAnnouncement("Night falls over Neon City.");
    }

    @Override
    public void submit(GameAction action, GameContext ctx, EventBus bus) {
        // Confidential: queued into private state, never applied or revealed yet.
        ctx.privateState().queueAction(action);
    }

    @Override
    public GamePhase resolve(GameContext ctx, EventBus bus) {
        resolver.resolve(ctx.privateState().drainQueuedActions(), ctx, bus);
        clearNightProtection(ctx);
        return new DayPhase();
    }

    private void clearNightProtection(GameContext ctx) {
        for (Player p : ctx.players().values()) {
            p.status().setProtectedThisNight(false);
        }
        for (NPC n : ctx.npcs().values()) {
            n.setProtected(false);
        }
    }
}
