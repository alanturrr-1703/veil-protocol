package com.veil.engine;

import com.veil.domain.action.GameAction;
import com.veil.events.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves a batch of queued commands deterministically. All ordering rules live in
 * exactly one place: sort by action priority (Shield → Attack → Investigate → Query →
 * Move), validate, then execute. Each action encapsulates its own effect (Command),
 * so this class stays tiny and rule changes are localized.
 */
public class GameResolver {

    public void resolve(List<GameAction> actions, GameContext ctx, EventBus bus) {
        List<GameAction> ordered = new ArrayList<>(actions);
        ordered.sort(Comparator.comparingInt(GameAction::priority));

        for (GameAction action : ordered) {
            if (action.validate(ctx)) {
                action.execute(ctx, bus);
            }
        }
    }
}
