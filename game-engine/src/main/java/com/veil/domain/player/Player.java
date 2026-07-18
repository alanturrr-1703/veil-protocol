package com.veil.domain.player;

import com.veil.domain.roles.RoleStrategy;

/**
 * A human-controlled participant. A Player <em>has-a</em> {@link RoleStrategy}
 * (Strategy pattern) rather than subclassing per role — this keeps every Player
 * structurally identical so the role can never leak through its concrete type,
 * and allows role swaps / disguises at runtime.
 */
public class Player {

    /** The open, shared area of any district — where occupants are visible to each other. */
    public static final String COMMONS = "commons";

    private final String id;
    private final String displayName;
    private final PlayerStatus status = new PlayerStatus();
    private RoleStrategy role;
    private String locationId;
    private String roomId = COMMONS;
    // District this player occupied at the start of the previous night; used to forbid
    // Citizens/Oracle from camping the same district two nights running.
    private String lastNightDistrict;

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

    /** Which room within the district the player is in ({@link #COMMONS} = the open area). */
    public String roomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String lastNightDistrict() { return lastNightDistrict; }
    public void setLastNightDistrict(String district) { this.lastNightDistrict = district; }
}
