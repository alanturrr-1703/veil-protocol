package com.veil.events;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;

import java.util.List;
import java.util.Set;

/**
 * Terminal event: a match has ended. Emitted once, PUBLIC, and it is the ONLY thing
 * the leaderboard consumes — the analytics listener derives every standing from this
 * payload and never touches game state. The event therefore carries a full, final
 * snapshot of the roster (role and faction revealed, since the game is over) plus who
 * survived and which faction won.
 *
 * <p>Win-condition detection is not wired into the phase machine yet; publish this via
 * {@code VeilEngine.publishGameEnded(...)} once a winner is determined.
 */
public class GameEndedEvent extends GameEvent {

    /**
     * A single player's final record within the ended match.
     *
     * @param playerId    the player's id
     * @param displayName the player's display name
     * @param role        the role held (revealed — the match is over)
     * @param faction     the role's faction (City or Shadows)
     * @param survived    whether the player was still alive at game end
     */
    public record PlayerResult(
            String playerId,
            String displayName,
            Role role,
            Faction faction,
            boolean survived
    ) {}

    private final Faction winningFaction;
    private final List<PlayerResult> results;

    public GameEndedEvent(long tick, Faction winningFaction, List<PlayerResult> results) {
        super(tick, Visibility.PUBLIC, Set.of());
        this.winningFaction = winningFaction;
        this.results = List.copyOf(results);
    }

    public Faction winningFaction() { return winningFaction; }
    public List<PlayerResult> results() { return results; }

    @Override
    public String describe() {
        return (winningFaction == Faction.SHADOW ? "The Shadows" : "The City")
                + " win the match.";
    }
}
