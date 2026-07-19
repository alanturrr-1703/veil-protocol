package com.veil.api.rest;

import com.veil.api.DTOs.PlayerView;
import com.veil.api.session.GameService;
import com.veil.api.session.GameSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Lobby / matchmaking REST API. Thin: it mints a server-side player id, drives the
 * {@link GameService} room lifecycle, and returns only public-safe data. All real-time play
 * (movement, night actions, votes, chat) happens over the WebSocket once a client connects
 * with its {@code code} + {@code playerId}.
 *
 * <ul>
 *   <li>POST /api/rooms                 — create a room; returns its code + your player id</li>
 *   <li>POST /api/rooms/{code}/join     — join a room by code; returns your player id</li>
 *   <li>POST /api/rooms/{code}/start    — host starts the match; returns phase + commitments</li>
 *   <li>GET  /api/rooms/{code}/view/{playerId} — your redacted PlayerView (also pushed over WS)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final GameService rooms;

    public RoomController(GameService rooms) {
        this.rooms = rooms;
    }

    public record NameRequest(String name) {}

    /** Create a room. The caller becomes the host. Returns the code and the host's player id. */
    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) NameRequest req) {
        String playerId = newPlayerId();
        GameSession room = rooms.createRoom(playerId, displayName(req, "Host"));
        return Map.of(
                "code", room.id(),
                "playerId", playerId,
                "isHost", true,
                "phase", room.currentPhase()
        );
    }

    /** Join an existing room by code while it is still in the lobby. */
    @PostMapping("/{code}/join")
    public ResponseEntity<Map<String, Object>> join(@PathVariable String code,
                                                     @RequestBody(required = false) NameRequest req) {
        String playerId = newPlayerId();
        GameSession room = rooms.joinRoom(code, playerId, displayName(req, "Operative"));
        if (room == null) {
            return ResponseEntity.status(409).body(Map.of("error", "room not found or already started"));
        }
        return ResponseEntity.ok(Map.of(
                "code", room.id(),
                "playerId", playerId,
                "isHost", false,
                "phase", room.currentPhase()
        ));
    }

    /** Host starts the match: roles are dealt + committed, and the first Night opens. */
    @PostMapping("/{code}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String code) {
        GameSession room = rooms.startRoom(code);
        if (room == null) {
            return ResponseEntity.status(409).body(Map.of("error", "cannot start (unknown room or too few players)"));
        }
        return ResponseEntity.ok(Map.of(
                "phase", room.currentPhase(),
                "commitments", rooms.commitmentsOf(code)
        ));
    }

    /** The redacted, per-viewer snapshot — exactly what that player is authorized to see. */
    @GetMapping("/{code}/view/{playerId}")
    public ResponseEntity<PlayerView> view(@PathVariable String code, @PathVariable String playerId) {
        GameSession room = rooms.get(code);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(room.viewFor(playerId));
    }

    /** Public role commitments (hashes) — safe to expose; the roles stay in the referee. */
    @GetMapping("/{code}/commitments")
    public ResponseEntity<Map<String, String>> commitments(@PathVariable String code) {
        if (rooms.get(code) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(rooms.commitmentsOf(code));
    }

    private static String newPlayerId() {
        return "p-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String displayName(NameRequest req, String fallback) {
        if (req == null || req.name() == null || req.name().isBlank()) return fallback;
        return req.name().trim();
    }
}
