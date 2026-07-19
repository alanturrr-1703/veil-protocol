package com.veil.domain.roles;

import com.veil.domain.action.EnterRoomAction;
import com.veil.domain.action.GameAction;
import com.veil.domain.action.MoveAction;
import com.veil.domain.action.QueryNPCAction;
import com.veil.domain.action.ShieldAction;
import com.veil.domain.action.VoteAction;
import com.veil.domain.player.Role;
import com.veil.phases.GamePhaseType;

/**
 * Protector. Can shield a player or NPC for the night, negating an attack.
 */
public class AegisRole implements RoleStrategy {

    @Override
    public Role role() {
        return Role.AEGIS;
    }

    @Override
    public boolean canPerform(GameAction action, GamePhaseType phase) {
        if (action instanceof VoteAction) return phase == GamePhaseType.VOTING;
        if (action instanceof ShieldAction) return phase == GamePhaseType.NIGHT;
        if (action instanceof MoveAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        if (action instanceof EnterRoomAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        if (action instanceof QueryNPCAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        return false;
    }
}
