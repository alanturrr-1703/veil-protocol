package com.veil.chat;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;
import com.veil.phases.GamePhaseType;

import java.util.EnumSet;
import java.util.Set;

/**
 * The single source of truth for chat legality. BOTH sides call it:
 * <ul>
 *   <li>posting — {@code VeilEngine.postChat} calls {@link #canPost} before accepting a line;</li>
 *   <li>reading — {@code DtoAssembler} calls {@link #canRead} when filtering a viewer's feed.</li>
 * </ul>
 * Because send-rules and see-rules are derived from this one class, they cannot drift.
 * All membership (e.g. "is a living Shadow") is DERIVED from the passed-in live state —
 * nothing is stored — so a Shadow who dies silently loses SHADOW access on the next call.
 *
 * <p>Pure and side-effect free: every decision is a function of (role, alive, phase, channel).
 */
public final class ChatPolicy {

    private ChatPolicy() {}

    /**
     * May this viewer POST to {@code channel} right now?
     * <ul>
     *   <li>DAY    — a LIVING player, only during Day or Voting (never at Night).</li>
     *   <li>SHADOW — a LIVING Shadow, only at Night.</li>
     *   <li>DEAD   — only the eliminated (a channel the living can never read).</li>
     *   <li>SYSTEM — never a player; narrator lines are engine-authored.</li>
     * </ul>
     */
    public static boolean canPost(Role role, boolean alive, GamePhaseType phase, ChatChannel channel) {
        return switch (channel) {
            case DAY -> alive && (phase == GamePhaseType.DAY || phase == GamePhaseType.VOTING);
            case SHADOW -> alive && isShadow(role) && phase == GamePhaseType.NIGHT;
            case DEAD -> !alive;
            case SYSTEM -> false;
        };
    }

    /**
     * May this viewer READ messages on {@code channel}?
     * <ul>
     *   <li>SYSTEM — everyone (public narration).</li>
     *   <li>DAY    — everyone; the open city record (the dead may spectate, but the
     *                key invariant is that they cannot POST here — see {@link #canPost}).</li>
     *   <li>SHADOW — LIVING Shadows only; invisible to City and to the dead.</li>
     *   <li>DEAD   — the eliminated only; invisible to every living player.</li>
     * </ul>
     */
    public static boolean canRead(Role role, boolean alive, ChatChannel channel) {
        return switch (channel) {
            case SYSTEM, DAY -> true;
            case SHADOW -> alive && isShadow(role);
            case DEAD -> !alive;
        };
    }

    /** The set of channels this viewer may post to right now (drives PlayerView). */
    public static Set<ChatChannel> postableChannels(Role role, boolean alive, GamePhaseType phase) {
        EnumSet<ChatChannel> out = EnumSet.noneOf(ChatChannel.class);
        for (ChatChannel c : ChatChannel.values()) {
            if (canPost(role, alive, phase, c)) out.add(c);
        }
        return out;
    }

    private static boolean isShadow(Role role) {
        return role != null && role.faction() == Faction.SHADOW;
    }
}
