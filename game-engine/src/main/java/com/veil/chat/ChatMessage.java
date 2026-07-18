package com.veil.chat;

import com.veil.phases.GamePhaseType;

/**
 * One immutable chat line. Held in {@link com.veil.state.PrivateState} (confidential):
 * a SHADOW or DEAD line must never reach the wrong viewer, so messages only leave the
 * engine after {@link ChatPolicy} filtering inside the redaction boundary.
 *
 * <p>Ordering is deterministic for replay: {@code tick} is the engine's logical clock
 * (never wall-clock) and {@code seq} is a monotonic counter that breaks ties within a
 * single tick, giving a stable total order.
 *
 * @param senderId       author's player id, or {@code "SYSTEM"} for narrator lines
 * @param senderName     author's display name (or {@code "Narrator"} for SYSTEM)
 * @param channel        which channel this was posted to
 * @param phaseWhenSent  the phase active when the message was posted
 * @param tick           logical tick at post time (deterministic, not wall-clock)
 * @param seq            monotonic sequence for stable intra-tick ordering
 * @param text           the message body
 */
public record ChatMessage(
        String senderId,
        String senderName,
        ChatChannel channel,
        GamePhaseType phaseWhenSent,
        long tick,
        long seq,
        String text
) {
    /** Sender id used for narrator / announcement lines. */
    public static final String SYSTEM_SENDER = "SYSTEM";
}
