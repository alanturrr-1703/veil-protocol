package com.veil.leaderboard;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;

/**
 * All leaderboard scoring lives in this ONE object so the rules are trivial to tune —
 * change a field (or pass a custom instance to the listener) and every future match
 * scores differently. Defaults implement the agreed rules:
 * <ul>
 *   <li>win as Citizen: +2</li>
 *   <li>win as Oracle / Aegis: +3</li>
 *   <li>win as Shadow: +4</li>
 *   <li>+2 if you survived AND your side won</li>
 *   <li>+1 participation (everyone, win or lose)</li>
 * </ul>
 * Points are only awarded for a role-win to players on the WINNING faction; losers get
 * participation only.
 */
public class ScoringConfig {

    public int winAsCitizen = 2;
    public int winAsOracle = 3;
    public int winAsAegis = 3;
    public int winAsShadow = 4;
    public int survivingWinnerBonus = 2;
    public int participation = 1;

    /** Default scoring. */
    public ScoringConfig() {}

    /** Points a player earns for one match given their role, the winner, and survival. */
    public int pointsFor(Role role, Faction winningFaction, boolean survived) {
        int points = participation;
        boolean onWinningSide = role.faction() == winningFaction;
        if (onWinningSide) {
            points += roleWinBonus(role);
            if (survived) {
                points += survivingWinnerBonus;
            }
        }
        return points;
    }

    private int roleWinBonus(Role role) {
        return switch (role) {
            case CITIZEN -> winAsCitizen;
            case ORACLE -> winAsOracle;
            case AEGIS -> winAsAegis;
            case SHADOW -> winAsShadow;
        };
    }
}
