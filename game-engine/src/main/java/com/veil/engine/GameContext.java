package com.veil.engine;

import com.veil.domain.npc.NPC;
import com.veil.domain.player.Player;
import com.veil.domain.world.City;
import com.veil.state.PrivateState;
import com.veil.state.PublicState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * The engine's working set for a single match: the world, the roster, the two state
 * partitions, a deterministic clock (tick) and a seeded RNG (for reproducible replays).
 */
public class GameContext {

    private final City city;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<String, NPC> npcs = new LinkedHashMap<>();
    private final PublicState publicState = new PublicState();
    private final PrivateState privateState = new PrivateState();
    private final Random rng;

    private long tick = 0;

    public GameContext(City city, long seed) {
        this.city = city;
        this.rng = new Random(seed);
    }

    public City city() { return city; }
    public Map<String, Player> players() { return players; }
    public Map<String, NPC> npcs() { return npcs; }
    public PublicState publicState() { return publicState; }
    public PrivateState privateState() { return privateState; }
    public Random rng() { return rng; }

    public long tick() { return tick; }
    public long advanceTick() { return ++tick; }

    public void addPlayer(Player player) {
        players.put(player.id(), player);
        var loc = city.location(player.locationId());
        if (loc != null) loc.addPlayer(player.id());
    }

    public void addNpc(NPC npc) {
        npcs.put(npc.id(), npc);
        var loc = city.location(npc.locationId());
        if (loc != null) loc.addNpc(npc.id());
    }
}
