package com.veil.domain.npc;

import java.util.List;

/**
 * A single fact an NPC witnessed. Immutable — memory is append-only. An NPC can only
 * ever speak to facts it holds as Observations, which is why NPCs "never lie" and can
 * never reveal roles: they have no access to global state or to anyone's secret role,
 * only to what they physically saw.
 *
 * <p>Crucially, for a killing an NPC does NOT record who the attacker was. It records
 * the victim and the set of people who were <em>nearby</em> ({@code suspects}) — so the
 * best it can ever offer is "it could have been one of these few", never a certainty.
 *
 * @param subject    who/what the observation is about (e.g. the victim of an attack)
 * @param action     what happened (e.g. "was attacked", "entered")
 * @param locationId where it happened
 * @param tick       when it happened
 * @param confidence how sure the NPC is (0..1); shapes how it is later phrased
 * @param suspects   people the NPC saw nearby — a fuzzy candidate set, never the culprit alone
 */
public record Observation(
        String subject,
        String action,
        String locationId,
        long tick,
        double confidence,
        List<String> suspects
) {
    public Observation {
        suspects = suspects == null ? List.of() : List.copyOf(suspects);
    }

    /** Convenience for observations that carry no suspect set. */
    public Observation(String subject, String action, String locationId, long tick, double confidence) {
        this(subject, action, locationId, tick, confidence, List.of());
    }

    public boolean isDeath() {
        return action != null && action.toLowerCase().contains("attack");
    }

    public boolean matchesTopic(String topic) {
        if (topic == null || topic.isBlank()) return true;
        String t = topic.toLowerCase();

        if (contains(subject, t) || contains(action, t) || contains(locationId, t)) return true;
        for (String s : suspects) {
            if (contains(s, t)) return true;
        }

        // Questions about the night / what happened / a death map to death observations,
        // so "what did you see last night" surfaces the killing.
        if (isDeath()) {
            for (String kw : DEATH_KEYWORDS) {
                if (t.contains(kw)) return true;
            }
        }
        return false;
    }

    private static boolean contains(String field, String needle) {
        return field != null && field.toLowerCase().contains(needle);
    }

    private static final String[] DEATH_KEYWORDS = {
            "night", "saw", "see", "seen", "happen", "death", "die", "died",
            "kill", "killed", "attack", "murder", "body", "witness"
    };
}
