package com.veil.domain.player;

/**
 * The role archetypes. This enum only names the role and its faction; the actual
 * behavior lives in a {@code RoleStrategy} (Strategy pattern) so a Player is
 * identical on the wire regardless of which role it secretly holds.
 */
public enum Role {
    SHADOW(Faction.SHADOW),
    ORACLE(Faction.CITY),
    AEGIS(Faction.CITY),
    CITIZEN(Faction.CITY);

    private final Faction faction;

    Role(Faction faction) {
        this.faction = faction;
    }

    public Faction faction() {
        return faction;
    }
}
