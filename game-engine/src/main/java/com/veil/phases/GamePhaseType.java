package com.veil.phases;

/**
 * The lifecycle phases of a match. Kept as a lightweight enum so that actions and
 * roles can declare phase requirements without depending on concrete phase classes.
 */
public enum GamePhaseType {
    LOBBY,
    NIGHT,
    DAY,
    VOTING,
    RESOLUTION
}
