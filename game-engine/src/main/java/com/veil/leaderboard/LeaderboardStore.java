package com.veil.leaderboard;

import java.util.List;
import java.util.Optional;

/**
 * Persistence seam for standings. The default is in-memory ({@link InMemoryLeaderboardStore});
 * a real deployment can swap in a DB-backed implementation without touching the listener
 * or the view.
 */
public interface LeaderboardStore {

    /** The current standing for a player, if one has been recorded. */
    Optional<PlayerStanding> find(String playerId);

    /** Insert or replace a player's standing. */
    void save(PlayerStanding standing);

    /** All standings, in no guaranteed order (the view sorts them). */
    List<PlayerStanding> all();
}
