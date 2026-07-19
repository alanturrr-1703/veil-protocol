package com.veil.ai;

import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Observation;

import java.util.List;

/**
 * Builds a tightly-scoped prompt from ONLY an NPC's personality and the specific
 * memories it is willing to share. The model is explicitly forbidden from adding
 * facts, from ever naming a single culprit, and from claiming to know anyone's role —
 * so NPCs stay consistent, never lie, and can only ever offer "one of these few".
 */
public class PromptBuilder {

    public String build(NPC npc, String topic, List<Observation> shareable) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(npc.displayName())
                .append(", a resident of Neon City. Stay fully in character.\n");
        sb.append("Personality: talkativeness=").append(npc.personality().talkativeness())
                .append(", cautious=").append(npc.personality().cautious()).append(".\n\n");

        sb.append("STRICT RULES:\n");
        sb.append("- You do NOT know anyone's secret role or faction. Never say who is a Shadow, ")
                .append("Oracle, Aegis, or Citizen. If asked, say you only know what you witnessed.\n");
        sb.append("- Only reference the facts below that you personally witnessed. Invent nothing.\n");
        sb.append("- If you saw a killing, you did NOT see who did it. You may only say who was ")
                .append("nearby, as a list of possibilities — never accuse one specific person.\n\n");

        sb.append("What you witnessed:\n");
        if (shareable.isEmpty()) {
            sb.append("- (you recall nothing relevant)\n");
        } else {
            for (Observation o : shareable) {
                sb.append("- ").append(factLine(o)).append('\n');
            }
        }

        sb.append("\nQuestion topic: ").append(topic).append('\n');
        sb.append("Answer in 1-2 sentences, in character.");
        return sb.toString();
    }

    /** Deterministic fallback text when no LLM is available. */
    public String fallbackAnswer(NPC npc, List<Observation> shareable) {
        if (shareable.isEmpty()) {
            return npc.displayName() + " shrugs. \"I didn't see anything about that.\"";
        }
        StringBuilder sb = new StringBuilder(npc.displayName()).append(": ");
        for (int i = 0; i < shareable.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(sentenceFor(shareable.get(i)));
        }
        return sb.toString();
    }

    private String factLine(Observation o) {
        if (o.isDeath()) {
            String base = o.subject() + " was attacked at " + o.locationId();
            if (o.suspects().isEmpty()) return base + " (no one else was around)";
            return base + " — nearby: " + String.join(", ", o.suspects());
        }
        return o.subject() + " " + o.action() + " at " + o.locationId();
    }

    private String sentenceFor(Observation o) {
        if (o.isDeath()) {
            String base = "I saw " + o.subject() + " struck down at " + o.locationId() + ".";
            if (o.suspects().isEmpty()) {
                return base + " I couldn't see anyone else about.";
            }
            return base + " I couldn't tell who did it, but it could've been one of "
                    + String.join(", ", o.suspects()) + ".";
        }
        return "I saw " + o.subject() + " " + o.action() + " at " + o.locationId() + ".";
    }
}
