package com.veil.phases;

import com.veil.domain.action.GameAction;
import com.veil.domain.npc.NPC;
import com.veil.domain.player.Player;
import com.veil.domain.player.Role;
import com.veil.domain.world.Location;
import com.veil.engine.GameContext;
import com.veil.engine.GameResolver;
import com.veil.events.EventBus;

import java.util.ArrayList;
import java.util.List;

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
        enforceNoCamping(ctx);
    }

    /**
     * A Citizen or Oracle may not spend two nights running in the same district — no
     * camping a safe corner. Anyone who failed to relocate during the day is moved to a
     * random adjacent district. Every survivor's district is then snapshotted so the same
     * check can run next night. (Shadows and the Aegis are free to hold position.)
     */
    private void enforceNoCamping(GameContext ctx) {
        for (Player p : ctx.players().values()) {
            if (!p.status().isAlive()) continue;
            Role role = p.role().role();
            boolean restricted = role == Role.CITIZEN || role == Role.ORACLE;
            if (restricted && p.locationId() != null && p.locationId().equals(p.lastNightDistrict())) {
                relocate(ctx, p);
            }
        }
        // Snapshot AFTER relocation so next night's comparison is against tonight's district.
        for (Player p : ctx.players().values()) {
            if (p.status().isAlive()) p.setLastNightDistrict(p.locationId());
        }
    }

    private void relocate(GameContext ctx, Player p) {
        List<String> options = new ArrayList<>(ctx.city().neighbors(p.locationId()));
        if (options.isEmpty()) return;
        String dest = options.get(ctx.rng().nextInt(options.size()));

        Location from = ctx.city().location(p.locationId());
        if (from != null) from.removePlayer(p.id());
        Location to = ctx.city().location(dest);
        if (to != null) to.addPlayer(p.id());
        p.setLocationId(dest);
        p.setRoomId(Player.COMMONS);
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
