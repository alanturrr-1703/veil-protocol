package com.veil.ai.agent;

import com.veil.ai.OllamaClient;
import com.veil.api.session.GameSession;
import com.veil.chat.ChatChannel;
import com.veil.chat.ChatMessage;
import com.veil.chat.ChatPolicy;
import com.veil.domain.action.AttackAction;
import com.veil.domain.action.InvestigateAction;
import com.veil.domain.action.ShieldAction;
import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Observation;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.domain.player.Role;
import com.veil.engine.GameContext;
import com.veil.phases.GamePhaseType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives every seat the human did NOT claim. It acts AS those operatives — so it may
 * read their own roles (server-side, never sent to a client) — and funnels every decision
 * back through the same authoritative {@link GameSession} a human would use, so the engine
 * still validates phase + role legality.
 *
 * <ul>
 *   <li>NIGHT — Shadows coordinate one kill; the Oracle investigates; the Aegis shields.
 *       (Fast, deterministic — no model call, so the night resolves instantly.)</li>
 *   <li>DAY / VOTING — each AI operative speaks and votes via Ollama, with a heuristic
 *       fallback, so a match is playable even with the model offline.</li>
 * </ul>
 *
 * Confidentiality: a prompt only ever contains that operative's OWN role (and, for a
 * Shadow, their partner). No AI is ever told anyone else's hidden role.
 */
@Service
public class AiEngine {

    private final OllamaClient ollama = new OllamaClient("http://localhost:11434", "llama3.2:3b");
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    /**
     * The rules every AI operative is made aware of — a compressed form of docs/game-rules.md
     * so the model actually knows what it is doing, what phase it's in, and how to win.
     */
    private static final String RULES_BRIEF =
            "GAME: Veil Protocol, a social-deduction game in Neon City. 8 operatives; 2 are secret SHADOWS "
            + "(mafia), 6 are CITY (Oracle, Aegis, Citizens). Phases loop: NIGHT (45s, Shadows secretly kill "
            + "one, Oracle learns one player's faction, Aegis shields one), DAY (60s, open discussion), VOTE "
            + "(30s, exile one suspect). CITY wins when all Shadows are dead; SHADOWS win when they equal or "
            + "outnumber the living City. You only see people in your own room; districts show only a head-count. "
            + "NPCs are witnesses who never lie and never know roles. Never reveal your own secret role outright.";

    /** Run the AI's turn for whatever phase the session just entered. */
    public void runPhase(GameSession session) {
        GamePhaseType phase = session.phaseType();
        switch (phase) {
            case NIGHT -> runNight(session);
            case DAY -> runDayChatter(session);
            case VOTING -> runVoting(session);
            default -> { /* LOBBY / others: nothing to do */ }
        }
    }

    // --- NIGHT: fast, coordinated, no model call --------------------------------

    private void runNight(GameSession session) {
        GameContext ctx = session.context();

        // The Oracle and Aegis act at nightfall (their effects don't depend on where anyone
        // stands). The Shadows' kill is deliberately deferred to LATE in the night — see
        // runNightStrike — so a victim who is paying attention can still dodge away.
        for (Player p : aliveAiPlayers(session)) {
            Role role = p.role().role();
            if (role == Role.ORACLE) {
                Player target = pickInvestigation(session, ctx, p);
                if (target != null) session.submit(new InvestigateAction(p.id(), target.id()));
            } else if (role == Role.AEGIS) {
                List<Player> others = new ArrayList<>(allAlive(ctx));
                others.removeIf(o -> o.id().equals(p.id()));
                if (!others.isEmpty()) {
                    Player shield = others.get(ctx.rng().nextInt(others.size()));
                    session.submit(new ShieldAction(p.id(), shield.id()));
                }
            }
        }
    }

