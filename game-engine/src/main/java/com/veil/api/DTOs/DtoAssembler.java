package com.veil.api.DTOs;

import com.veil.chat.ChatChannel;
import com.veil.chat.ChatMessage;
import com.veil.chat.ChatPolicy;
import com.veil.domain.player.Player;
import com.veil.domain.player.Role;
import com.veil.engine.GameContext;
import com.veil.phases.GamePhase;
import com.veil.phases.GamePhaseType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        // Chat is confidential (PrivateState). It only leaves the engine here, filtered
        // through ChatPolicy — the SAME class the engine uses to police posting — so
        // what a viewer can send and what they can see are governed by one rule set.
        Role viewerRole = viewer == null ? null : viewer.role().role();
        boolean viewerAlive = viewer != null && viewer.status().isAlive();
        GamePhaseType phaseType = phase == null ? GamePhaseType.LOBBY : phase.type();

        List<ChatMessage> readableChat = new ArrayList<>();
        for (ChatMessage m : ctx.privateState().chatLog()) {
            if (ChatPolicy.canRead(viewerRole, viewerAlive, m.channel())) {
                readableChat.add(m);
            }
        }
        Set<ChatChannel> postableChannels =
                ChatPolicy.postableChannels(viewerRole, viewerAlive, phaseType);

        return new PlayerView(
                viewerId,
                phase == null ? "NONE" : phase.type().name(),
                ctx.publicState().announcements(),
                roster,
                ownRole,                                        // only the viewer's own role
                ctx.privateState().investigationResults(viewerId), // only the viewer's results
                ctx.privateState().npcAnswers(viewerId),           // only the viewer's answers
                readableChat,                                   // only channels they may read
                postableChannels                                // channels they may post to now
        );
    }
}
