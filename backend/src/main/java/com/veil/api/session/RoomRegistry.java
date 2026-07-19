package com.veil.api.session;

/**
 * Room-code matchmaking seam. A host creates a room and shares its short code; other humans
 * join with that code; the host starts the match, which deals roles and opens the first
 * Night. One {@link GameSession} (one authoritative {@code VeilEngine}) backs each room.
 *
 * <p>Kept as an interface so the lobby transport (REST) never depends on a concrete service,
 * and so a ranked/global matchmaker could later be provided behind the same contract.
 */
public interface RoomRegistry {

    /** Create a new room in the LOBBY phase with the host seated. Returns the live session. */
    GameSession createRoom(String hostPlayerId, String hostDisplayName);

    /** Seat another human in an existing room's lobby. Returns the session, or null if unknown. */
    GameSession joinRoom(String roomCode, String playerId, String displayName);

    /**
     * Deal roles (committing each to the confidential referee) and resolve the lobby into the
     * first Night, arming the phase clock. Returns the session, or null if unknown / not startable.
     */
    GameSession startRoom(String roomCode);

    /** Look up a live room by its code, or null if none. */
    GameSession byCode(String roomCode);
}
