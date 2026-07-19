package com.veil.engine;

import com.veil.chat.ChatChannel;
import com.veil.chat.ChatMessage;
import com.veil.chat.ChatPolicy;
import com.veil.domain.action.GameAction;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.domain.player.Role;
import com.veil.events.EventBus;
import com.veil.events.GameEndedEvent;
import com.veil.events.GameEventListener;
import com.veil.phases.GameOverPhase;
import com.veil.phases.GamePhase;
import com.veil.phases.GamePhaseType;
import com.veil.phases.LobbyPhase;
import com.veil.phases.NightPhase;
import com.veil.phases.VotingPhase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrator and single writer for one match. All commands funnel through here,
 * which enforces phase + role legality before delegating to the current phase state.
 * Keeping one authoritative engine per match makes resolution deterministic and
 * lock-free.
 */
public class VeilEngine {

    private final GameContext context;
    private final EventBus eventBus = new EventBus();
    private final GameResolver resolver = new GameResolver();

    private GamePhase currentPhase;

    public VeilEngine(GameContext context) {
        this.context = context;
    }

    public GameContext context() { return context; }
    public EventBus eventBus() { return eventBus; }
    public GamePhase currentPhase() { return currentPhase; }

    public void addListener(GameEventListener listener) {
        eventBus.register(listener);
    }

    /** Begin the match at Night (used by the scripted Demo harness). */
    public void start() {
        currentPhase = new NightPhase(resolver);
        currentPhase.onEnter(context, eventBus);
    }

    /** Open the pre-match lobby (used by room-based multiplayer before roles are dealt). */
    public void openLobby() {
        currentPhase = new LobbyPhase();
        currentPhase.onEnter(context, eventBus);
    }

    /**
     * Begin a lobby match: resolve the lobby into the first Night. Roles must already have
     * been dealt and committed to the confidential referee by the room service.
     */
    public void beginMatch() {
        if (currentPhase instanceof LobbyPhase) {
            advancePhase();
        }
    }

    /**
     * Submit a player command. Rejected unless it is legal for the current phase and
     * the player's role permits it.
     */
    public boolean submit(GameAction action) {
        if (currentPhase == null) return false;
        if (action.requiredPhase() != currentPhase.type()) return false;

        Player player = context.players().get(action.actorId());
        if (player == null || !player.status().isAlive()) return false;
        if (!player.role().canPerform(action, currentPhase.type())) return false;

        // A Shadow can only strike someone in their OWN room. We check co-location at the
        // moment the kill is FILED (not at dawn resolution, when everyone has wandered off)
        // and record a live strike so co-located players witness it — and see who struck.
        if (action instanceof com.veil.domain.action.AttackAction atk && atk.targetsPlayer(context)) {
            Player victim = context.players().get(atk.targetId());
            if (victim == null || !victim.status().isAlive() || !sameRoom(player, victim)) return false;
            context.privateState().recordAttackFx(new com.veil.events.AttackFx(
                    player.id(), victim.id(), player.locationId(), player.roomId(),
                    context.rng().nextInt(3), context.tick()));
        }

        currentPhase.submit(action, context, eventBus);
        return true;
    }

    /**
     * The teleport half of the one-per-night relocation: an instant jump to ANY district (not
     * just an adjacent one), consuming the allowance. Lands you in the open commons. This is
     * the "lots of time left — vanish so the Shadow can't find me" escape. Allowed in any
     * phase; the allowance refreshes at nightfall.
     */
    public boolean teleport(String playerId, String toLocationId) {
        Player p = context.players().get(playerId);
        if (p == null || !p.status().isAlive() || !p.relocateAvailable()) return false;
        if (toLocationId == null || toLocationId.equals(p.locationId())) return false;
        com.veil.domain.world.Location to = context.city().location(toLocationId);
        if (to == null) return false;

        com.veil.domain.world.Location from = context.city().location(p.locationId());
        String fromId = p.locationId();
        if (from != null) from.removePlayer(playerId);
        to.addPlayer(playerId);
        p.setLocationId(toLocationId);
        p.setRoomId(Player.COMMONS);
        p.setPosition(0.5, 0.6);
        p.setRelocateAvailable(false);
        eventBus.publish(new com.veil.events.PlayerMovedEvent(context.tick(), playerId, fromId, toLocationId));
        return true;
    }

    /**
     * Free-roam district movement (walk to an ADJACENT district). During the NIGHT this is
     * the same one-per-night relocation as teleport — you may leave your district only once,
     * so a hunted player must choose between fleeing early and dodging between rooms late.
     * During the day (and lobby) it's unrestricted so the city can regroup and de-camp.
     */
    public boolean moveTo(String playerId, String toLocationId) {
        Player p = context.players().get(playerId);
        if (p == null || !p.status().isAlive()) return false;
        boolean night = currentPhaseType() == GamePhaseType.NIGHT;
        if (night && !p.relocateAvailable()) return false;

        com.veil.domain.action.MoveAction a =
                new com.veil.domain.action.MoveAction(playerId, toLocationId, currentPhaseType());
        if (!a.validate(context)) return false;
        a.execute(context, eventBus);
        if (night) p.setRelocateAvailable(false);
        return true;
    }

    /** Free-roam room change (any phase, including the lobby). Validated but not phase-gated. */
    public boolean enterRoom(String playerId, String roomId) {
        com.veil.domain.action.EnterRoomAction a =
                new com.veil.domain.action.EnterRoomAction(playerId, roomId, currentPhaseType());
        if (!a.validate(context)) return false;
        a.execute(context, eventBus);
        return true;
    }

    /** Cast a vote (only meaningful during the Voting phase). */
    public void castVote(String voterId, String targetId) {
        if (currentPhase instanceof VotingPhase voting) {
            voting.castVote(voterId, targetId, context);
        }
    }

