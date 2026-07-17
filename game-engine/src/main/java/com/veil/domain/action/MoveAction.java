package com.veil.domain.action;

import com.veil.domain.player.Player;
import com.veil.domain.world.Location;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;
import com.veil.events.PlayerMovedEvent;
import com.veil.phases.GamePhaseType;

/**
 * Move a player to an adjacent location. Movement is public information, so it
 * resolves last and emits a public {@link PlayerMovedEvent}.
 */
public class MoveAction implements GameAction {

    public static final int PRIORITY = 40;

    private final String actorId;
    private final String toLocationId;
    private final GamePhaseType phase;

    public MoveAction(String actorId, String toLocationId, GamePhaseType phase) {
        this.actorId = actorId;
        this.toLocationId = toLocationId;
        this.phase = phase;
    }

    @Override public String actorId() { return actorId; }
    @Override public GamePhaseType requiredPhase() { return phase; }
    @Override public int priority() { return PRIORITY; }

    @Override
    public boolean validate(GameContext ctx) {
        Player actor = ctx.players().get(actorId);
        if (actor == null || !actor.status().isAlive()) return false;
        if (ctx.city().location(toLocationId) == null) return false;
        return ctx.city().areAdjacent(actor.locationId(), toLocationId);
    }

    @Override
    public void execute(GameContext ctx, EventBus bus) {
        Player actor = ctx.players().get(actorId);
        String from = actor.locationId();

        Location fromLoc = ctx.city().location(from);
        if (fromLoc != null) fromLoc.removePlayer(actorId);

        Location toLoc = ctx.city().location(toLocationId);
        toLoc.addPlayer(actorId);
        actor.setLocationId(toLocationId);

        bus.publish(new PlayerMovedEvent(ctx.tick(), actorId, from, toLocationId));
    }
}
