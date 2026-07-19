package com.veil.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.ai.agent.AiEngine;
import com.veil.api.session.GameService;
import com.veil.api.session.GameSession;
import com.veil.chat.ChatChannel;
import com.veil.domain.action.AttackAction;
import com.veil.domain.action.InvestigateAction;
import com.veil.domain.action.QueryNPCAction;
import com.veil.domain.action.ShieldAction;
import com.veil.domain.action.VoteAction;
import com.veil.phases.GamePhaseType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns client intents into engine commands. It never contains game rules — it parses the
 * intent, builds the matching Command, and hands it to the {@link GameSession}, which the
 * engine validates against phase + role. Redacted views are pushed back by the session.
 *
 * Connect with: ws://localhost:8080/ws/game?gameId=...&playerId=...
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameService games;
    private final ObjectMapper mapper;
    private final AiEngine ai;

    public GameWebSocketHandler(GameService games, ObjectMapper mapper, AiEngine ai) {
        this.games = games;
        this.mapper = mapper;
        this.ai = ai;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> q = parseQuery(session.getUri());
        String gameId = q.get("gameId");
        String playerId = q.get("playerId");
        GameSession game = gameId == null ? null : games.get(gameId);

        if (game == null || playerId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put("gameId", gameId);
        session.getAttributes().put("playerId", playerId);
        game.addViewer(playerId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String gameId = (String) session.getAttributes().get("gameId");
        String playerId = (String) session.getAttributes().get("playerId");
        GameSession game = games.get(gameId);
        if (game == null) return;

        IntentMessage intent = mapper.readValue(message.getPayload(), IntentMessage.class);
        GamePhaseType phase = game.phaseType();

        switch (intent.type() == null ? "" : intent.type().toUpperCase()) {
            case "MOVE" -> game.move(playerId, intent.toLocationId());
            case "ENTER_ROOM" -> game.enterRoom(playerId, intent.roomId());
            case "TELEPORT" -> game.teleport(playerId, intent.toLocationId());
            case "ATTACK" -> game.submit(new AttackAction(playerId, intent.targetId()));
            case "SHIELD" -> game.submit(new ShieldAction(playerId, intent.targetId()));
            case "INVESTIGATE" -> game.submit(new InvestigateAction(playerId, intent.targetId()));
            case "QUERY_NPC" -> {
                game.submit(new QueryNPCAction(playerId, intent.npcId(), intent.topic(), phase));
                // NPCs are the only AI: reply is phrased by Ollama from what the NPC witnessed.
                ai.npcReply(game, playerId, intent.npcId(), intent.topic());
            }
            case "VOTE" -> game.submit(new VoteAction(playerId, intent.targetId()));
            case "CHAT" -> {
                ChatChannel channel = parseChannel(intent.channel());
                game.postChat(playerId, channel, intent.text());
            }
            case "WHISPER" -> game.postWhisper(playerId, intent.targetId(), intent.text());
            case "POS" -> {
                if (intent.x() != null && intent.y() != null) {
                    game.updatePosition(playerId, intent.x(), intent.y());
                }
            }
            default -> { /* unknown intent ignored */ }
        }
    }

    private static ChatChannel parseChannel(String raw) {
        if (raw == null) return ChatChannel.DAY;
        try {
            return ChatChannel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatChannel.DAY;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String gameId = (String) session.getAttributes().get("gameId");
        String playerId = (String) session.getAttributes().get("playerId");
        if (gameId == null || playerId == null) return;
        GameSession game = games.get(gameId);
        if (game != null) game.removeViewer(playerId);
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> out = new HashMap<>();
        if (uri == null || uri.getQuery() == null) return out;
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) out.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return out;
    }
}
