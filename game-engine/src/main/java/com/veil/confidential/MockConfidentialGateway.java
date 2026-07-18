package com.veil.confidential;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process stand-in for Midnight. It behaves like the confidential layer: it privately
 * holds the secret roles + blinding salts (as Midnight would off-chain), publishes only
 * commitments, and answers confidential queries with PUBLIC-SAFE results. The engine
 * depends only on {@link ConfidentialGateway}, so this can be swapped for a real Midnight
 * client with zero game-logic changes.
 *
 * <p>The internal maps model data the engine must NEVER read directly — mirroring how a
 * real deployment keeps roles in Midnight private state, not in the Java server.
 */
public class MockConfidentialGateway implements ConfidentialGateway {

    private final Map<String, Role> secretRoles = new ConcurrentHashMap<>();
    private final Map<String, String> salts = new ConcurrentHashMap<>();
    private final Map<String, String> commitments = new ConcurrentHashMap<>();
    private final SecureRandom rng = new SecureRandom();

    @Override
    public String commitRole(String playerId, Role role) {
        String salt = randomSalt();
        secretRoles.put(playerId, role);
        salts.put(playerId, salt);
        String commitment = hash(role.name() + ":" + salt);
        commitments.put(playerId, commitment);
        return commitment;
    }

    @Override
    public String commitmentOf(String playerId) {
        return commitments.getOrDefault(playerId, "");
    }

    @Override
    public Faction investigate(String oracleId, String targetId) {
        Role target = secretRoles.get(targetId);
        // Selective disclosure: return only the faction, never the exact role.
        return target == null ? Faction.NEUTRAL : target.faction();
    }

    @Override
    public ConfidentialResult submitAttack(String attackerId, String targetId) {
        Role attacker = secretRoles.get(attackerId);
        if (attacker != Role.SHADOW) {
            return ConfidentialResult.denied("attack rejected: submitter is not a Shadow");
        }
        // Bind the action to an opaque commitment of the target; target stays hidden.
        String opaque = hash(targetId + ":" + randomSalt());
        return ConfidentialResult.ok(opaque, "attack authorized by a valid Shadow");
    }

    @Override
    public boolean verifyWin(Faction faction) {
        boolean anyAlive = secretRoles.values().stream().anyMatch(r -> r.faction() == faction);
        // Example: the CITY wins when no SHADOW-faction role remains.
        return faction == Faction.CITY ? secretRoles.values().stream().noneMatch(r -> r.faction() == Faction.SHADOW)
                : anyAlive;
    }

    @Override
    public Faction resolveWinner(Set<String> alivePlayerIds) {
        // Only the PUBLIC alive-set crosses the boundary. We look up each alive player's
        // secret role internally (never disclosed) and output only the winning faction.
        long aliveShadows = alivePlayerIds.stream()
                .map(secretRoles::get)
                .filter(r -> r != null && r.faction() == Faction.SHADOW)
                .count();
        long aliveCity = alivePlayerIds.stream()
                .map(secretRoles::get)
                .filter(r -> r != null && r.faction() == Faction.CITY)
                .count();

        if (aliveShadows == 0) return Faction.CITY;          // every Shadow eliminated
        if (aliveShadows >= aliveCity) return Faction.SHADOW; // Shadows reach parity
        return Faction.NEUTRAL;                               // undecided — match continues
    }

    private String randomSalt() {
        byte[] b = new byte[32];
        rng.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