    /**
     * The Shadows' kill, fired only in the closing seconds of the night. A Shadow can strike
     * only in its OWN room, so it closes the distance — teleporting to reach the victim's
     * district if needed — slips into their room, and strikes. Because this happens late, a
     * human target who spots the strike (or has already fled) can still escape by ducking into
     * another room before dawn; the kill is re-validated at resolution. The AI prefers a
     * victim who is alone, since killing in front of witnesses exposes it.
     */
    public void runNightStrike(GameSession session) {
        if (session.phaseType() != GamePhaseType.NIGHT) return;
        GameContext ctx = session.context();

        List<Player> shadows = new ArrayList<>();
        List<Player> cityTargets = new ArrayList<>();
        for (Player p : allAlive(ctx)) {
            if (p.role().role().faction() == Faction.SHADOW) shadows.add(p);
            else cityTargets.add(p);
        }
        Player aiShadow = firstAi(session, shadows);
        if (aiShadow == null || cityTargets.isEmpty()) return;

        Player victim = pickVictim(ctx, cityTargets);
        if (!sameRoom(aiShadow, victim)) {
            if (!aiShadow.locationId().equals(victim.locationId())) {
                session.teleport(aiShadow.id(), victim.locationId());
            }
            session.enterRoom(aiShadow.id(), victim.roomId());
        }
        session.submit(new AttackAction(aiShadow.id(), victim.id()));
    }

    /** Prefer a victim who is alone in their room (quiet kill); otherwise anyone. */
    private Player pickVictim(GameContext ctx, List<Player> cityTargets) {
        List<Player> alone = new ArrayList<>();
        for (Player t : cityTargets) {
            boolean hasCompany = false;
            for (Player other : allAlive(ctx)) {
                if (!other.id().equals(t.id()) && sameRoom(other, t)
                        && other.role().role().faction() != Faction.SHADOW) {
                    hasCompany = true;
                    break;
                }
            }
            if (!hasCompany) alone.add(t);
        }
        List<Player> pool = alone.isEmpty() ? cityTargets : alone;
        return pool.get(ctx.rng().nextInt(pool.size()));
    }

    private boolean sameRoom(Player a, Player b) {
        return a.locationId() != null
                && a.locationId().equals(b.locationId())
                && a.roomId().equals(b.roomId());
    }

