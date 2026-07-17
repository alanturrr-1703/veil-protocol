package com.veil.api.DTOs;

import com.veil.domain.player.Player;
import com.veil.engine.GameContext;
import com.veil.phases.GamePhase;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The redaction boundary. This is the single place allowed to read from PrivateState,
 * and it only ever copies the slice belonging to the requesting viewer into a
 * {@link PlayerView}. If it compiles and only produces PlayerViews, confidential state
 * cannot be broadcast by accident.
 */
public final class DtoAssembler {

    private DtoAssembler() {}

    public static PlayerView forViewer(GameContext ctx, GamePhase phase, String viewerId) {
        Map<String, Boolean> roster = new LinkedHashMap<>();
        for (Player p : ctx.players().values()) {
            roster.put(p.id(), p.status().isAlive());
        }

        Player viewer = ctx.players().get(viewerId);
        String ownRole = viewer == null ? "UNKNOWN" : viewer.role().role().name();

        return new PlayerView(
                viewerId,
                phase == null ? "NONE" : phase.type().name(),
                ctx.publicState().announcements(),
                roster,
                ownRole,                                        // only the viewer's own role
                ctx.privateState().investigationResults(viewerId), // only the viewer's results
                ctx.privateState().npcAnswers(viewerId)            // only the viewer's answers
        );
    }
}
