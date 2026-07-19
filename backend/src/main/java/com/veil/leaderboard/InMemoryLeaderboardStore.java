package com.veil.leaderboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link LeaderboardStore}: keeps standings in a map. Insertion-ordered so
 * that, before any ranking, iteration is deterministic (aiding tests and replay).
 */
public class InMemoryLeaderboardStore implements LeaderboardStore {

    private final Map<String, PlayerStanding> standings = new LinkedHashMap<>();

    @Override
    public Optional<PlayerStanding> find(String playerId) {
        return Optional.ofNullable(standings.get(playerId));
    }

    @Override
    public void save(PlayerStanding standing) {
        standings.put(standing.playerId(), standing);
    }

    @Override
    public List<PlayerStanding> all() {
        return new ArrayList<>(standings.values());
    }
}
