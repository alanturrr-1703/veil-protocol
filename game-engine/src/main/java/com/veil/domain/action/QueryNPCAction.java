package com.veil.domain.action;

import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Observation;
import com.veil.domain.player.Player;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;
import com.veil.events.EvidenceEvent;
import com.veil.phases.GamePhaseType;

import java.util.List;

/**
 * A player questions an NPC about a topic. The NPC answers ONLY from its own
 * memory + personality, so two players asking the same thing get consistent
 * answers. The result is confidential to the asker.
 */
public class QueryNPCAction implements GameAction {

    public static final int PRIORITY = 35;

    private final String actorId;
    private final String npcId;
    private final String topic;
    private final GamePhaseType phase;

    public QueryNPCAction(String actorId, String npcId, String topic, GamePhaseType phase) {
        this.actorId = actorId;
        this.npcId = npcId;
        this.topic = topic;
        this.phase = phase;
    }

    @Override public String actorId() { return actorId; }
    @Override public GamePhaseType requiredPhase() { return phase; }
    @Override public int priority() { return PRIORITY; }

    @Override
    public boolean validate(GameContext ctx) {
        Player actor = ctx.players().get(actorId);
        if (actor == null || !actor.status().isAlive()) return false;
        NPC npc = ctx.npcs().get(npcId);
        if (npc == null || !npc.isAlive()) return false;
        // You must be face to face — same district AND same room — to question a witness.
        return actor.locationId() != null
                && actor.locationId().equals(npc.locationId())
                && actor.roomId().equals(npc.roomId());
    }

    @Override
    public void execute(GameContext ctx, EventBus bus) {
        NPC npc = ctx.npcs().get(npcId);
        List<Observation> shared = npc.recall(topic, actorId);
        ctx.privateState().recordNpcAnswer(actorId, npcId, shared);

        String detail = npc.displayName() + " shared " + shared.size()
                + " memory item(s) about '" + topic + "'";
        bus.publish(EvidenceEvent.privateTo(ctx.tick(), actorId, npcId, detail));
    }
}
