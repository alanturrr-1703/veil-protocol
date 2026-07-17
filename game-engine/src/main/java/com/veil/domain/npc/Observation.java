package com.veil.domain.npc;

/**
 * A single fact an NPC witnessed. Immutable — memory is append-only. An NPC can
 * only ever speak to facts it holds as Observations, which is why NPCs "never lie":
 * they have no access to global state, only to what they saw.
 *
 * @param subject    who/what the observation is about (e.g. a player id)
 * @param action     what happened (e.g. "was attacked", "entered")
 * @param locationId where it happened
 * @param tick       when it happened
 * @param confidence how sure the NPC is (0..1); shapes how it is later phrased
 */
public record Observation(
        String subject,
        String action,
        String locationId,
        long tick,
        double confidence
) {
    public boolean matchesTopic(String topic) {
        if (topic == null || topic.isBlank()) return true;
        String t = topic.toLowerCase();
        return (subject != null && subject.toLowerCase().contains(t))
                || (action != null && action.toLowerCase().contains(t))
                || (locationId != null && locationId.toLowerCase().contains(t));
    }
}
