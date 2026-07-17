package com.veil;

import com.veil.ai.NPCAgent;
import com.veil.ai.OllamaClient;
import com.veil.ai.PromptBuilder;
import com.veil.api.DTOs.DtoAssembler;
import com.veil.api.DTOs.PlayerView;
import com.veil.domain.action.AttackAction;
import com.veil.domain.action.InvestigateAction;
import com.veil.domain.action.QueryNPCAction;
import com.veil.domain.action.ShieldAction;
import com.veil.domain.npc.NPC;
import com.veil.domain.npc.Personality;
import com.veil.domain.player.Player;
import com.veil.domain.roles.AegisRole;
import com.veil.domain.roles.CitizenRole;
import com.veil.domain.roles.OracleRole;
import com.veil.domain.roles.ShadowRole;
import com.veil.domain.world.City;
import com.veil.domain.world.Location;
import com.veil.domain.world.Position;
import com.veil.engine.GameContext;
import com.veil.engine.VeilEngine;
import com.veil.events.Visibility;
import com.veil.phases.GamePhaseType;

/**
 * A tiny scripted match that exercises a full Night -> Day -> Voting -> Night cycle
 * and prints the public narration plus one redacted per-viewer snapshot.
 */
public final class Demo {

    public static void main(String[] args) {
        City city = new City();
        city.addLocation(new Location("plaza", "Neon Plaza", new Position(0, 0)));
        city.addLocation(new Location("docks", "Rust Docks", new Position(5, 0)));
        city.connect("plaza", "docks");

        GameContext ctx = new GameContext(city, 42L);
        ctx.addPlayer(new Player("p1", "Vex", new ShadowRole(), "plaza"));
        ctx.addPlayer(new Player("p2", "Mara", new AegisRole(), "plaza"));
        ctx.addPlayer(new Player("p3", "Ilya", new OracleRole(), "plaza"));
        ctx.addPlayer(new Player("p4", "Dax", new CitizenRole(), "plaza"));
        ctx.addNpc(new NPC("n1", "Old Kesh", Personality.neutral(), "plaza"));

        VeilEngine engine = new VeilEngine(ctx);

        // Narrator (Observer): print public events; keep private ones off the public feed.
        engine.addListener(e -> {
            String tag = e.visibility() == Visibility.PUBLIC ? "PUBLIC " : "private";
            System.out.println("  [event " + tag + "] " + e.describe());
        });

        System.out.println("=== NIGHT 1 ===");
        engine.start();
        engine.submit(new ShieldAction("p2", "p3"));        // Aegis shields the Oracle
        engine.submit(new InvestigateAction("p3", "p1"));   // Oracle investigates the Shadow
        engine.submit(new AttackAction("p1", "p4"));        // Shadow attacks the Citizen
        engine.advancePhase();                              // resolve night -> Day

        System.out.println("=== DAY 1 ===");
        engine.submit(new QueryNPCAction("p3", "n1", "p4", GamePhaseType.DAY)); // ask witness about Dax
        engine.advancePhase();                              // Day -> Voting

        System.out.println("=== VOTING 1 ===");
        engine.castVote("p2", "p1");
        engine.castVote("p3", "p1");                        // city exiles the Shadow
        engine.advancePhase();                              // resolve votes -> Night 2

        // Redacted view for the Oracle: sees own role + own investigation, not others'.
        PlayerView oracleView = DtoAssembler.forViewer(ctx, engine.currentPhase(), "p3");
        System.out.println("\n=== ORACLE'S REDACTED VIEW ===");
        System.out.println("  role: " + oracleView.ownRole());
        System.out.println("  roster: " + oracleView.roster());
        System.out.println("  investigations: " + oracleView.ownInvestigations());
        System.out.println("  npc answers: " + oracleView.ownNpcAnswers());

        // NPC dialogue via the AI layer (falls back to deterministic text with no Ollama).
        NPCAgent agent = new NPCAgent(new OllamaClient(), new PromptBuilder());
        String reply = agent.answer(ctx.npcs().get("n1"), "p3", "p4");
        System.out.println("\n=== NPC DIALOGUE ===\n  " + reply);
    }
}
