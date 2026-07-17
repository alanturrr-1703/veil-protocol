package com.veil.domain.roles;

import com.veil.domain.action.GameAction;
import com.veil.domain.action.MoveAction;
import com.veil.domain.action.QueryNPCAction;
import com.veil.domain.player.Role;
import com.veil.phases.GamePhaseType;

/**
 * Ordinary citizen. Gathers information by moving and questioning NPCs, and votes
 * during the day. Has no night power.
 */
public class CitizenRole implements RoleStrategy {

    @Override
    public Role role() {
        return Role.CITIZEN;
    }

    @Override
    public boolean canPerform(GameAction action, GamePhaseType phase) {
        if (action instanceof MoveAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        if (action instanceof QueryNPCAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        return false;
    }
}
