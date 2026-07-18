package com.veil.ai.agent;

import com.veil.ai.OllamaClient;
import com.veil.api.session.GameSession;
import com.veil.chat.ChatChannel;
import com.veil.domain.action.AttackAction;
import com.veil.domain.action.InvestigateAction;
import com.veil.domain.action.ShieldAction;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Player;
import com.veil.domain.player.Role;
import com.veil.engine.GameContext;
import com.veil.phases.GamePhaseType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        List<Player> alive = aliveAiPlayers(session);

        List<Player> shadows = new ArrayList<>();
        List<Player> cityTargets = new ArrayList<>();
        for (Player p : allAlive(ctx)) {
            if (p.role().role().faction() == Faction.SHADOW) shadows.add(p);
            else cityTargets.add(p);
        }

        // Shadows (mafia) coordinate a single kill. If at least one shadow is AI, it files
        // the hit; if the human is the only shadow, they choose their own target in the UI.
        Player aiShadow = firstAi(session, shadows);
        if (aiShadow != null && !cityTargets.isEmpty()) {
            Player victim = cityTargets.get(ctx.rng().nextInt(cityTargets.size()));
            session.submit(new AttackAction(aiShadow.id(), victim.id()));
        }

        for (Player p : alive) {
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
        String prompt = personaHeader(session, self)
                + "\nIt is DAY in Neon City. Discuss who might be a Shadow (mafia)."
                + "\n" + publicContext(session)
                + "\nSpeak ONE short in-character line (max 25 words). Do NOT reveal your real role. "
                + "Reply with only the line, no quotes.";
        return oneLine(ollama.generate(prompt, fallback, 60, 0.9));
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
        String prompt = personaHeader(session, voter)
                + "\nIt is the VOTE. Choose exactly one operative to exile from: "
                + String.join(", ", names) + "."
                + "\n" + publicContext(session)
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
