package com.veil.domain.roles;

import com.veil.domain.action.GameAction;
import com.veil.domain.action.InvestigateAction;
import com.veil.domain.action.MoveAction;
import com.veil.domain.action.QueryNPCAction;
import com.veil.domain.player.Role;
import com.veil.phases.GamePhaseType;

/**
 * Investigator. Can query evidence and discover partial truths about other players.
 */
public class OracleRole implements RoleStrategy {

    @Override
    public Role role() {
        return Role.ORACLE;
    }

    @Override
    public boolean canPerform(GameAction action, GamePhaseType phase) {
        if (action instanceof InvestigateAction) return phase == GamePhaseType.NIGHT;
        if (action instanceof MoveAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        if (action instanceof QueryNPCAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        return false;
    }
}
