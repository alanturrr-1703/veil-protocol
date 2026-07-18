package com.veil.api.DTOs;

import com.veil.chat.ChatChannel;
import com.veil.chat.ChatMessage;
import com.veil.chat.ChatPolicy;
import com.veil.domain.npc.NPC;
import com.veil.domain.player.Player;
import com.veil.domain.player.Role;
import com.veil.domain.world.Location;
import com.veil.engine.GameContext;
import com.veil.events.AttackFx;
import com.veil.phases.GamePhase;
import com.veil.phases.GamePhaseType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The redaction boundary. The single place allowed to read from PrivateState and from the
 * raw world occupancy, it copies out ONLY what the requesting viewer is authorized to see:
 * their own confidential slice, plus the whereabouts of players/NPCs who share their exact
 * room. Everyone else is reduced to an anonymous per-district head-count.
 */
public final class DtoAssembler {

    private DtoAssembler() {}

    public static PlayerView forViewer(GameContext ctx, GamePhase phase, String viewerId,
                                       Set<String> humanIds, long phaseEndsAt) {
        Map<String, Boolean> roster = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Player p : ctx.players().values()) {
            roster.put(p.id(), p.status().isAlive());
            names.put(p.id(), p.displayName());
        }

        // Public but anonymous: how many living operatives are in each district.
        Map<String, Integer> districtCounts = new LinkedHashMap<>();
        for (Location loc : ctx.city().locations().values()) districtCounts.put(loc.id(), 0);
        for (Player p : ctx.players().values()) {
            if (p.status().isAlive()) districtCounts.merge(p.locationId(), 1, Integer::sum);
        }

        Player viewer = ctx.players().get(viewerId);
        String ownRole = viewer == null ? "UNKNOWN" : viewer.role().role().name();
        String viewerDistrict = viewer == null ? null : viewer.locationId();
        String viewerRoom = viewer == null ? null : viewer.roomId();

        // Rooms of the viewer's current district (so the UI can offer doors to slip through).
        Map<String, String> rooms = new LinkedHashMap<>();
        Location here = viewerDistrict == null ? null : ctx.city().location(viewerDistrict);
        if (here != null) rooms.putAll(here.rooms());

        // VISIBLE players: those sharing the viewer's exact (district, room). Includes self.
        Map<String, String> positions = new LinkedHashMap<>();
        Map<String, double[]> coords = new LinkedHashMap<>();
        Map<String, String> npcsHere = new LinkedHashMap<>();
        if (viewer != null && viewer.status().isAlive()) {
            for (Player p : ctx.players().values()) {
                if (!p.status().isAlive()) continue;
                if (sameRoom(viewer, p)) {
                    positions.put(p.id(), p.locationId());
                    coords.put(p.id(), new double[]{p.x(), p.y()});
                }
            }
            for (NPC n : ctx.npcs().values()) {
                if (n.isAlive() && sameRoom(viewer, n.locationId(), n.roomId())) {
                    npcsHere.put(n.id(), n.displayName());
                }
            }
        }

        // Chat is confidential (PrivateState). It only leaves the engine here, filtered
        // through ChatPolicy — the SAME class the engine uses to police posting.
        Role viewerRole = viewer == null ? null : viewer.role().role();
        boolean viewerAlive = viewer != null && viewer.status().isAlive();
        GamePhaseType phaseType = phase == null ? GamePhaseType.LOBBY : phase.type();

        List<ChatMessage> readableChat = new ArrayList<>();
        for (ChatMessage m : ctx.privateState().chatLog()) {
            boolean visible = m.channel() == ChatChannel.DIRECT
                    // a whisper reaches only its two ends
                    ? (viewerId.equals(m.senderId()) || viewerId.equals(m.toId()))
                    : ChatPolicy.canRead(viewerRole, viewerAlive, m.channel());
            if (visible) readableChat.add(m);
        }
        Set<ChatChannel> postableChannels =
                ChatPolicy.postableChannels(viewerRole, viewerAlive, phaseType);

        // Live strikes the viewer can witness: only those in the viewer's exact room. Because
        // you were there, you also see who struck (the attacker is included). Killing with an
        // audience is how a Shadow gets caught.
        List<AttackFx> roomAttacks = new ArrayList<>();
        if (viewer != null && viewer.status().isAlive()) {
            for (AttackFx fx : ctx.privateState().attackFx()) {
                if (sameRoom(viewer, fx.district(), fx.room())) roomAttacks.add(fx);
            }
        }
        // Public dawn reveal: who fell last night (everyone sees this, never who struck).
        List<String> lastNightVictims = new ArrayList<>(ctx.publicState().lastNightVictims());
        boolean relocateAvailable = viewer != null && viewer.status().isAlive() && viewer.relocateAvailable();

        return new PlayerView(
                viewerId,
                phase == null ? "NONE" : phase.type().name(),
                phaseEndsAt,
                ctx.publicState().announcements(),
                roster,
                names,
                humanIds == null ? Set.of() : Set.copyOf(humanIds),
                districtCounts,
                viewerDistrict,
                viewerRoom,
                rooms,
                positions,                                          // only co-located players
                coords,                                             // their live x,y in the room
                npcsHere,                                           // only co-located NPCs
                ownRole,
                ctx.privateState().investigationResults(viewerId),
                ctx.privateState().npcAnswers(viewerId),
                readableChat,
                postableChannels,
                roomAttacks,
                lastNightVictims,
                relocateAvailable
        );
    }

    private static boolean sameRoom(Player viewer, Player other) {
        return sameRoom(viewer, other.locationId(), other.roomId());
    }

    private static boolean sameRoom(Player viewer, String districtId, String roomId) {
        return viewer.locationId() != null
                && viewer.locationId().equals(districtId)
                && viewer.roomId().equals(roomId);
    }
}
