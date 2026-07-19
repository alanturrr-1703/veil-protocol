package com.veil.domain.roles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deals a balanced set of role strategies for a match, scaled to the number of players who
 * joined the room. The composition (how many Shadows, whether an Oracle/Aegis are present)
 * is the only game-balance knob here; the assignment order is shuffled with the match RNG so
 * seats can't predict their role from join order.
 */
public final class RoleDealer {

    /** Fewest players a match can start with (2 City vs 1 Shadow gives a real deduction). */
    public static final int MIN_PLAYERS = 3;

    private RoleDealer() {}

    /**
     * Build exactly {@code n} role strategies: one or two Shadows (two once the room is 7+),
     * an Oracle and an Aegis when there's room, and Citizens for the rest. Shuffled.
     */
    public static List<RoleStrategy> deal(int n, Random rng) {
        if (n < MIN_PLAYERS) {
            throw new IllegalArgumentException("need at least " + MIN_PLAYERS + " players, got " + n);
        }

        int shadows = n >= 7 ? 2 : 1;
        List<RoleStrategy> roles = new ArrayList<>(n);
        for (int i = 0; i < shadows; i++) roles.add(new ShadowRole());

        // Fill the City side: Oracle, then Aegis (if seats remain), then Citizens.
        if (roles.size() < n) roles.add(new OracleRole());
        if (roles.size() < n) roles.add(new AegisRole());
        while (roles.size() < n) roles.add(new CitizenRole());

        Collections.shuffle(roles, rng);
        return roles;
    }
}
