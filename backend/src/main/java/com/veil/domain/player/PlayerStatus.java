package com.veil.domain.player;

/**
 * Public-safe status flags for a player. "alive" is broadcast in the public roster;
 * "protectedThisNight" is transient and cleared at the end of each night by the engine.
 */
public class PlayerStatus {

    private boolean alive = true;
    private boolean protectedThisNight = false;
    private boolean silenced = false;

    public boolean isAlive() { return alive; }
    public void kill() { this.alive = false; }

    public boolean isProtectedThisNight() { return protectedThisNight; }
    public void setProtectedThisNight(boolean protectedThisNight) { this.protectedThisNight = protectedThisNight; }

    public boolean isSilenced() { return silenced; }
    public void setSilenced(boolean silenced) { this.silenced = silenced; }
}
