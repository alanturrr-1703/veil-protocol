package com.veil.domain.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * An NPC's append-only memory. A monotonic {@code version} lets the AI layer cache
 * answers safely: a cached answer stays valid until new memories arrive.
 */
public class MemoryBank {

    private final List<Observation> observations = new ArrayList<>();
    private int version = 0;

    public void add(Observation observation) {
        observations.add(observation);
        version++;
    }

    public int version() {
        return version;
    }

    public List<Observation> all() {
        return Collections.unmodifiableList(observations);
    }

    public List<Observation> recall(Predicate<Observation> filter) {
        List<Observation> out = new ArrayList<>();
        for (Observation o : observations) {
            if (filter.test(o)) out.add(o);
        }
        return out;
    }
}
