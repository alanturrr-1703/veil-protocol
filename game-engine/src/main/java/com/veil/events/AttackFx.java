package com.veil.events;

/**
 * A visible strike, recorded the moment a Shadow files a kill at night. Because a Shadow can
 * only attack someone in their OWN room, anyone standing in that room witnesses it live — so
 * the redaction boundary reveals this (attacker included) ONLY to co-located players. Killing
 * with an audience blows your cover; killing in an empty room keeps you hidden.
 *
 * @param attackerId who struck (revealed to co-located witnesses)
 * @param victimId   who was struck
 * @param district   where it happened
 * @param room       the room within the district
 * @param variant    which of the attack animations to play (0..2)
 * @param tick       when it happened, so a client animates each strike exactly once
 */
public record AttackFx(
        String attackerId,
        String victimId,
        String district,
        String room,
        int variant,
        long tick
) {}
