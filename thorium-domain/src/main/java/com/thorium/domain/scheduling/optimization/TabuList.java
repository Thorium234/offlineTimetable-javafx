package com.thorium.domain.scheduling.optimization;

import java.util.HashMap;
import java.util.Map;

public class TabuList {

    private final int tenure;
    private final Map<String, Integer> entries;
    private int currentIteration;

    public TabuList(int tenure) {
        this.tenure = tenure;
        this.entries = new HashMap<>();
        this.currentIteration = 0;
    }

    public void add(SchedulingMove move) {
        String key = move.lesson1Idx() + "-" + move.lesson2Idx() + "-" + move.slot2() + "-" + move.slot1();
        entries.put(key, currentIteration + tenure);
    }

    public boolean contains(SchedulingMove move) {
        String key = move.lesson1Idx() + "-" + move.lesson2Idx() + "-" + move.slot2() + "-" + move.slot1();
        Integer expireAt = entries.get(key);
        if (expireAt == null) return false;
        if (currentIteration >= expireAt) {
            entries.remove(key);
            return false;
        }
        return true;
    }

    public void advanceIteration() {
        currentIteration++;
    }
}
