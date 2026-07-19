package com.veil.domain.action;

import com.veil.domain.npc.NPC;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;
import com.veil.phases.GamePhaseType;

/**
 * Aegis shields a player or NPC for the current night. Resolves before attacks
 * (lower priority) so protection is in place when an attack is evaluated.
 */
public class ShieldAction implements GameAction {

    public static final int PRIORITY = 10;

    private final String actorId;
    private final String targetId;

    public ShieldAction(String actorId, String targetId) {
        this.actorId = actorId;
        this.targetId = targetId;
    }

    @Override public String actorId() { return actorId; }
    @Override public GamePhaseType requiredPhase() { return GamePhaseType.NIGHT; }
    @Override public int priority() { return PRIORITY; }

    @Override
    public boolean validate(GameContext ctx) {
        Player actor = ctx.players().get(actorId);
        if (actor == null || !actor.status().isAlive()) return false;
        if (actor.role().role() != com.veil.domain.player.Role.AEGIS) return false;
        return ctx.players().containsKey(targetId) || ctx.npcs().containsKey(targetId);
    }

    @Override
    public void execute(GameContext ctx, EventBus bus) {
        Player targetPlayer = ctx.players().get(targetId);
        if (targetPlayer != null) {
            targetPlayer.status().setProtectedThisNight(true);
            return;
        }
        NPC targetNpc = ctx.npcs().get(targetId);
        if (targetNpc != null) {
            targetNpc.setProtected(true);
        }
    }
}
