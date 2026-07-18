package com.veil.api.DTOs;

import com.veil.chat.ChatChannel;
import com.veil.chat.ChatMessage;
import com.veil.domain.npc.Observation;
import com.veil.domain.player.Faction;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ONLY shape ever sent to a player client. It carries public state plus exactly
 * the confidential slice that belongs to this one viewer. Domain objects (Player,
 * NPC, PrivateState) never cross this boundary, so roles and memories cannot leak.
 *
 * @param viewerId            who this view was built for
 * @param phase               current phase name (public)
 * @param announcements       public announcements (public)
 * @param roster              playerId -> alive? (public)
 * @param names               playerId -> display name (public)
 * @param positions           playerId -> locationId (public; movement is public state)
 * @param humans              playerIds controlled by a human (public; the rest are AI)
 * @param ownRole             THIS viewer's own role (confidential to them)
 * @param ownInvestigations   THIS viewer's investigation results (confidential to them)
 * @param ownNpcAnswers       THIS viewer's NPC answers (confidential to them)
 * @param readableChat        chat lines THIS viewer may see (already filtered by ChatPolicy)
 * @param postableChannels    channels THIS viewer may post to right now
 */
public record PlayerView(
        String viewerId,
        String phase,
        List<String> announcements,
        Map<String, Boolean> roster,
        Map<String, String> names,
        Map<String, String> positions,
        Set<String> humans,
        String ownRole,
        Map<String, Faction> ownInvestigations,
        Map<String, List<Observation>> ownNpcAnswers,
        List<ChatMessage> readableChat,
        Set<ChatChannel> postableChannels
) {}
