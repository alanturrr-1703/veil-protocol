package com.veil.domain.world;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A node in the city graph. Tracks which players and NPCs currently occupy it,
 * plus any evidence dropped here. Occupant sets are the raw truth; what a client
 * sees about occupants is decided later at the redaction boundary.
 */
public class Location {

    private final String id;
    private final String name;
    private final Position position;
    private final Set<String> playerIds = new LinkedHashSet<>();
    private final Set<String> npcIds = new LinkedHashSet<>();
    private final Set<String> evidence = new LinkedHashSet<>();

    public Location(String id, String name, Position position) {
        this.id = id;
        this.name = name;
        this.position = position;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Position position() { return position; }

    public Set<String> playerIds() { return playerIds; }
    public Set<String> npcIds() { return npcIds; }
    public Set<String> evidence() { return evidence; }

    public void addPlayer(String playerId) { playerIds.add(playerId); }
    public void removePlayer(String playerId) { playerIds.remove(playerId); }

    public void addNpc(String npcId) { npcIds.add(npcId); }
    public void removeNpc(String npcId) { npcIds.remove(npcId); }

    public void addEvidence(String note) { evidence.add(note); }
}
