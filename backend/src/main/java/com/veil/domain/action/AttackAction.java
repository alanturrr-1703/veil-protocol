package com.veil.domain.action;

import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Observation;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.domain.roles.ShadowRole;
import com.veil.domain.world.Location;
import com.veil.engine.GameContext;
import com.veil.events.AttackEvent;
import com.veil.events.EventBus;
import com.veil.events.NPCDeathEvent;
import com.veil.phases.GamePhaseType;

import java.util.ArrayList;
import java.util.List;

/**
 * Shadow eliminates a target. A target player survives if shielded. A target NPC
 * can only be hunted if the Shadow is within hunt radius and still has attempts left.
 * Surviving NPCs in the location witness the attack (append to their memory).
 */
public class AttackAction implements GameAction {

    public static final int PRIORITY = 20;

    private final String actorId;
    private final String targetId;

    public AttackAction(String actorId, String targetId) {
        this.actorId = actorId;
        this.targetId = targetId;
    }

    @Override public String actorId() { return actorId; }
    @Override public GamePhaseType requiredPhase() { return GamePhaseType.NIGHT; }
    @Override public int priority() { return PRIORITY; }

    public String targetId() { return targetId; }

    /** True when this strike targets another player (vs. an NPC witness). */
    public boolean targetsPlayer(GameContext ctx) {
        return ctx.players().containsKey(targetId);
    }

    @Override
    public boolean validate(GameContext ctx) {
        Player attacker = ctx.players().get(actorId);
        if (attacker == null || !attacker.status().isAlive()) return false;
        if (attacker.role().faction() != Faction.SHADOW) return false;

        Player targetPlayer = ctx.players().get(targetId);
        if (targetPlayer != null) {
            // Re-checked at dawn: the victim must STILL share the attacker's room. If they
            // dodged into another room (or fled the district) after the strike was filed,
            // they slip away — this is what lets a hunted player save themselves.
            return targetPlayer.status().isAlive() && sameRoom(attacker, targetPlayer);
        }
        return ctx.npcs().containsKey(targetId); // NPC hunt proximity is checked in execute()
    }

    private boolean sameRoom(Player a, Player b) {
        return a.locationId() != null
                && a.locationId().equals(b.locationId())
                && a.roomId().equals(b.roomId());
    }

    @Override
    public void execute(GameContext ctx, EventBus bus) {
        Player attacker = ctx.players().get(actorId);

        // Target is another player.
        Player targetPlayer = ctx.players().get(targetId);
        if (targetPlayer != null) {
            // Confidential referee gate: the kill only lands if the referee confirms a valid
            // Shadow authorized it (the attacker's role never leaves the gateway). Falls back
            // to the local role check when running without a referee (Demo/tests).
            if (ctx.confidential() != null && !ctx.confidential().authorizeAttack(actorId, targetId)) {
                bus.publish(new AttackEvent(ctx.tick(), actorId, targetId, true));
                return;
            }
            boolean blocked = targetPlayer.status().isProtectedThisNight();
            if (!blocked) {
                targetPlayer.status().kill();
                Location loc = ctx.city().location(targetPlayer.locationId());
                if (loc != null) loc.removePlayer(targetPlayer.id());
                witnessAttack(ctx, targetPlayer.locationId(), targetPlayer.id());
                // Public dawn reveal: the city learns WHO fell (never who struck).
                ctx.publicState().recordNightVictim(targetPlayer.id());
            }
            bus.publish(new AttackEvent(ctx.tick(), actorId, targetId, blocked));
            return;
        }

        // Target is an NPC witness — requires proximity and a remaining attempt.
        NPC targetNpc = ctx.npcs().get(targetId);
        if (targetNpc == null || !targetNpc.isAlive()) return;

        ShadowRole shadow = (ShadowRole) attacker.role();
        boolean hasAttempt = ctx.privateState().tryConsumeHuntAttempt(actorId, shadow.maxHuntAttempts());
        if (!hasAttempt) return;

        boolean inRange = withinHuntRadius(ctx, attacker, targetNpc, shadow.huntRadius());
        if (!inRange) return;

        if (targetNpc.isProtected()) {
            bus.publish(new AttackEvent(ctx.tick(), actorId, targetId, true));
            return;
        }

        targetNpc.eliminate();
        Location loc = ctx.city().location(targetNpc.locationId());
        if (loc != null) loc.removeNpc(targetNpc.id());
        bus.publish(new NPCDeathEvent(ctx.tick(), actorId, targetId, targetNpc.locationId()));
    }

    private boolean withinHuntRadius(GameContext ctx, Player attacker, NPC npc, double radius) {
        Location from = ctx.city().location(attacker.locationId());
        Location to = ctx.city().location(npc.locationId());
        if (from == null || to == null) return false;
        return from.position().distanceTo(to.position()) <= radius;
    }

    /** Max number of bystanders an NPC will name as possible culprits. */
    private static final int MAX_SUSPECTS = 3;

    private void witnessAttack(GameContext ctx, String locationId, String victimId) {
        Location loc = ctx.city().location(locationId);
        if (loc == null) return;

        // The NPC only saw who was AROUND — never who struck the blow. The victim has
        // already been removed from the location, so this is the set of bystanders. The
        // real attacker may or may not be among them; either way it stays a fuzzy guess.
        List<String> suspects = new ArrayList<>();
        for (String pid : loc.playerIds()) {
            if (pid.equals(victimId)) continue;
            suspects.add(pid);
            if (suspects.size() >= MAX_SUSPECTS) break;
        }

        for (String npcId : loc.npcIds()) {
            NPC npc = ctx.npcs().get(npcId);
            if (npc != null && npc.isAlive()) {
                npc.witness(new Observation(victimId, "was attacked", locationId, ctx.tick(), 0.9, suspects));
            }
        }
    }
}