    /** Resolve the current phase, transition to the next, then check for a winner. */
    public void advancePhase() {
        currentPhase = currentPhase.resolve(context, eventBus);
        currentPhase.onEnter(context, eventBus);
        maybeEndMatch();
    }

    /**
     * Ask the confidential referee whether a faction has won, using ONLY the public alive-set.
     * If decided, publish the terminal {@link GameEndedEvent} (which the leaderboard scores)
     * and switch to the {@link GameOverPhase}. The engine cannot compute the winner itself
     * without leaking roles, so the referee is the trust anchor. No-ops without a referee
     * (e.g. the framework-free Demo) or once the match is already over.
     */
    private void maybeEndMatch() {
        if (context.confidential() == null) return;
        if (currentPhase.type() == GamePhaseType.GAME_OVER) return;

        Faction winner = context.confidential().resolveWinner(alivePlayerIds());
        if (winner == Faction.CITY || winner == Faction.SHADOW) {
            publishGameEnded(winner);
            currentPhase = new GameOverPhase(winner);
            currentPhase.onEnter(context, eventBus);
        }
    }

    private Set<String> alivePlayerIds() {
        Set<String> alive = new LinkedHashSet<>();
        for (Player p : context.players().values()) {
            if (p.status().isAlive()) alive.add(p.id());
        }
        return alive;
    }

    // --- Chat -----------------------------------------------------------------
    // Chat is an IMMEDIATE path (not a Command / night action): it is not resolved in
    // a batch, and DAY spans two phases. Legality is enforced here server-side via
    // ChatPolicy — the same class DtoAssembler uses to filter reads — so send-rules and
    // see-rules cannot drift. Membership (living Shadow, dead) is derived from live
    // state on every call, so a Shadow who dies immediately loses SHADOW posting.

    private final GamePhaseType currentPhaseType() {
        return currentPhase == null ? GamePhaseType.LOBBY : currentPhase.type();
    }

    /**
     * Post a chat line from a player. Returns false (and stores nothing) if the post is
     * illegal for the sender's role, alive status, and the current phase.
     */
    public boolean postChat(String senderId, ChatChannel channel, String text) {
        Player sender = context.players().get(senderId);
        if (sender == null) return false;

        Role role = sender.role().role();
        boolean alive = sender.status().isAlive();
        if (!ChatPolicy.canPost(role, alive, currentPhaseType(), channel)) return false;

        context.privateState().postChatMessage(new ChatMessage(
                senderId,
                sender.displayName(),
                channel,
                currentPhaseType(),
                context.tick(),
                context.privateState().nextChatSeq(),
                text,
                null
        ));
        return true;
    }

    /**
     * Post a private whisper from one player to another. Allowed ONLY when both are alive
     * and standing in the SAME room (proximity) — you can only speak privately to someone
     * right next to you. Stored on the confidential DIRECT channel; the redaction boundary
     * reveals it exclusively to the sender and recipient.
     */
    public boolean postWhisper(String senderId, String toId, String text) {
        if (toId == null || text == null || text.isBlank()) return false;
        Player sender = context.players().get(senderId);
        Player target = context.players().get(toId);
        if (sender == null || target == null) return false;
        if (!sender.status().isAlive() || !target.status().isAlive()) return false;
        if (!sameRoom(sender, target)) return false;

        context.privateState().postChatMessage(new ChatMessage(
                senderId,
                sender.displayName(),
                ChatChannel.DIRECT,
                currentPhaseType(),
                context.tick(),
                context.privateState().nextChatSeq(),
                text,
                toId
        ));
        return true;
    }

    private boolean sameRoom(Player a, Player b) {
        return a.locationId() != null
                && a.locationId().equals(b.locationId())
                && a.roomId().equals(b.roomId());
    }

    /**
     * Append a raw DIRECT line to the chat log with an explicit sender name (e.g. an NPC that
     * is not a Player). Used for NPC conversation, where co-location has already been checked
     * by the caller. The redaction boundary still reveals it only to the sender and recipient.
     */
    public void postDirectRaw(String senderId, String senderName, String toId, String text) {
        if (toId == null || text == null || text.isBlank()) return;
        context.privateState().postChatMessage(new ChatMessage(
                senderId,
                senderName,
                ChatChannel.DIRECT,
                currentPhaseType(),
                context.tick(),
                context.privateState().nextChatSeq(),
                text,
                toId
        ));
    }

    /**
     * Narrator / announcement line into the SYSTEM channel (readable by everyone).
     * Reuses the confidential chat log so the frontend renders it inline with Day chat.
     */
    public void postSystemChat(String text) {
        context.privateState().postChatMessage(new ChatMessage(
                ChatMessage.SYSTEM_SENDER,
                "Narrator",
                ChatChannel.SYSTEM,
                currentPhaseType(),
                context.tick(),
                context.privateState().nextChatSeq(),
                text,
                null
        ));
    }

    // --- Game end (leaderboard trigger) --------------------------------------

    /**
     * One-line helper to end the match: derives the final per-player snapshot from live
     * state and publishes a {@link GameEndedEvent}. The engine (which may read state)
     * builds the payload here; the leaderboard listener consumes only the event.
     */
    public void publishGameEnded(Faction winningFaction) {
        List<GameEndedEvent.PlayerResult> results = new ArrayList<>();
        for (Player p : context.players().values()) {
            Role role = p.role().role();
            results.add(new GameEndedEvent.PlayerResult(
                    p.id(), p.displayName(), role, role.faction(), p.status().isAlive()));
        }
        eventBus.publish(new GameEndedEvent(context.tick(), winningFaction, results));
    }
}
