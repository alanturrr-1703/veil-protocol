package com.veil.api.ws;

/**
 * An intent sent by a client over the WebSocket. The client expresses WHAT it wants to do;
 * the server decides whether it is legal. Unused fields are simply null for a given type.
 *
 * Examples:
 *   {"type":"MOVE","toLocationId":"docks"}
 *   {"type":"ATTACK","targetId":"p4"}
 *   {"type":"QUERY_NPC","npcId":"n1","topic":"last night"}
 *   {"type":"VOTE","targetId":"p1"}
 *   {"type":"CHAT","channel":"DAY","text":"I saw Vex near the docks"}
 */
public record IntentMessage(
        String type,
        String toLocationId,
        String targetId,
        String npcId,
        String topic,
        String channel,
        String text
) {}
