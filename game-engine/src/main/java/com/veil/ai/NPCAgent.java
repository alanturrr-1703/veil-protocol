package com.veil.ai;

import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Observation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an NPC's shareable memories into a natural-language reply. Facts come from
 * {@link NPC#recall} (deterministic); the LLM only adds phrasing. Answers are cached
 * by (npcId, asker, topic, memoryVersion) so identical questions stay consistent and
 * cheap, and the cache invalidates automatically when the NPC learns something new.
 */
public class NPCAgent {

    private final OllamaClient ollama;
    private final PromptBuilder promptBuilder;
    private final Map<String, String> cache = new HashMap<>();

    public NPCAgent(OllamaClient ollama, PromptBuilder promptBuilder) {
        this.ollama = ollama;
        this.promptBuilder = promptBuilder;
    }

    public String answer(NPC npc, String askerId, String topic) {
        List<Observation> shareable = npc.recall(topic, askerId);
        String key = npc.id() + "|" + askerId + "|" + topic + "|v" + npc.memory().version();

        String cached = cache.get(key);
        if (cached != null) return cached;

        String prompt = promptBuilder.build(npc, topic, shareable);
        String fallback = promptBuilder.fallbackAnswer(npc, shareable);
        String reply = ollama.generate(prompt, fallback);

        cache.put(key, reply);
        return reply;
    }
}
