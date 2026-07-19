package com.veil.ai.agent;

import com.veil.ai.OllamaClient;
import com.veil.api.session.GameSession;
import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Observation;
import com.veil.domain.player.Player;
import com.veil.engine.GameContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ONLY AI in Veil Protocol: it phrases what an NPC witness says. Operatives are all
 * humans — the AI never plays a role, takes a night action, chats, or votes.
 *
 * <p>The engine decides the FACTS (the observations an NPC actually witnessed, already scoped
 * to the asker); Ollama only phrases them. Because the facts are deterministic, two players
 * asking the same NPC the same question get the same answer. The model is given ONLY those
 * observations — never whole game state — and is guard-railed so it can't invent facts,
 * accuse anyone of being a Shadow, reveal a role, or name a killer with certainty.
 */
@Service
public class AiEngine {

    private final OllamaClient ollama = new OllamaClient("http://localhost:11434", "llama3.2:3b");
    // LLM calls are slow; run them off the engine thread so play never blocks on the model.
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    /**
     * A player talks to an NPC in their room. Records the question, then (asynchronously)
     * phrases the NPC's reply from what it truly saw and records that too — both as private
     * DIRECT lines between the asker and the NPC, so the dialogue reads like a chat.
     */
    public void npcReply(GameSession session, String askerId, String npcId, String topic) {
        GameContext ctx = session.context();
        Player asker = ctx.players().get(askerId);
        NPC npc = ctx.npcs().get(npcId);
        if (asker == null || npc == null || !npc.isAlive() || !asker.status().isAlive()) return;
        if (!sameRoomAsNpc(asker, npc)) return; // must be face to face

        String question = topic == null || topic.isBlank() ? "what did you see" : topic.trim();
        session.postNpcLine(askerId, asker.displayName(), npcId, "(to " + npc.displayName() + ") " + question);

        List<Observation> shared = npc.recall(question, askerId);
        pool.submit(() -> {
            String reply = npcSpeak(ctx, npc, question, shared);
            session.postNpcLine(npcId, npc.displayName(), askerId, reply);
        });
    }

    private String npcSpeak(GameContext ctx, NPC npc, String question, List<Observation> shared) {
        StringBuilder facts = new StringBuilder();
        for (Observation o : shared) facts.append("- ").append(describeObs(ctx, o)).append("\n");

        String fallback = shared.isEmpty()
                ? "I didn't see anything about that."
                : "I saw " + describeObs(ctx, shared.get(0)) + ". That's all I can say for sure.";

        String prompt = "You are " + npc.displayName() + ", a wary street NPC in Neon City. You are NOT a player "
                + "and have no role. You only know what you personally witnessed. NEVER invent facts, NEVER "
                + "accuse anyone of being a Shadow, NEVER claim to know a person's role, and NEVER name a single "
                + "killer with certainty (the most you saw is who was NEARBY).\n"
                + "Someone asks you: \"" + question + "\".\n"
                + (facts.length() == 0
                        ? "You did not witness anything relevant.\n"
                        : "What you actually saw:\n" + facts)
                + "Answer in ONE or TWO short in-character sentences using ONLY what you saw. "
                + "Reply with only the words you say.";
        return oneLine(ollama.generate(prompt, fallback, 80, 0.7));
    }

    private String describeObs(GameContext ctx, Observation o) {
        String subject = nameFor(ctx, o.subject());
        String loc = o.locationId() == null ? "the area" : o.locationId();
        StringBuilder sb = new StringBuilder(subject + " " + o.action() + " at " + loc);
        if (!o.suspects().isEmpty()) {
            List<String> who = new java.util.ArrayList<>();
            for (String s : o.suspects()) who.add(nameFor(ctx, s));
            sb.append("; nearby were ").append(String.join(", ", who));
        }
        return sb.toString();
    }

    private String nameFor(GameContext ctx, String id) {
        Player p = id == null ? null : ctx.players().get(id);
        return p != null ? p.displayName() : (id == null ? "someone" : id);
    }

    private boolean sameRoomAsNpc(Player p, NPC npc) {
        return p.locationId() != null
                && p.locationId().equals(npc.locationId())
                && p.roomId().equals(npc.roomId());
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\"", "").trim();
        return t.length() > 180 ? t.substring(0, 180) : t;
    }
}
