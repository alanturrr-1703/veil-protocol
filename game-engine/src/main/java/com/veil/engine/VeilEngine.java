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
import com.veil.phases.GamePhase;
import com.veil.phases.GamePhaseType;
import com.veil.phases.NightPhase;
import com.veil.phases.VotingPhase;

import java.util.ArrayList;
import java.util.List;

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

    /** Begin the match at Night. */
    public void start() {
        currentPhase = new NightPhase(resolver);
        currentPhase.onEnter(context, eventBus);
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

        currentPhase.submit(action, context, eventBus);
        return true;
    }

    /**
     * Free-roam movement, decoupled from the turn engine so players (and AI) can wander the
     * map in ANY phase — including the lobby, before the match has started. Still validated
     * (must be alive; districts must be adjacent), just not gated by phase/role.
     */
    public boolean moveTo(String playerId, String toLocationId) {
        com.veil.domain.action.MoveAction a =
                new com.veil.domain.action.MoveAction(playerId, toLocationId, currentPhaseType());
        if (!a.validate(context)) return false;
        a.execute(context, eventBus);
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

    /** Resolve the current phase and transition to the next. */
    public void advancePhase() {
        currentPhase = currentPhase.resolve(context, eventBus);
        currentPhase.onEnter(context, eventBus);
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
