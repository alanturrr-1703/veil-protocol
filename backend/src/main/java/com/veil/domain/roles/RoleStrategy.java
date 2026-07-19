package com.veil.domain.roles;

import com.veil.domain.action.GameAction;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;
import com.veil.phases.GamePhaseType;

/**
 * Strategy pattern. Encapsulates the interchangeable behavior of a role. A Player
 * holds one of these instead of subclassing per role, so behavior can be swapped
 * at runtime and the role never leaks through the Player's concrete type.
 */
public interface RoleStrategy {

    /** The role archetype this strategy implements. */
    Role role();

    /** Faction / win-condition alignment (derived from the role). */
    default Faction faction() {
        return role().faction();
    }

    /** Whether this role is permitted to issue the given action in the given phase. */
    boolean canPerform(GameAction action, GamePhaseType phase);
}
