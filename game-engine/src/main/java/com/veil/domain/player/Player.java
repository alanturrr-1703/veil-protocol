package com.veil.domain.player;

import com.veil.domain.roles.RoleStrategy;

/**
 * A human-controlled participant. A Player <em>has-a</em> {@link RoleStrategy}
 * (Strategy pattern) rather than subclassing per role — this keeps every Player
 * structurally identical so the role can never leak through its concrete type,
 * and allows role swaps / disguises at runtime.
 */
public class Player {

    private final String id;
    private final String displayName;
    private final PlayerStatus status = new PlayerStatus();
    private RoleStrategy role;
    private String locationId;

    public Player(String id, String displayName, RoleStrategy role, String locationId) {
        this.id = id;
        this.displayName = displayName;
        this.role = role;
        this.locationId = locationId;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public PlayerStatus status() { return status; }

    /** Confidential: only exposed via redacted per-viewer projections. */
    public RoleStrategy role() { return role; }
    public void setRole(RoleStrategy role) { this.role = role; }

    public String locationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }
}
