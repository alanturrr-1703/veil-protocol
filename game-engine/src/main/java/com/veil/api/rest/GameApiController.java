package com.veil.api.rest;

import com.veil.api.DTOs.PlayerView;
import com.veil.api.session.GameService;
import com.veil.api.session.GameSession;
import com.veil.domain.player.Faction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lobby / lifecycle REST API. Thin: it only drives the {@link GameSession} and returns
 * public-safe data. Confidential results (like an investigation) come straight from the
 * {@link GameService}'s confidential gateway, already reduced to a faction.
 */
@RestController
@RequestMapping("/api/games")
public class GameApiController {

    private final GameService games;

    public GameApiController(GameService games) {
        this.games = games;
    }

    /** Create a ready-to-play demo match. Returns id, phase, and PUBLIC role commitments. */
    @PostMapping
    public Map<String, Object> create() {
        GameSession game = games.createDemoGame();
        return Map.of(
                "gameId", game.id(),
                "phase", game.currentPhase(),
                "commitments", games.commitmentsOf(game.id())
        );
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, String>> start(@PathVariable String id) {
        GameSession game = games.get(id);
        if (game == null) return ResponseEntity.notFound().build();
        game.start();
        return ResponseEntity.ok(Map.of("phase", game.currentPhase()));
    }

    @PostMapping("/{id}/advance")
    public ResponseEntity<Map<String, String>> advance(@PathVariable String id) {
        GameSession game = games.get(id);
        if (game == null) return ResponseEntity.notFound().build();
        game.advance();
        return ResponseEntity.ok(Map.of("phase", game.currentPhase()));
    }

    /** The redacted, per-viewer snapshot — exactly what that player is authorized to see. */
    @GetMapping("/{id}/view/{playerId}")
    public ResponseEntity<PlayerView> view(@PathVariable String id, @PathVariable String playerId) {
        GameSession game = games.get(id);
        if (game == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(game.viewFor(playerId));
    }

    /** Public role commitments (hashes) — safe to expose; the roles stay in the gateway. */
    @GetMapping("/{id}/commitments")
    public ResponseEntity<Map<String, String>> commitments(@PathVariable String id) {
        if (games.get(id) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(games.commitmentsOf(id));
    }

    /** Confidential investigation through the gateway; only a faction is returned. */
    @PostMapping("/{id}/investigate")
    public ResponseEntity<Map<String, String>> investigate(
            @PathVariable String id,
            @RequestParam String oracle,
            @RequestParam String target) {
        if (games.get(id) == null) return ResponseEntity.notFound().build();
        Faction faction = games.investigate(id, oracle, target);
        return ResponseEntity.ok(Map.of("target", target, "faction", faction.name()));
    }
}
