package com.rlyles.wep.games;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Proves each built-in Chip's Challenge level can be finished, by breadth-first search over moves. */
public class ChipsSolveTest {
    /** One player move: hold a direction (or nothing) for the three ticks a step takes. */
    private static ChipsEngine step(ChipsEngine e, int dir) {
        ChipsEngine n = e.copy();
        for (int i = 0; i < 3 && !n.won && !n.dead; i++) {
            n.setInput(i == 0 ? dir : -1);
            n.tick();
        }
        return n;
    }

    @Test
    public void everyBuiltInLevelCanBeWon() {
        List<ChipsEngine.Level> levels = ChipsLevels.builtIn();
        for (ChipsEngine.Level lv : levels) {
            ChipsEngine start = new ChipsEngine();
            start.load(lv);
            ArrayDeque<ChipsEngine> queue = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            queue.add(start);
            seen.add(start.stateKey());
            boolean solved = false;
            while (!queue.isEmpty() && !solved && seen.size() < 400_000) {
                ChipsEngine e = queue.poll();
                for (int d = -1; d < 4; d++) {
                    ChipsEngine n = step(e, d);
                    if (n.won) {
                        solved = true;
                        break;
                    }
                    if (n.dead || n.timed() && n.timeLeft <= 0) {
                        continue;
                    }
                    if (seen.add(n.stateKey())) {
                        queue.add(n);
                    }
                }
            }
            assertTrue("level " + lv.number + " " + lv.title + " can be won (searched " + seen.size() + ")", solved);
        }
    }
}
