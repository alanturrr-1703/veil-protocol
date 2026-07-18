package com.veil.api.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.api.DTOs.LeaderboardView;
import com.veil.confidential.ConfidentialGateway;
import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Personality;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.domain.roles.AegisRole;
import com.veil.domain.roles.CitizenRole;
import com.veil.domain.roles.OracleRole;
import com.veil.domain.roles.ShadowRole;
import com.veil.domain.world.City;
import com.veil.domain.world.Location;
import com.veil.domain.world.Position;
import com.veil.engine.GameContext;
import com.veil.engine.VeilEngine;
import com.veil.leaderboard.LeaderboardListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Owns all live matches and bridges the engine to the confidential layer. When a game is
 * created it registers each player's secret role with the {@link ConfidentialGateway} and
 * keeps only the returned PUBLIC commitments — demonstrating that roles live in the
 * confidential layer, not in the wire protocol.
 */
@Service
public class GameService {

    private final ConfidentialGateway gateway;
    private final LeaderboardListener leaderboard;
    private final ObjectMapper mapper;
    private final Map<String, GameSession> games = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> commitments = new ConcurrentHashMap<>();

    public GameService(ConfidentialGateway gateway, LeaderboardListener leaderboard, ObjectMapper mapper) {
        this.gateway = gateway;
        this.leaderboard = leaderboard;
        this.mapper = mapper;
    }

    /** Create a ready-to-play demo match (4 players + 1 NPC witness) and return its id. */
    public GameSession createDemoGame() {
        String id = "g-" + UUID.randomUUID().toString().substring(0, 8);

        City city = new City();
        city.addLocation(new Location("plaza", "Neon Plaza", new Position(0, 0)));
        city.addLocation(new Location("docks", "Rust Docks", new Position(5, 0)));
        city.connect("plaza", "docks");

        GameContext ctx = new GameContext(city, System.nanoTime());
        ctx.addPlayer(new Player("p1", "Vex", new ShadowRole(), "plaza"));
        ctx.addPlayer(new Player("p2", "Mara", new AegisRole(), "plaza"));
        ctx.addPlayer(new Player("p3", "Ilya", new OracleRole(), "plaza"));
        ctx.addPlayer(new Player("p4", "Dax", new CitizenRole(), "plaza"));
        ctx.addNpc(new NPC("n1", "Old Kesh", Personality.neutral(), "plaza"));

        // Confidential layer owns the roles: publish only commitments (namespaced per game).
        Map<String, String> gameCommitments = new LinkedHashMap<>();
        for (Player p : ctx.players().values()) {
            String commitment = gateway.commitRole(id + "/" + p.id(), p.role().role());
            gameCommitments.put(p.id(), commitment);
        }
        commitments.put(id, gameCommitments);

        VeilEngine engine = new VeilEngine(ctx);
        // Analytics Observer: the shared leaderboard accumulates standings across every
        // match from the terminal GameEndedEvent — it never reads roles or game state.
        engine.addListener(leaderboard);

        GameSession session = new GameSession(id, engine, mapper);
        games.put(id, session);
        return session;
    }

    public GameSession get(String gameId) {
        return games.get(gameId);
    }

    public Map<String, String> commitmentsOf(String gameId) {
        return commitments.getOrDefault(gameId, Map.of());
    }

    /** Confidential Oracle investigation via the gateway — returns only a faction. */
    public Faction investigate(String gameId, String oracleId, String targetId) {
        return gateway.investigate(gameId + "/" + oracleId, gameId + "/" + targetId);
    }

    /**
     * Ask the confidential layer who has won, from the PUBLIC alive-set only. The engine
     * can't compute this without leaking roles, so it delegates to the gateway (Midnight).
     * If a faction is decided, the match ends and the leaderboard scores it; otherwise the
     * result is {@link Faction#NEUTRAL} and nothing is scored.
     */
    public Faction resolveWinner(String gameId) {
        GameSession session = games.get(gameId);
        if (session == null) return Faction.NEUTRAL;

        Set<String> aliveNamespaced = session.alivePlayerIds().stream()
                .map(pid -> gameId + "/" + pid)
                .collect(Collectors.toSet());

        Faction winner = gateway.resolveWinner(aliveNamespaced);
        if (winner == Faction.CITY || winner == Faction.SHADOW) {
            session.endMatch(winner);
        }
        return winner;
    }

    /** The public, ranked cross-match leaderboard (shared across all games). */
    public LeaderboardView leaderboard() {
        return leaderboard.view();
    }
}
