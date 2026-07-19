package com.veil.leaderboard;

/**
 * A player's cumulative cross-match record. Immutable: accumulating a new result
 * returns a fresh instance ({@link #withResult}), so a store can treat standings as
 * plain values.
 *
 * @param playerId    the player's id
 * @param displayName most recently seen display name
 * @param points      total points across all matches
 * @param gamesPlayed matches participated in
 * @param wins        matches won (was on the winning faction)
 */
public record PlayerStanding(
        String playerId,
        String displayName,
        int points,
        int gamesPlayed,
        int wins
) {
    /** A zeroed standing for a player who has not been scored yet. */
    public static PlayerStanding empty(String playerId, String displayName) {
        return new PlayerStanding(playerId, displayName, 0, 0, 0);
    }

    /** Wins divided by games played; 0.0 before any game is recorded. */
    public double winRate() {
        return gamesPlayed == 0 ? 0.0 : (double) wins / gamesPlayed;
    }

    /** Fold one more match result in, refreshing the display name. */
    public PlayerStanding withResult(int addedPoints, boolean won, String latestName) {
        return new PlayerStanding(
                playerId,
                latestName == null ? displayName : latestName,
                points + addedPoints,
                gamesPlayed + 1,
                wins + (won ? 1 : 0)
        );
    }
}
