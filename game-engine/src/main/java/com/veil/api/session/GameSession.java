package com.veil.api.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.api.DTOs.DtoAssembler;
import com.veil.api.DTOs.PlayerView;
import com.veil.domain.action.GameAction;
import com.veil.engine.VeilEngine;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
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

    public GameSession(String id, VeilEngine engine, ObjectMapper mapper) {
        this.id = id;
        this.engine = engine;
        this.mapper = mapper;
        // Observer: any world change re-pushes redacted views to everyone connected.
        engine.addListener(event -> broadcast());
    }

    public String id() { return id; }

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

    public synchronized PlayerView viewFor(String playerId) {
        return DtoAssembler.forViewer(engine.context(), engine.currentPhase(), playerId);
    }

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
