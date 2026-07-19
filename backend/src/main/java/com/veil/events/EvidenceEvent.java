package com.veil.events;

import java.util.Set;

/**
 * Evidence surfaced to a specific investigator/asker (Oracle investigation result,
 * NPC answer, etc.). Always PRIVATE and addressed to a single viewer.
 */
public class EvidenceEvent extends GameEvent {

    private final String subjectId;
    private final String detail;

    private EvidenceEvent(long tick, Set<String> audience, String subjectId, String detail) {
        super(tick, Visibility.PRIVATE, audience);
        this.subjectId = subjectId;
        this.detail = detail;
    }

    public static EvidenceEvent privateTo(long tick, String viewerId, String subjectId, String detail) {
        return new EvidenceEvent(tick, Set.of(viewerId), subjectId, detail);
    }

    public String subjectId() { return subjectId; }
    public String detail() { return detail; }

    @Override
    public String describe() {
        return "[private] " + detail;
    }
}
