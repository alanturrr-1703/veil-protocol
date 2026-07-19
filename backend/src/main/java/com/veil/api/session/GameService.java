package com.veil.api.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.api.DTOs.LeaderboardView;
import com.veil.confidential.ConfidentialGateway;
import com.veil.confidential.MatchConfidential;
import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Personality;
import com.veil.domain.player.Player;
import com.veil.domain.roles.CitizenRole;
import com.veil.domain.roles.RoleDealer;
import com.veil.domain.roles.RoleStrategy;
import com.veil.domain.world.City;
import com.veil.domain.world.Location;
import com.veil.domain.world.Position;
import com.veil.engine.GameContext;
import com.veil.engine.VeilEngine;
import com.veil.leaderboard.LeaderboardListener;
import com.veil.phases.GamePhaseType;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Owns every live room and is the {@link RoomRegistry}. A host creates a room (getting a
 * short code), other humans join by that code, and the host starts the match — at which
 * point roles are dealt and committed to the confidential referee (each role stays inside
 * the gateway; only commitments are kept). From there a per-room clock auto-advances the
 * phases so the match runs in real time. There are no AI operatives: every seat is a human.
 */
@Service
public class GameService implements RoomRegistry {

    // How long each phase runs before it auto-advances. The Shadows' kill window is 45s.
    private static final long NIGHT_MS = 45_000;
    private static final long DAY_MS = 60_000;
    private static final long VOTING_MS = 30_000;

    private final ConfidentialGateway gateway;
    private final LeaderboardListener leaderboard;
    private final ObjectMapper mapper;

    private final Map<String, GameSession> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final SecureRandom codeRng = new SecureRandom();

    public GameService(ConfidentialGateway gateway, LeaderboardListener leaderboard, ObjectMapper mapper) {
        this.gateway = gateway;
        this.leaderboard = leaderboard;
        this.mapper = mapper;
    }

    // --- RoomRegistry ---------------------------------------------------------

    @Override
    public GameSession createRoom(String hostPlayerId, String hostDisplayName) {
        String code = uniqueRoomCode();

        City city = buildCity();
        // The confidential referee for this room. Roles will be committed into it at start.
        MatchConfidential confidential = new MatchConfidential(gateway, code);
        GameContext ctx = new GameContext(city, System.nanoTime(), confidential);
        seedNpcs(ctx);

        VeilEngine engine = new VeilEngine(ctx);
        // Analytics Observer: standings accumulate across every room from the terminal
        // GameEndedEvent — it never reads roles or private state.
        engine.addListener(leaderboard);

        GameSession session = new GameSession(code, engine, mapper);
        rooms.put(code, session);
        session.openLobby();

        seatHuman(session, hostPlayerId, hostDisplayName, true);
        return session;
    }

    @Override
    public GameSession joinRoom(String roomCode, String playerId, String displayName) {
        GameSession session = rooms.get(roomCode);
        if (session == null) return null;
        if (session.phaseType() != GamePhaseType.LOBBY) return null; // no join mid-match
        seatHuman(session, playerId, displayName, false);
        return session;
    }

    @Override
    public GameSession startRoom(String roomCode) {
        GameSession session = rooms.get(roomCode);
        if (session == null) return null;
        if (session.phaseType() != GamePhaseType.LOBBY) return null;

        GameContext ctx = session.context();
        List<Player> players = new ArrayList<>(ctx.players().values());
        if (players.size() < RoleDealer.MIN_PLAYERS) return null;

        // Deal balanced roles and commit each secretly to the confidential referee. The role
        // is set locally too (the server is the real-time authority) but only its commitment
        // is ever exposed; the referee is the trust anchor for attacks/investigations/wins.
        List<RoleStrategy> dealt = RoleDealer.deal(players.size(), ctx.rng());
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            RoleStrategy role = dealt.get(i);
            p.setRole(role);
            ctx.confidential().commit(p.id(), role.role());
        }

