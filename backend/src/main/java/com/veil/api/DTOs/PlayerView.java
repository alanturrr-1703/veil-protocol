package com.veil.api.DTOs;

import com.veil.chat.ChatChannel;
import com.veil.chat.ChatMessage;
import com.veil.domain.npc.Observation;
import com.veil.domain.player.Faction;
import com.veil.events.AttackFx;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ONLY shape ever sent to a player client. It carries public state plus exactly the
 * confidential slice that belongs to this one viewer. Domain objects never cross this
 * boundary, so roles, memories — and now other operatives' exact whereabouts — cannot leak.
 *
 * <p><b>Location privacy:</b> who exists ({@code names}, {@code roster}) is public, but
 * WHERE they are is not. You only receive the identities of players/NPCs who share your
 * exact room ({@code positions}, {@code npcsHere}); everyone else is only visible as an
 * anonymous head-count per district ({@code districtCounts}). Duck into a side room and you
 * vanish from the commons.
 *
 * @param viewerId          who this view was built for
 * @param phase             current phase name (public)
 * @param phaseEndsAt       epoch millis the current phase auto-ends (0 = untimed)
 * @param announcements     public announcements
 * @param roster            playerId -> alive? (public)
 * @param names             playerId -> display name (public)
 * @param humans            human-controlled seats; the rest are AI
 * @param districtCounts    districtId -> living occupant count (public, anonymous)
 * @param viewerDistrict    the district THIS viewer is in
 * @param viewerRoom        the room THIS viewer is in
 * @param rooms             rooms of the viewer's current district: roomId -> name
 * @param positions         VISIBLE players (share the viewer's room): playerId -> districtId
 * @param coords            VISIBLE players' free-roam positions in the room: playerId -> [x,y]
 * @param npcsHere          NPCs sharing the viewer's room: npcId -> name
 * @param ownRole           THIS viewer's own role (confidential to them)
 * @param ownInvestigations THIS viewer's investigation results
 * @param ownNpcAnswers     THIS viewer's NPC answers
 * @param readableChat      chat lines THIS viewer may see (filtered by ChatPolicy)
 * @param postableChannels  channels THIS viewer may post to right now
 * @param roomAttacks       strikes happening in THIS viewer's room (witnessed live, attacker shown)
 * @param lastNightVictims  who fell last night — the public dawn reveal
 * @param relocateAvailable whether THIS viewer still has their one nightly district relocation
 */
public record PlayerView(
        String viewerId,
        String phase,
        long phaseEndsAt,
        List<String> announcements,
        Map<String, Boolean> roster,
        Map<String, String> names,
        Set<String> humans,
        Map<String, Integer> districtCounts,
        String viewerDistrict,
        String viewerRoom,
        Map<String, String> rooms,
        Map<String, String> positions,
        Map<String, double[]> coords,
        Map<String, String> npcsHere,
        String ownRole,
        Map<String, Faction> ownInvestigations,
        Map<String, List<Observation>> ownNpcAnswers,
        List<ChatMessage> readableChat,
        Set<ChatChannel> postableChannels,
        List<AttackFx> roomAttacks,
        List<String> lastNightVictims,
        boolean relocateAvailable
) {}
