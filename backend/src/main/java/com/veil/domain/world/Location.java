package com.veil.domain.world;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A node in the city graph. Tracks which players and NPCs currently occupy it, the rooms
 * inside it (the open {@code commons} plus a few side rooms one can hide in), and any
 * evidence dropped here. Occupant sets are the raw truth; what a client sees about
 * occupants is decided later at the redaction boundary (only your own room is visible).
 */
public class Location {

    private final String id;
    private final String name;
    private final Position position;
    private final Set<String> playerIds = new LinkedHashSet<>();
    private final Set<String> npcIds = new LinkedHashSet<>();
    private final Set<String> evidence = new LinkedHashSet<>();
    // roomId -> display name. Always contains the open "commons"; districts add side rooms.
    private final Map<String, String> rooms = new LinkedHashMap<>();

    public Location(String id, String name, Position position) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.rooms.put("commons", "Commons");
    }

    public String id() { return id; }
    public String name() { return name; }
    public Position position() { return position; }

    public Map<String, String> rooms() { return rooms; }
    public void addRoom(String roomId, String roomName) { rooms.put(roomId, roomName); }
    public boolean hasRoom(String roomId) { return rooms.containsKey(roomId); }

    public Set<String> playerIds() { return playerIds; }
    public Set<String> npcIds() { return npcIds; }
    public Set<String> evidence() { return evidence; }

    public void addPlayer(String playerId) { playerIds.add(playerId); }
    public void removePlayer(String playerId) { playerIds.remove(playerId); }

    public void addNpc(String npcId) { npcIds.add(npcId); }
    public void removeNpc(String npcId) { npcIds.remove(npcId); }

    public void addEvidence(String note) { evidence.add(note); }
}
