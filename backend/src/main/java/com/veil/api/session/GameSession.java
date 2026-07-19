package com.veil.api.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.api.DTOs.DtoAssembler;
import com.veil.api.DTOs.PlayerView;
import com.veil.chat.ChatChannel;
import com.veil.domain.action.GameAction;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.engine.GameContext;
import com.veil.engine.VeilEngine;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One live match. Wraps a single authoritative {@link VeilEngine} and the set of connected
 * viewers. Every public method is {@code synchronized}, making this a single-writer per match
 * (deterministic, lock-free from the engine's point of view). On every engine event it pushes
 * a freshly redacted {@link PlayerView} to each viewer — so clients only ever receive their
 * own authorized slice.
 */
public class GameSession {

    private final String id;
    private final VeilEngine engine;
    private final ObjectMapper mapper;
    private final Map<String, WebSocketSession> viewers = new ConcurrentHashMap<>();
    // Every seated player. In multiplayer all operatives are humans who joined by room code;
    // this set is broadcast so the UI can label real participants. Public info.
    private final Set<String> humanIds = ConcurrentHashMap.newKeySet();
    // The host (room creator) — the only seat allowed to start the match.
    private volatile String hostId;
    // Epoch millis at which the current phase auto-advances (0 = untimed). Broadcast so
    // clients can show a live countdown (e.g. the Shadows' 45-second window).
    private volatile long phaseEndsAt = 0;

    public GameSession(String id, VeilEngine engine, ObjectMapper mapper) {
        this.id = id;
        this.engine = engine;
        this.mapper = mapper;
        // Observer: any world change re-pushes redacted views to everyone connected.
        engine.addListener(event -> broadcast());
    }

    public String id() { return id; }
    public String hostId() { return hostId; }
    public boolean isHost(String playerId) { return playerId != null && playerId.equals(hostId); }

    /** Open the pre-match lobby (called once, right after the room is created). */
    public synchronized void openLobby() {
        engine.openLobby();
        broadcast();
    }

    /**
     * Seat a human in the lobby. The first player seated becomes the host. Returns false if
     * the id is already taken. Adds the player to the authoritative context with a placeholder
     * role; the real role is dealt at {@link #beginMatch()}.
     */
    public synchronized boolean seatPlayer(Player player, boolean asHost) {
        if (engine.context().players().containsKey(player.id())) return false;
        engine.context().addPlayer(player);
        humanIds.add(player.id());
        if (asHost || hostId == null) hostId = player.id();
        broadcast();
        return true;
    }

    public synchronized int playerCount() { return engine.context().players().size(); }

    public Set<String> humanIds() { return humanIds; }
    public boolean isHuman(String playerId) { return humanIds.contains(playerId); }

    /**
     * Server-side read access. The redaction boundary ({@link #viewFor}) is the ONLY path to
     * a client; this raw context is for engine/room orchestration on the server only.
     */
    public GameContext context() { return engine.context(); }

    public synchronized void addViewer(String playerId, WebSocketSession session) {
        viewers.put(playerId, session);
        sendViewTo(playerId);
    }

    public synchronized void removeViewer(String playerId) {
        viewers.remove(playerId);
    }

    public synchronized void start() {
        engine.start();
        broadcast();
    }

    /** Deal is done by the room service; resolve the lobby into the first Night. */
    public synchronized void beginMatch() {
        engine.beginMatch();
        broadcast();
    }

    public synchronized void advance() {
        engine.advancePhase();
        broadcast();
    }

    public synchronized boolean submit(GameAction action) {
        boolean accepted = engine.submit(action);
        broadcast();
        return accepted;
    }

    public synchronized void vote(String voterId, String targetId) {
        engine.castVote(voterId, targetId);
    }

    /**
     * Post a chat line. Legality (role/alive/phase/channel) is enforced by the engine via
     * {@code ChatPolicy}; on success every viewer gets a freshly redacted feed, so a SHADOW
     * line never reaches a City viewer.
     */
    public synchronized boolean postChat(String senderId, ChatChannel channel, String text) {
        boolean ok = engine.postChat(senderId, channel, text);
        if (ok) broadcast();
        return ok;
    }

    /** Private whisper to a co-located player; visible only to the two of them. */
    public synchronized boolean postWhisper(String senderId, String toId, String text) {
        boolean ok = engine.postWhisper(senderId, toId, text);
        if (ok) broadcast();
        return ok;
    }

    /**
     * Append one line of NPC conversation (either the player's question or the NPC's reply)
     * as a private DIRECT line between {@code senderId} and {@code toId}, then re-push views.
     * Co-location has already been checked by the caller (AiEngine).
     */
    public synchronized void postNpcLine(String senderId, String senderName, String toId, String text) {
        engine.postDirectRaw(senderId, senderName, toId, text);
        broadcast();
    }

    /** Free-roam district move (allowed in any phase, including the lobby). */
    public synchronized boolean move(String playerId, String toLocationId) {
        boolean ok = engine.moveTo(playerId, toLocationId);
        if (ok) broadcast();
        return ok;
    }

    /** Free-roam room change (allowed in any phase, including the lobby). */
    public synchronized boolean enterRoom(String playerId, String roomId) {
        boolean ok = engine.enterRoom(playerId, roomId);
        if (ok) broadcast();
        return ok;
    }

    /** The one-per-night teleport: jump to any district (any phase). */
    public synchronized boolean teleport(String playerId, String toLocationId) {
        boolean ok = engine.teleport(playerId, toLocationId);
        if (ok) broadcast();
        return ok;
    }

    /** Update a player's free-roam position within their room and re-push co-located views. */
    public synchronized void updatePosition(String playerId, double x, double y) {
        Player p = engine.context().players().get(playerId);
        if (p == null || !p.status().isAlive()) return;
        p.setPosition(x, y);
        broadcast();
    }

    /** Public alive-set (roster is public state), used as input to the confidential winner check. */
    public synchronized Set<String> alivePlayerIds() {
        Set<String> alive = new LinkedHashSet<>();
        for (Player p : engine.context().players().values()) {
            if (p.status().isAlive()) alive.add(p.id());
        }
        return alive;
    }

    /**
     * End the match on a winner the confidential layer resolved: announce it on the SYSTEM
     * channel and publish the terminal event that the leaderboard observer consumes.
     */
    public synchronized void endMatch(Faction winner) {
        engine.postSystemChat((winner == Faction.SHADOW ? "The Shadows" : "The City")
                + " have won the match.");
        engine.publishGameEnded(winner);
        broadcast();
    }

    public synchronized PlayerView viewFor(String playerId) {
        return DtoAssembler.forViewer(engine.context(), engine.currentPhase(), playerId, humanIds, phaseEndsAt);
    }

    /** Set the countdown deadline for the current phase and push it to clients. */
    public synchronized void setPhaseDeadline(long epochMillis) {
        this.phaseEndsAt = epochMillis;
        broadcast();
    }

    public long phaseEndsAt() { return phaseEndsAt; }

    public synchronized String currentPhase() {
        return engine.currentPhase() == null ? "NONE" : engine.currentPhase().type().name();
    }

    public synchronized com.veil.phases.GamePhaseType phaseType() {
        return engine.currentPhase() == null ? com.veil.phases.GamePhaseType.LOBBY
                : engine.currentPhase().type();
    }

    private void broadcast() {
        for (String playerId : viewers.keySet()) {
            sendViewTo(playerId);
        }
    }

    private void sendViewTo(String playerId) {
        WebSocketSession session = viewers.get(playerId);
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(viewFor(playerId))));
        } catch (IOException e) {
            viewers.remove(playerId);
        }
    }
}
