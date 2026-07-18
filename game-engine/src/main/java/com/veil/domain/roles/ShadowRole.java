package com.veil.domain.roles;

import com.veil.domain.action.AttackAction;
import com.veil.domain.action.EnterRoomAction;
import com.veil.domain.action.GameAction;
import com.veil.domain.action.MoveAction;
import com.veil.domain.action.QueryNPCAction;
import com.veil.domain.player.Role;
import com.veil.phases.GamePhaseType;

/**
 * Hidden faction. Can eliminate targets and hunt NPC witnesses within a limited
 * radius and a limited number of attempts per match.
 */
public class ShadowRole implements RoleStrategy {

    private final double huntRadius;
    private final int maxHuntAttempts;

    public ShadowRole() {
        this(2.5, 3);
    }

    public ShadowRole(double huntRadius, int maxHuntAttempts) {
        this.huntRadius = huntRadius;
        this.maxHuntAttempts = maxHuntAttempts;
    }

    public double huntRadius() { return huntRadius; }
    public int maxHuntAttempts() { return maxHuntAttempts; }

    @Override
    public Role role() {
        return Role.SHADOW;
    }

    @Override
    public boolean canPerform(GameAction action, GamePhaseType phase) {
        if (action instanceof AttackAction) return phase == GamePhaseType.NIGHT;
        if (action instanceof MoveAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        if (action instanceof EnterRoomAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        if (action instanceof QueryNPCAction) return phase == GamePhaseType.NIGHT || phase == GamePhaseType.DAY;
        return false;
    }
}
