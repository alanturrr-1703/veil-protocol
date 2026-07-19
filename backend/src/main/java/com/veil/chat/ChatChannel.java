package com.veil.chat;

/**
 * The four chat channels. Visibility and post-legality per channel are NOT encoded
 * here — they live in {@link ChatPolicy}, the single source of truth that both the
 * engine (send-rules) and the redaction boundary (see-rules) consult, so the two can
 * never drift apart.
 *
 * <ul>
 *   <li>{@link #DAY}    — the living city's open channel; postable in Day and Voting.</li>
 *   <li>{@link #SHADOW} — living Shadows only; postable at Night, invisible to City.</li>
 *   <li>{@link #DEAD}   — the eliminated; talk freely but invisible to the living.</li>
 *   <li>{@link #SYSTEM} — narrator / announcement lines; posted by the engine only.</li>
 *   <li>{@link #DIRECT} — a private whisper between two co-located players; only the
 *       sender and recipient ever see it (recipient carried on the message's {@code toId}).</li>
 * </ul>
 */
public enum ChatChannel {
    DAY,
    SHADOW,
    DEAD,
    SYSTEM,
    DIRECT
}