    private Player pickInvestigation(GameSession session, GameContext ctx, Player oracle) {
        var alreadyRead = ctx.privateState().investigationResults(oracle.id()).keySet();
        List<Player> candidates = new ArrayList<>();
        for (Player p : allAlive(ctx)) {
            if (p.id().equals(oracle.id())) continue;
            if (alreadyRead.contains(p.id())) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(ctx.rng().nextInt(candidates.size()));
    }

    // --- DAY: each AI operative says one line (Ollama, async) -------------------

    private void runDayChatter(GameSession session) {
        for (Player p : aliveAiPlayers(session)) {
            pool.submit(() -> {
                String line = daySpeech(session, p);
                session.postChat(p.id(), ChatChannel.DAY, line);
            });
        }
    }

    private String daySpeech(GameSession session, Player self) {
        String fallback = fallbackDayLine(session, self);
        String prompt = RULES_BRIEF + "\n\n" + personaHeader(session, self)
                + "\nIt is DAY. Push your faction's agenda: if City, hunt the Shadows; if Shadow, deflect."
                + "\n" + publicContext(session)
                + "\n" + recentChat(session, ChatChannel.DAY, self)
                + "\nSpeak ONE short in-character line (max 25 words). Do NOT reveal your real role. "
                + "Reply with only the line, no quotes.";
        return oneLine(ollama.generate(prompt, fallback, 60, 0.9));
    }

    // --- Reactive conversation: AI answers humans in chat and in whispers -------

    /**
     * A human just spoke on {@code channel}. Have one or two eligible AI operatives reply in
     * character, so the chat is an actual back-and-forth. Only human lines trigger this, so
     * AI replies never cascade into an endless AI-to-AI loop.
     */
    public void reactToChat(GameSession session, String senderId, ChatChannel channel, String text) {
        if (channel != ChatChannel.DAY && channel != ChatChannel.SHADOW) return;
        if (!session.isHuman(senderId)) return;
        Player sender = session.context().players().get(senderId);
        if (sender == null || text == null || text.isBlank()) return;

        List<Player> responders = new ArrayList<>();
        for (Player p : aliveAiPlayers(session)) {
            if (p.id().equals(senderId)) continue;
            if (ChatPolicy.canPost(p.role().role(), true, session.phaseType(), channel)) responders.add(p);
        }
        Collections.shuffle(responders, ThreadLocalRandom.current());
        int n = Math.min(2, responders.size());
        for (int i = 0; i < n; i++) {
            Player responder = responders.get(i);
            pool.submit(() -> {
                sleepJitter(300, 1400);
                String reply = chatReply(session, responder, channel, sender.displayName(), text);
                if (!reply.isBlank()) session.postChat(responder.id(), channel, reply);
            });
        }
    }

    /** A human whispered an AI operative in the same room; the AI whispers back. */
    public void reactToWhisper(GameSession session, String fromId, String toId, String text) {
        if (session.isHuman(toId)) return;
        Player from = session.context().players().get(fromId);
        Player to = session.context().players().get(toId);
        if (from == null || to == null || !to.status().isAlive() || text == null || text.isBlank()) return;
        pool.submit(() -> {
            sleepJitter(300, 1200);
            String reply = whisperReply(session, to, from.displayName(), text);
            if (!reply.isBlank()) session.postWhisper(to.id(), from.id(), reply);
        });
    }

    private String chatReply(GameSession session, Player self, ChatChannel channel, String speaker, String said) {
        String where = channel == ChatChannel.SHADOW ? "the private SHADOW channel" : "the DAY city chat";
        String prompt = RULES_BRIEF + "\n\n" + personaHeader(session, self)
                + "\nYou are talking on " + where + ". " + publicContext(session)
                + "\n" + recentChat(session, channel, self)
                + "\n" + speaker + " just said to the group: \"" + said + "\"."
                + "\nReply directly to them in ONE short in-character line (max 25 words). "
                + "Do NOT reveal your real role. Reply with only the line, no quotes.";
        return oneLine(ollama.generate(prompt, "", 60, 0.9));
    }

    private String whisperReply(GameSession session, Player self, String speaker, String said) {
        String prompt = RULES_BRIEF + "\n\n" + personaHeader(session, self)
                + "\n" + speaker + " whispers to you privately: \"" + said + "\"."
                + "\nWhisper back ONE short, candid in-character line (max 25 words). "
                + "You may share or trade information quietly, but never bluntly announce your role. "
                + "Reply with only the words you whisper.";
        return oneLine(ollama.generate(prompt, "Not here. Later.", 50, 0.9));
    }

    // --- VOTING: each AI operative announces + casts a vote (Ollama, async) -----

    private void runVoting(GameSession session) {
        GameContext ctx = session.context();
        for (Player voter : aliveAiPlayers(session)) {
            pool.submit(() -> {
                List<Player> candidates = voteCandidates(ctx, voter);
                if (candidates.isEmpty()) return;
                Player choice = chooseVote(session, voter, candidates);
                session.postChat(voter.id(), ChatChannel.DAY,
                        "I vote to exile " + choice.displayName() + ".");
                session.vote(voter.id(), choice.id());
            });
        }
    }

    private List<Player> voteCandidates(GameContext ctx, Player voter) {
        boolean voterIsShadow = voter.role().role().faction() == Faction.SHADOW;
        List<Player> out = new ArrayList<>();
        for (Player p : allAlive(ctx)) {
            if (p.id().equals(voter.id())) continue;
            // Shadows never vote to exile their own partner.
            if (voterIsShadow && p.role().role().faction() == Faction.SHADOW) continue;
            out.add(p);
        }
        return out;
    }

    private Player chooseVote(GameSession session, Player voter, List<Player> candidates) {
        List<String> names = new ArrayList<>();
        for (Player c : candidates) names.add(c.displayName());
        String prompt = RULES_BRIEF + "\n\n" + personaHeader(session, voter)
                + "\nIt is the VOTE. Choose exactly one operative to exile from: "
                + String.join(", ", names) + "."
                + "\n" + publicContext(session)
                + "\n" + recentChat(session, ChatChannel.DAY, voter)
                + "\nReply with ONLY the chosen name, nothing else.";
        String reply = ollama.generate(prompt, "", 12, 0.7).toLowerCase(Locale.ROOT);

        for (Player c : candidates) {
            if (!reply.isBlank() && reply.contains(c.displayName().toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        // Fallback: random suspect.
        return candidates.get(session.context().rng().nextInt(candidates.size()));
    }

    // --- NPC conversation: a witness answers in natural language (never lying) ---

    /**
     * A player talks to an NPC in their room. The NPC replies in natural language grounded
     * ONLY in what it actually witnessed — the model phrases the answer but never decides the
     * facts, so the NPC cannot lie, name a certain killer, or reveal a role. Both the question
     * and the reply are stored as a private (asker-only) line so the dialogue reads as a chat.
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
            List<String> who = new ArrayList<>();
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

    private void sleepJitter(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + ThreadLocalRandom.current().nextInt(Math.max(1, maxMs - minMs)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Recent lines on a channel this operative can read, for conversational context. */
    private String recentChat(GameSession session, ChatChannel channel, Player self) {
        boolean alive = self.status().isAlive();
        Role role = self.role().role();
        List<String> lines = new ArrayList<>();
        List<ChatMessage> log = session.context().privateState().chatLog();
        for (ChatMessage m : log) {
            if (m.channel() != channel) continue;
            if (!ChatPolicy.canRead(role, alive, m.channel())) continue;
            lines.add(m.senderName() + ": " + m.text());
        }
        if (lines.isEmpty()) return "No one has spoken yet.";
        int from = Math.max(0, lines.size() - 5);
        return "Recent chat:\n" + String.join("\n", lines.subList(from, lines.size()));
    }

    // --- Prompt building (confidential-safe) ------------------------------------

    private String personaHeader(GameSession session, Player self) {
        Role role = self.role().role();
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(self.displayName())
                .append(", an operative in the cyberpunk city of Neon City. Stay in character, terse and street-smart.");
        sb.append("\nYour SECRET role is ").append(role.name()).append(" (never state it outright).");
        if (role.faction() == Faction.SHADOW) {
            String partner = shadowPartnerName(session, self);
            sb.append(" You are one of the Shadows (mafia). ")
                    .append(partner == null ? "" : "Your partner is " + partner + " — protect them and deflect suspicion.");
        } else if (role == Role.ORACLE) {
            sb.append(" You can privately read a person's faction each night; hint carefully without exposing yourself.");
        } else if (role == Role.AEGIS) {
            sb.append(" You secretly protect someone each night.");
        } else {
            sb.append(" You are an ordinary citizen trying to find the Shadows.");
        }
        return sb.toString();
    }

    private String shadowPartnerName(GameSession session, Player self) {
        for (Player p : allAlive(session.context())) {
            if (!p.id().equals(self.id()) && p.role().role().faction() == Faction.SHADOW) {
                return p.displayName();
            }
        }
        return null;
    }

    private String publicContext(GameSession session) {
        GameContext ctx = session.context();
        List<String> living = new ArrayList<>();
        for (Player p : allAlive(ctx)) living.add(p.displayName());
        List<String> ann = ctx.publicState().announcements();
        String recent = ann.isEmpty() ? "nothing yet"
                : String.join(" | ", ann.subList(Math.max(0, ann.size() - 3), ann.size()));
        return "Still alive: " + String.join(", ", living) + ". Recent events: " + recent + ".";
    }

    // --- Fallbacks (model offline) ----------------------------------------------

    private String fallbackDayLine(GameSession session, Player self) {
        String[] lines = {
                "Keep your eyes open. Someone in this crowd isn't who they say.",
                "The docks were quiet last night. Too quiet.",
                "I don't trust anyone who stays silent.",
                "We need to think before we start pointing fingers.",
                "Watch the ones moving between districts after dark.",
        };
        return self.displayName() + ": " + lines[session.context().rng().nextInt(lines.length)];
    }

    // --- helpers ----------------------------------------------------------------

    private List<Player> allAlive(GameContext ctx) {
        List<Player> out = new ArrayList<>();
        for (Player p : ctx.players().values()) if (p.status().isAlive()) out.add(p);
        return out;
    }

    private List<Player> aliveAiPlayers(GameSession session) {
        List<Player> out = new ArrayList<>();
        for (Player p : allAlive(session.context())) {
            if (!session.isHuman(p.id())) out.add(p);
        }
        return out;
    }

    private Player firstAi(GameSession session, List<Player> players) {
        for (Player p : players) if (!session.isHuman(p.id())) return p;
        return null;
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replace("\"", "").trim();
        return t.length() > 180 ? t.substring(0, 180) : t;
    }
}
