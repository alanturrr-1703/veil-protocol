package com.veil.ai;

import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Observation;

import java.util.List;

/**
 * Builds a tightly-scoped prompt from ONLY an NPC's personality and the specific
 * memories it is willing to share. The model is explicitly forbidden from adding
 * facts, guaranteeing NPCs never lie and stay consistent across askers.
 */
public class PromptBuilder {

    public String build(NPC npc, String topic, List<Observation> shareable) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(npc.displayName())
                .append(", a resident of Neon City. Stay fully in character.\n");
        sb.append("Personality: talkativeness=").append(npc.personality().talkativeness())
                .append(", cautious=").append(npc.personality().cautious()).append(".\n\n");

        sb.append("You may ONLY reference the following facts you personally witnessed. ")
                .append("Do NOT invent anything. If you know nothing relevant, say so plainly.\n");

        if (shareable.isEmpty()) {
            sb.append("- (you recall nothing relevant)\n");
        } else {
            for (Observation o : shareable) {
                sb.append("- ").append(o.subject()).append(' ').append(o.action())
                        .append(" at ").append(o.locationId())
                        .append(" (t=").append(o.tick()).append(")\n");
            }
        }

        sb.append("\nQuestion topic: ").append(topic).append('\n');
        sb.append("Answer in 1-2 sentences, in character.");
        return sb.toString();
    }

    /** Deterministic fallback text when no LLM is available. */
    public String fallbackAnswer(NPC npc, List<Observation> shareable) {
        if (shareable.isEmpty()) {
            return npc.displayName() + " shrugs. \"I didn't see anything worth mentioning.\"";
        }
        StringBuilder sb = new StringBuilder(npc.displayName()).append(" recalls: ");
        for (int i = 0; i < shareable.size(); i++) {
            Observation o = shareable.get(i);
            if (i > 0) sb.append("; ");
            sb.append(o.subject()).append(' ').append(o.action()).append(" at ").append(o.locationId());
        }
        return sb.append('.').toString();
    }
}
