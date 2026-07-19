package com.veil.confidential;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-match view onto the shared {@link ConfidentialGateway}. It is the single object the
 * engine and its actions hold to reach the confidential referee: they pass RAW player ids
 * (e.g. {@code "p3"}) and this adapter namespaces them by match ({@code "room/p3"}) so a
 * server-wide gateway (or a real Midnight deployment / relayer) can host many concurrent
 * matches without id collisions.
 *
 * <p>Everything this exposes is PUBLIC-SAFE: a commitment hash, a faction bit, an
 * authorized yes/no, or a winning faction. Raw roles never cross this boundary.
 *
 * <p>Because the engine depends only on this small adapter, swapping the mock gateway for
 * the relayer-backed {@code MidnightConfidentialGateway} (Phase 4) requires no engine change.
 */
public final class MatchConfidential {

    private final ConfidentialGateway gateway;
    private final String matchId;
    // Public commitments produced for this match, keyed by RAW player id. Safe to broadcast.
    private final Map<String, String> commitments = new LinkedHashMap<>();

    public MatchConfidential(ConfidentialGateway gateway, String matchId) {
        this.gateway = gateway;
        this.matchId = matchId;
    }

    /** Register a player's secret role; returns (and caches) only the public commitment. */
    public String commit(String playerId, Role role) {
        String commitment = gateway.commitRole(key(playerId), role);
        commitments.put(playerId, commitment);
        return commitment;
    }

    /** The public commitments for this match (raw playerId -> hash). Safe to expose. */
    public Map<String, String> commitments() {
        return Collections.unmodifiableMap(commitments);
    }

    /**
     * Ask the referee whether a Shadow is authorized to strike the target. Returns only a
     * boolean; the attacker's role and the bound target stay inside the gateway.
     */
    public boolean authorizeAttack(String attackerId, String targetId) {
        return gateway.submitAttack(key(attackerId), key(targetId)).authorized();
    }

    /** Confidential Oracle read: returns only the target's faction, never the exact role. */
    public Faction investigate(String oracleId, String targetId) {
        return gateway.investigate(key(oracleId), key(targetId));
    }

    /**
     * Resolve the winning faction from the PUBLIC alive-set (raw ids). The gateway intersects
     * it with its private role commitments and discloses only the winner (or NEUTRAL).
     */
    public Faction resolveWinner(Set<String> aliveRawIds) {
        Set<String> namespaced = aliveRawIds.stream().map(this::key).collect(Collectors.toSet());
        return gateway.resolveWinner(namespaced);
    }

    private String key(String playerId) {
        return matchId + "/" + playerId;
    }
}
