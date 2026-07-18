package com.veil.api.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.ai.agent.AiEngine;
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
import com.veil.phases.GamePhaseType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Owns all live matches and bridges the engine to the confidential layer. When a game is
 * created it registers each player's secret role with the {@link ConfidentialGateway} and
 * keeps only the returned PUBLIC commitments — demonstrating that roles live in the
 * confidential layer, not in the wire protocol.
 */
@Service
public class GameService {

    // How long each phase runs before it auto-advances. The Shadows' kill window is 45s.
    private static final long NIGHT_MS = 45_000;
    private static final long DAY_MS = 60_000;
    private static final long VOTING_MS = 30_000;

    private final ConfidentialGateway gateway;
    private final LeaderboardListener leaderboard;
    private final AiEngine ai;
    private final ObjectMapper mapper;
    private final Map<String, GameSession> games = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> commitments = new ConcurrentHashMap<>();
    // Per-game countdown that auto-advances the phase, giving the match a live clock.
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public GameService(ConfidentialGateway gateway, LeaderboardListener leaderboard,
                       AiEngine ai, ObjectMapper mapper) {
        this.gateway = gateway;
        this.leaderboard = leaderboard;
        this.ai = ai;
        this.mapper = mapper;
    }

    /**
     * Create a ready-to-play 8-operative match: 2 Shadows (the mafia) vs the City
     * (1 Oracle, 1 Aegis, 4 Citizens), across a six-district neon city. Returns its id.
     */
    public GameSession createDemoGame() {
        String id = "g-" + UUID.randomUUID().toString().substring(0, 8);

        City city = new City();
        city.addLocation(new Location("plaza", "Neon Plaza", new Position(0, 0)));
        city.addLocation(new Location("market", "Data Market", new Position(3, 2)));
        city.addLocation(new Location("docks", "Rust Docks", new Position(6, 1)));
        city.addLocation(new Location("tower", "Spire Tower", new Position(6, 5)));
        city.addLocation(new Location("alley", "Glitch Alley", new Position(1, 4)));
        city.addLocation(new Location("garden", "Hydro Garden", new Position(3, 6)));
        // A few rooms per district — the commons is open; side rooms are places to hide.
        addRooms(city, "plaza", "fountain", "Fountain", "arcade", "Arcade");
        addRooms(city, "market", "backroom", "Back Room", "server-cage", "Server Cage");
        addRooms(city, "docks", "warehouse", "Warehouse", "hold", "Cargo Hold");
        addRooms(city, "tower", "penthouse", "Penthouse", "maintenance", "Maintenance");
        addRooms(city, "alley", "hideout", "Hideout", "den", "Neon Den");
        addRooms(city, "garden", "greenhouse", "Greenhouse", "pump", "Pump Room");
        city.connect("plaza", "market");
        city.connect("plaza", "alley");
        city.connect("market", "docks");
        city.connect("market", "garden");
        city.connect("docks", "tower");
        city.connect("alley", "garden");
        city.connect("garden", "tower");

        GameContext ctx = new GameContext(city, System.nanoTime());
        // 2 Shadows (mafia) + Oracle + Aegis + 4 Citizens = 8 operatives.
        ctx.addPlayer(new Player("p1", "Vex", new ShadowRole(), "plaza"));
        ctx.addPlayer(new Player("p2", "Nyx", new ShadowRole(), "docks"));
        ctx.addPlayer(new Player("p3", "Mara", new OracleRole(), "market"));
        ctx.addPlayer(new Player("p4", "Dax", new AegisRole(), "tower"));
        ctx.addPlayer(new Player("p5", "Ilya", new CitizenRole(), "plaza"));
        ctx.addPlayer(new Player("p6", "Juno", new CitizenRole(), "alley"));
        ctx.addPlayer(new Player("p7", "Rook", new CitizenRole(), "garden"));
        ctx.addPlayer(new Player("p8", "Echo", new CitizenRole(), "market"));
        ctx.addNpc(new NPC("n1", "Old Kesh", Personality.neutral(), "plaza"));
        NPC wire = new NPC("n2", "Wire", Personality.neutral(), "docks");
        wire.setRoomId("warehouse"); // an NPC hiding off the commons — only found by entering
        ctx.addNpc(wire);

        // Spread starting positions so co-located operatives don't stack on one spot.
        int idx = 0;
        for (Player p : ctx.players().values()) {
            p.setPosition(0.2 + (idx % 4) * 0.2, 0.35 + (idx / 4) * 0.3);
            idx++;
        }

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

    /** Claim a seat for a human; every other seat is then played by the AI. */
    public void claimSeat(String gameId, String playerId) {
        GameSession session = games.get(gameId);
        if (session != null) session.claimSeat(playerId);
    }

    private static void addRooms(City city, String districtId, String... roomIdNamePairs) {
        Location loc = city.location(districtId);
        if (loc == null) return;
        for (int i = 0; i + 1 < roomIdNamePairs.length; i += 2) {
            loc.addRoom(roomIdNamePairs[i], roomIdNamePairs[i + 1]);
        }
    }

    /** Start the match, then let the AI take its turn for the opening (Night) phase. */
    public GameSession startGame(String gameId) {
        GameSession session = games.get(gameId);
        if (session == null) return null;
        session.start();
        ai.runPhase(session);
        afterPhase(session);
        return session;
    }

    /** Advance the phase, then let the AI act for the new phase (chat, votes, night ops). */
    public GameSession advanceGame(String gameId) {
        GameSession session = games.get(gameId);
        if (session == null) return null;
        cancelTimer(gameId);
        session.advance();
        ai.runPhase(session);
        afterPhase(session);
        return session;
    }

    /**
     * After a phase begins: ask the confidential layer whether the match is decided (it
     * ends and scores if so), otherwise arm the countdown that will auto-advance to the
     * next phase — turning the match into a self-running, real-time loop.
     */
    private void afterPhase(GameSession session) {
        Faction winner = resolveWinner(session.id()); // ends + scores the match if decided
        if (winner == Faction.CITY || winner == Faction.SHADOW) {
            cancelTimer(session.id());
            session.setPhaseDeadline(0);
            return;
        }
        long dur = durationMs(session.phaseType());
        if (dur <= 0) {
            session.setPhaseDeadline(0);
            return;
        }
        session.setPhaseDeadline(System.currentTimeMillis() + dur);
        ScheduledFuture<?> future =
                scheduler.schedule(() -> advanceGame(session.id()), dur, TimeUnit.MILLISECONDS);
        timers.put(session.id(), future);
    }

    private long durationMs(GamePhaseType phase) {
        return switch (phase) {
            case NIGHT -> NIGHT_MS;
            case DAY -> DAY_MS;
            case VOTING -> VOTING_MS;
            default -> 0;
        };
    }

    private void cancelTimer(String gameId) {
        ScheduledFuture<?> existing = timers.remove(gameId);
        if (existing != null) existing.cancel(false);
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