        session.beginMatch();   // Lobby -> Night (+ immediate win check inside the engine)
        armTimer(session);
        return session;
    }

    @Override
    public GameSession byCode(String roomCode) {
        return rooms.get(roomCode);
    }

    /** Alias used by the WebSocket handler, which keys sessions by room code. */
    public GameSession get(String roomCode) {
        return rooms.get(roomCode);
    }

    /** Public role commitments (hashes) for a room — safe to expose; roles stay in the gateway. */
    public Map<String, String> commitmentsOf(String roomCode) {
        GameSession session = rooms.get(roomCode);
        if (session == null || session.context().confidential() == null) return Map.of();
        return session.context().confidential().commitments();
    }

    /** The public, ranked cross-room leaderboard (shared across all matches). */
    public LeaderboardView leaderboard() {
        return leaderboard.view();
    }

    // --- Seating & role placeholder ------------------------------------------

    private void seatHuman(GameSession session, String playerId, String displayName, boolean asHost) {
        GameContext ctx = session.context();
        String district = pickStartDistrict(ctx);
        // Placeholder role until the deal at start; roles are never revealed in the lobby.
        Player player = new Player(playerId, displayName, new CitizenRole(), district);
        double idx = ctx.players().size();
        player.setPosition(0.2 + (idx % 4) * 0.2, 0.35 + Math.floor(idx / 4) * 0.3);
        session.seatPlayer(player, asHost);
    }

    /** Spread joiners across districts so the lobby isn't a single crowded room. */
    private String pickStartDistrict(GameContext ctx) {
        List<String> districts = new ArrayList<>(ctx.city().locations().keySet());
        if (districts.isEmpty()) return null;
        return districts.get(ctx.players().size() % districts.size());
    }

    // --- Real-time phase clock (no AI; humans act over the WebSocket) ---------

    private void armTimer(GameSession session) {
        cancelTimer(session.id());
        long dur = durationMs(session.phaseType());
        if (dur <= 0) {                       // LOBBY / GAME_OVER: untimed
            session.setPhaseDeadline(0);
            return;
        }
        session.setPhaseDeadline(System.currentTimeMillis() + dur);
        ScheduledFuture<?> future =
                scheduler.schedule(() -> advanceRoom(session.id()), dur, TimeUnit.MILLISECONDS);
        timers.put(session.id(), future);
    }

    /** Auto-advance a room's phase when its timer fires, then arm the next one. */
    private void advanceRoom(String roomCode) {
        GameSession session = rooms.get(roomCode);
        if (session == null) return;
        session.advance();                    // engine resolves + checks the winner internally
        armTimer(session);                    // no-op deadline once the match is GAME_OVER
    }

    private long durationMs(GamePhaseType phase) {
        return switch (phase) {
            case NIGHT -> NIGHT_MS;
            case DAY -> DAY_MS;
            case VOTING -> VOTING_MS;
            default -> 0;
        };
    }

    private void cancelTimer(String roomCode) {
        ScheduledFuture<?> existing = timers.remove(roomCode);
        if (existing != null) existing.cancel(false);
    }

    // --- World construction ---------------------------------------------------

    /** The six-district neon city with a couple of hideable rooms each. */
    private City buildCity() {
        City city = new City();
        city.addLocation(new Location("plaza", "Neon Plaza", new Position(0, 0)));
        city.addLocation(new Location("market", "Data Market", new Position(3, 2)));
        city.addLocation(new Location("docks", "Rust Docks", new Position(6, 1)));
        city.addLocation(new Location("tower", "Spire Tower", new Position(6, 5)));
        city.addLocation(new Location("alley", "Glitch Alley", new Position(1, 4)));
        city.addLocation(new Location("garden", "Hydro Garden", new Position(3, 6)));
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
        return city;
    }

    /** A couple of NPC witnesses who wander and remember what they saw (never their role). */
    private void seedNpcs(GameContext ctx) {
        ctx.addNpc(new NPC("n1", "Old Kesh", Personality.neutral(), "plaza"));
        NPC wire = new NPC("n2", "Wire", Personality.neutral(), "docks");
        wire.setRoomId("warehouse"); // an NPC hiding off the commons — only found by entering
        ctx.addNpc(wire);
    }

    private static void addRooms(City city, String districtId, String... roomIdNamePairs) {
        Location loc = city.location(districtId);
        if (loc == null) return;
        for (int i = 0; i + 1 < roomIdNamePairs.length; i += 2) {
            loc.addRoom(roomIdNamePairs[i], roomIdNamePairs[i + 1]);
        }
    }

    private String uniqueRoomCode() {
        String code;
        do {
            code = randomCode();
        } while (rooms.containsKey(code));
        return code;
    }

    private String randomCode() {
        // Unambiguous alphabet (no O/0/I/1) for codes players read aloud.
        final String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(5);
        for (int i = 0; i < 5; i++) sb.append(alphabet.charAt(codeRng.nextInt(alphabet.length())));
        return sb.toString();
    }
}
