package com.veil.domain.action;

import com.veil.domain.player.Player;
import com.veil.domain.world.Location;
import com.veil.engine.GameContext;
import com.veil.events.EventBus;
import com.veil.phases.GamePhaseType;

/**
 * Slip into (or out of) a side room within your current district. Rooms are how players and
 * NPCs hide: you only ever see occupants who share your exact room, so ducking into a
 * back room removes you from view of anyone in the commons — and vice versa.
 *
 * <p>Movement between rooms is immediate (not batched) and does not change your district.
 */
public class EnterRoomAction implements GameAction {

    public static final int PRIORITY = 41;

    private final String actorId;
    private final String roomId;
    private final GamePhaseType phase;

    public EnterRoomAction(String actorId, String roomId, GamePhaseType phase) {
        this.actorId = actorId;
        this.roomId = roomId;
        this.phase = phase;
    }

    @Override public String actorId() { return actorId; }
    @Override public GamePhaseType requiredPhase() { return phase; }
    @Override public int priority() { return PRIORITY; }

    @Override
    public boolean validate(GameContext ctx) {
        Player actor = ctx.players().get(actorId);
        if (actor == null || !actor.status().isAlive()) return false;
        Location here = ctx.city().location(actor.locationId());
        return here != null && here.hasRoom(roomId);
    }

    @Override
    public void execute(GameContext ctx, EventBus bus) {
        Player actor = ctx.players().get(actorId);
        actor.setRoomId(roomId);
    }
}
