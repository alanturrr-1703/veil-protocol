package com.veil.confidential;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;

import java.util.Set;

/**
 * The single seam between the authoritative Java engine and the confidential layer
 * (Midnight). The engine owns ALL public state; anything on the confidential list
 * (roles, night-action authorization, investigation results, win verification) goes
 * through this gateway and comes back as a PUBLIC-SAFE result — never a raw secret.
 *
 * <p>Two implementations plug in behind this interface:
 * <ul>
 *   <li>{@link MockConfidentialGateway} — in-process, deterministic, for local dev and
 *       tests when no Midnight node is available.</li>
 *   <li>{@code MidnightConfidentialGateway} — calls the Midnight proof server / midnight.js
 *       so the confidential state actually lives on-chain (see docs/midnight-design.md).</li>
 * </ul>
 *
 * <p>Because the engine only ever depends on this interface, swapping mock ↔ real Midnight
 * requires no change to game logic.
 */
public interface ConfidentialGateway {

    /**
     * Register a player's secret role in the confidential store. Returns ONLY a public
     * commitment (a hash) that may be broadcast; the role itself never leaves the gateway.
     */
    String commitRole(String playerId, Role role);

    /** The public commitment previously produced for a player (safe to expose). */
    String commitmentOf(String playerId);

    /**
     * Confidentially resolve an Oracle investigation. Returns ONLY the target's faction —
     * the exact role is never disclosed. Mirrors {@code proveInvestigation} in Compact.
     */
    Faction investigate(String oracleId, String targetId);

    /**
     * Verify a Shadow is authorized to attack a target. The gateway checks the attacker's
     * committed role privately and returns a public-safe outcome (authorized or not) with
     * an opaque action reference — no role or attacker identity is disclosed by the result.
     * Mirrors {@code submitShadowAttack} in Compact.
     */
    ConfidentialResult submitAttack(String attackerId, String targetId);

    /**
     * Verify the end-game win condition for a faction without revealing individual roles
     * (e.g. "the Shadow faction has been eliminated"). Mirrors a win-check circuit.
     */
    boolean verifyWin(Faction faction);

    /**
     * Confidentially resolve which faction (if any) has won, given ONLY the PUBLIC set of
     * players still alive. Roles never leave the gateway: it intersects the public
     * alive-set with its own private role commitments and discloses ONLY the winning
     * faction — or {@link Faction#NEUTRAL} to mean "no winner yet". This mirrors a Midnight
     * win-condition circuit whose public inputs are the alive commitments and whose private
     * witness is each role, and which outputs (discloses) just the winner.
     *
     * <p>Because the winner depends on hidden roles, the authoritative Java engine cannot
     * compute it without leaking; it delegates here and trusts the disclosed faction. This
     * is precisely the leaderboard's trust anchor — a match is only scored on a win the
     * confidential layer confirmed.
     */
    Faction resolveWinner(Set<String> alivePlayerIds);
}
