package com.veil.state;

import com.veil.chat.ChatMessage;
import com.veil.domain.action.GameAction;
import com.veil.domain.npc.Observation;
import com.veil.domain.player.Faction;
import com.veil.events.AttackFx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Confidential state. Roles, NPC memories (held on NPCs), the night action queue,
 * investigation results, and hunt budgets live here. Nothing in this class is ever
 * serialized to a client directly; only the redaction boundary may read from it, and
 * only the slice belonging to a specific viewer.
 */
public class PrivateState {

    private final List<GameAction> queuedNightActions = new ArrayList<>();
    private final Map<String, Map<String, Faction>> investigations = new HashMap<>();
    private final Map<String, Map<String, List<Observation>>> npcAnswers = new HashMap<>();
    private final Map<String, Integer> huntAttemptsRemaining = new HashMap<>();
    private final List<ChatMessage> chatLog = new ArrayList<>();
    private long chatSeq = 0;
    // Live strikes filed THIS night — witnessed only by players sharing the attack's room.
    private final List<AttackFx> attackFx = new ArrayList<>();

    // --- Night action queue -------------------------------------------------

    public void queueAction(GameAction action) {
        queuedNightActions.add(action);
    }

    public List<GameAction> drainQueuedActions() {
        List<GameAction> drained = new ArrayList<>(queuedNightActions);
        queuedNightActions.clear();
        return drained;
    }

    // --- Oracle investigations ---------------------------------------------

    public void recordInvestigation(String oracleId, String targetId, Faction reading) {
        investigations.computeIfAbsent(oracleId, k -> new LinkedHashMap<>()).put(targetId, reading);
    }

    public Map<String, Faction> investigationResults(String oracleId) {
        return investigations.getOrDefault(oracleId, Map.of());
    }

    // --- NPC answers --------------------------------------------------------

    public void recordNpcAnswer(String askerId, String npcId, List<Observation> answer) {
        npcAnswers.computeIfAbsent(askerId, k -> new LinkedHashMap<>()).put(npcId, answer);
    }

    public Map<String, List<Observation>> npcAnswers(String askerId) {
        return npcAnswers.getOrDefault(askerId, Map.of());
    }

    // --- Shadow hunt budget -------------------------------------------------

    /** Returns true and decrements if the shadow still has hunt attempts left. */
    public boolean tryConsumeHuntAttempt(String shadowId, int max) {
        int remaining = huntAttemptsRemaining.getOrDefault(shadowId, max);
        if (remaining <= 0) return false;
        huntAttemptsRemaining.put(shadowId, remaining - 1);
        return true;
    }

    public int huntAttemptsRemaining(String shadowId, int max) {
        return huntAttemptsRemaining.getOrDefault(shadowId, max);
    }

    // --- Chat (confidential: filtered per-viewer only at the redaction boundary) ----

    /** Next monotonic sequence number, ensuring stable ordering within a tick. */
    public long nextChatSeq() {
        return chatSeq++;
    }

    /** Append a chat line. Never broadcast raw; {@code DtoAssembler} filters it per viewer. */
    public void postChatMessage(ChatMessage message) {
        chatLog.add(message);
    }

    /** The full append-only chat log. Only the redaction boundary should read this. */
    public List<ChatMessage> chatLog() {
        return Collections.unmodifiableList(chatLog);
    }

    // --- Live attack FX (co-located witnesses only) -------------------------

    public void recordAttackFx(AttackFx fx) {
        attackFx.add(fx);
    }

    public List<AttackFx> attackFx() {
        return Collections.unmodifiableList(attackFx);
    }

    /** Cleared at the start of each night so last night's strikes don't replay. */
    public void clearAttackFx() {
        attackFx.clear();
    }
}
