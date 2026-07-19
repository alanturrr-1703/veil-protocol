package com.veil.domain.action;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.domain.player.Role;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;
import com.veil.events.EvidenceEvent;
import com.veil.phases.GamePhaseType;

/**
 * Oracle investigates a target and learns a partial truth (the target's faction,
 * with a confidence that can be noised for balance). The result is written to the
 * confidential store keyed by the Oracle, and surfaced only via a private event.
 */
public class InvestigateAction implements GameAction {

    public static final int PRIORITY = 30;

    private final String actorId;
    private final String targetId;

    public InvestigateAction(String actorId, String targetId) {
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
        if (actor.role().role() != Role.ORACLE) return false;
        return ctx.players().containsKey(targetId);
    }

    @Override
    public void execute(GameContext ctx, EventBus bus) {
        Player target = ctx.players().get(targetId);
        // Selective disclosure through the confidential referee: it returns ONLY the target's
        // faction bit (never the exact role). Falls back to the local role when no referee is
        // wired (Demo/tests).
        Faction reading = ctx.confidential() != null
                ? ctx.confidential().investigate(actorId, targetId)
                : target.role().faction();
        ctx.privateState().recordInvestigation(actorId, targetId, reading);

        String detail = "Investigation of " + targetId + " reads as faction " + reading;
        bus.publish(EvidenceEvent.privateTo(ctx.tick(), actorId, targetId, detail));
    }
}
