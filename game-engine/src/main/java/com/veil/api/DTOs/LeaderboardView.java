package com.veil.api.DTOs;

import java.util.List;

/**
 * Public, cross-match analytics — served SEPARATELY from {@link PlayerView}. Unlike a
 * PlayerView, this holds no per-viewer secret: every field (points, wins, win rate) is
 * aggregate history, safe to show anyone, so it is not built per viewer and carries no
 * confidential slice.
 *
 * <p>Rows arrive pre-ranked (points, then win rate, then games played, then playerId),
 * so the frontend renders them top-down as-is.
 */
public record LeaderboardView(List<Row> rows) {

    /**
     * One ranked line.
     *
     * @param rank        1-based position after sorting
     * @param playerId    the player's id
     * @param displayName the player's display name
     * @param points      total points
     * @param gamesPlayed matches played
     * @param wins        matches won
     * @param winRate     wins / gamesPlayed
     */
    public record Row(
            int rank,
            String playerId,
            String displayName,
            int points,
            int gamesPlayed,
            int wins,
            double winRate
    ) {}
}
