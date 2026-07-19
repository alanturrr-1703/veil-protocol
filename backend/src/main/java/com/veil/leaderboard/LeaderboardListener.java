package com.veil.leaderboard;

import com.veil.api.DTOs.LeaderboardView;
import com.veil.events.GameEndedEvent;
import com.veil.events.GameEvent;
import com.veil.events.GameEventListener;

import java.util.Comparator;
import java.util.List;

/**
 * Analytics Observer. Registered on the {@code EventBus}, it derives standings PURELY
 * from {@link GameEndedEvent} payloads — it never reads {@code GameContext},
 * {@code PrivateState}, players, or roles directly. It stays dormant (does nothing on
 * every other event type) until a match publishes its end, so it never fabricates game
 * state.
 *
 * <p>Scoring is delegated to a single {@link ScoringConfig}; persistence to a
 * {@link LeaderboardStore}. Register with {@code engine.addListener(leaderboard)}.
 */
public class LeaderboardListener implements GameEventListener {

    /** Ranking order: points desc, win rate desc, games desc, then playerId asc (stable). */
    private static final Comparator<PlayerStanding> RANKING =
            Comparator.comparingInt(PlayerStanding::points).reversed()
                    .thenComparing(Comparator.comparingDouble(PlayerStanding::winRate).reversed())
                    .thenComparing(Comparator.comparingInt(PlayerStanding::gamesPlayed).reversed())
                    .thenComparing(PlayerStanding::playerId);

    private final LeaderboardStore store;
    private final ScoringConfig scoring;

    /** Uses the in-memory store and default scoring. */
    public LeaderboardListener() {
        this(new InMemoryLeaderboardStore(), new ScoringConfig());
    }

    public LeaderboardListener(LeaderboardStore store, ScoringConfig scoring) {
        this.store = store;
        this.scoring = scoring;
    }

    @Override
    public void onEvent(GameEvent event) {
        if (!(event instanceof GameEndedEvent ended)) {
            return; // dormant until a game actually ends
        }
        for (GameEndedEvent.PlayerResult r : ended.results()) {
            int points = scoring.pointsFor(r.role(), ended.winningFaction(), r.survived());
            boolean won = r.faction() == ended.winningFaction();
            PlayerStanding current =
                    store.find(r.playerId()).orElseGet(() -> PlayerStanding.empty(r.playerId(), r.displayName()));
            store.save(current.withResult(points, won, r.displayName()));
        }
    }

    /** The underlying store, e.g. for a custom query or a different view. */
    public LeaderboardStore store() {
        return store;
    }

    /** Build the public, ranked {@link LeaderboardView} (served separately from PlayerView). */
    public LeaderboardView view() {
        List<PlayerStanding> sorted = store.all();
        sorted.sort(RANKING);

        List<LeaderboardView.Row> rows = new java.util.ArrayList<>(sorted.size());
        int rank = 1;
        for (PlayerStanding s : sorted) {
            rows.add(new LeaderboardView.Row(
                    rank++, s.playerId(), s.displayName(),
                    s.points(), s.gamesPlayed(), s.wins(), s.winRate()));
        }
        return new LeaderboardView(rows);
    }
}
