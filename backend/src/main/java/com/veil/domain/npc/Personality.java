package com.veil.domain.npc;

/**
 * Traits that shape HOW an NPC shares what it knows — never WHAT is true. An NPC
 * with low talkativeness or low trust in the asker withholds; it never fabricates.
 *
 * @param talkativeness 0..1 baseline willingness to share
 * @param cautious      if true, only shares high-confidence memories
 * @param trustThreshold minimum trust in the asker required to share
 */
public record Personality(
        double talkativeness,
        boolean cautious,
        double trustThreshold
) {
    public static Personality neutral() {
        return new Personality(0.6, false, 0.3);
    }

    /** Given trust in the asker, decide whether a specific memory would be shared. */
    public boolean willShare(Observation observation, double trustInAsker) {
        if (trustInAsker < trustThreshold) return false;
        if (cautious && observation.confidence() < 0.7) return false;
        return talkativeness > 0.2;
    }
}
