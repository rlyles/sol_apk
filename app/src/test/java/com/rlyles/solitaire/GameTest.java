package com.rlyles.solitaire;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameTest {

    /** Card code helpers for building positions with {@link Game#load}. */
    private static int up(int suit, int rank) {
        return 100 + suit * 13 + rank - 1;
    }

    private static int down(int suit, int rank) {
        return suit * 13 + rank - 1;
    }

    /** Builds a save string: header values then 13 piles of card codes. */
    private static String position(int scoring, boolean drawThree, int score, int[]... piles) {
        StringBuilder sb = new StringBuilder("v1;" + (drawThree ? 1 : 0) + ";" + scoring + ";1;" + score
                + ";100;1;0;0;0;0");
        for (int[] p : piles) {
            sb.append(';');
            for (int i = 0; i < p.length; i++) {
                sb.append(i > 0 ? "," : "").append(p[i]);
            }
        }
        return sb.toString();
    }

    /** Every card on the foundations except the given ones. */
    private static int[] foundationUpTo(int suit, int topRank) {
        int[] f = new int[topRank];
        for (int r = 1; r <= topRank; r++) {
            f[r - 1] = up(suit, r);
        }
        return f;
    }

    private static int[] empty() {
        return new int[0];
    }

    /** Just the piles part of a save string. */
    private static String layout(Game g) {
        return g.save().split(";", 12)[11];
    }

    private static int total(Game g) {
        int n = 0;
        for (int i = 0; i < Game.PILE_COUNT; i++) {
            n += g.pile(i).size();
        }
        return n;
    }

    @Test
    public void dealLaysOutKlondike() {
        Game g = new Game();
        g.deal(1, false, Game.SCORE_STANDARD, true, 0);
        for (int col = 0; col < 7; col++) {
            List<Card> p = g.pile(Game.TABLEAU + col);
            assertEquals(col + 1, p.size());
            for (int i = 0; i < p.size(); i++) {
                assertEquals(i == p.size() - 1, p.get(i).faceUp);
            }
        }
        assertEquals(24, g.pile(Game.STOCK).size());
        assertEquals(52, total(g));
        assertEquals(0, g.score());
    }

    @Test
    public void drawOneAndRecycleCostsHundred() {
        Game g = new Game();
        g.deal(2, false, Game.SCORE_STANDARD, false, 0);
        assertTrue(g.drawOrRecycle());
        assertEquals(1, g.pile(Game.WASTE).size());
        assertTrue(g.pile(Game.WASTE).get(0).faceUp);
        while (!g.pile(Game.STOCK).isEmpty()) {
            g.drawOrRecycle();
        }
        assertEquals(24, g.pile(Game.WASTE).size());
        assertTrue(g.canRecycle());
        assertTrue(g.drawOrRecycle());
        assertEquals(24, g.pile(Game.STOCK).size());
        assertEquals(0, g.score()); // clamped at zero
    }

    @Test
    public void drawThreeFansThreeAndOrderSurvivesRecycle() {
        Game g = new Game();
        g.deal(3, true, Game.SCORE_STANDARD, false, 0);
        List<Card> stockBefore = new ArrayList<>(g.pile(Game.STOCK));
        g.drawOrRecycle();
        assertEquals(3, g.pile(Game.WASTE).size());
        assertEquals(3, g.wasteFan());
        // The top of the stock is dealt first, so the third card from the top ends up on the waste top.
        assertEquals(stockBefore.get(21), g.top(Game.WASTE));
        while (!g.pile(Game.STOCK).isEmpty()) {
            g.drawOrRecycle();
        }
        g.drawOrRecycle();
        assertEquals(stockBefore, g.pile(Game.STOCK));
    }

    @Test
    public void standardScoring() {
        Game g = new Game();
        // Waste: A spades under A hearts. Columns: K spades (face down) under 2 clubs, 3 diamonds,
        // 2 hearts, 3 spades.
        int[] rest = new int[45];
        int k = 0;
        for (int s = 0; s < 4; s++) {
            for (int r = 1; r <= 13; r++) {
                int id = s * 13 + r - 1;
                if (id != down(Card.HEARTS, 1) && id != down(Card.CLUBS, 2) && id != down(Card.DIAMONDS, 3)
                        && id != down(Card.SPADES, 13) && id != down(Card.HEARTS, 2)
                        && id != down(Card.SPADES, 1) && id != down(Card.SPADES, 3)) {
                    rest[k++] = id;
                }
            }
        }
        g.load(position(Game.SCORE_STANDARD, false, 0,
                rest, new int[] {up(Card.SPADES, 1), up(Card.HEARTS, 1)},
                empty(), empty(), empty(), empty(),
                new int[] {down(Card.SPADES, 13), up(Card.CLUBS, 2)},
                new int[] {up(Card.DIAMONDS, 3)},
                new int[] {up(Card.HEARTS, 2)},
                new int[] {up(Card.SPADES, 3)}, empty(), empty(), empty()));
        assertEquals(52, total(g));

        assertTrue(g.move(Game.WASTE, 1, Game.FOUNDATION)); // A hearts up: +10
        assertEquals(10, g.score());
        assertTrue(g.move(Game.WASTE, 0, Game.FOUNDATION + 1)); // A spades up: +10
        assertEquals(20, g.score());
        assertTrue(g.move(Game.TABLEAU + 2, 0, Game.FOUNDATION)); // 2 hearts from column: +10
        assertEquals(30, g.score());
        assertTrue(g.move(Game.TABLEAU, 1, Game.TABLEAU + 1)); // 2 clubs on 3 diamonds: 0
        assertEquals(30, g.score());
        assertTrue(g.flip(Game.TABLEAU)); // +5
        assertEquals(35, g.score());
        assertTrue(g.move(Game.FOUNDATION, 1, Game.TABLEAU + 3)); // 2 hearts back down onto 3 spades: -15
        assertEquals(20, g.score());
    }

    @Test
    public void tableauRules() {
        Game g = new Game();
        int[] rest = new int[48];
        int k = 0;
        int[] used = {down(Card.HEARTS, 13), down(Card.SPADES, 12), down(Card.CLUBS, 12), down(Card.DIAMONDS, 11)};
        outer:
        for (int id = 0; id < 52; id++) {
            for (int u : used) {
                if (u == id) {
                    continue outer;
                }
            }
            rest[k++] = id;
        }
        g.load(position(Game.SCORE_STANDARD, false, 0, rest, empty(), empty(), empty(), empty(), empty(),
                new int[] {up(Card.HEARTS, 13)}, new int[] {up(Card.SPADES, 12)}, new int[] {up(Card.CLUBS, 12)},
                new int[] {up(Card.DIAMONDS, 11)}, empty(), empty(), empty()));
        assertTrue(g.canDrop(Game.TABLEAU + 1, 0, Game.TABLEAU)); // black Q on red K
        assertFalse(g.canDrop(Game.TABLEAU + 3, 0, Game.TABLEAU)); // J on K
        assertFalse(g.canDrop(Game.TABLEAU + 3, 0, Game.TABLEAU + 4)); // only kings to empty columns
        assertTrue(g.canDrop(Game.TABLEAU, 0, Game.TABLEAU + 4)); // king to empty column
        assertTrue(g.canDrop(Game.TABLEAU + 3, 0, Game.TABLEAU + 1)); // red J on black Q
        assertFalse(g.canDrop(Game.TABLEAU + 3, 0, Game.FOUNDATION)); // foundations start with aces
        assertTrue(g.move(Game.TABLEAU + 1, 0, Game.TABLEAU));
        assertTrue(g.move(Game.TABLEAU + 3, 0, Game.TABLEAU));
        // Moving a run: K Q J together into an empty column.
        assertTrue(g.canDrop(Game.TABLEAU, 0, Game.TABLEAU + 5));
        assertTrue(g.move(Game.TABLEAU, 0, Game.TABLEAU + 5));
        assertEquals(3, g.pile(Game.TABLEAU + 5).size());
    }

    @Test
    public void vegasAnteCumulativeAndPassLimits() {
        Game g = new Game();
        g.deal(5, false, Game.SCORE_VEGAS, false, 0);
        assertEquals(-52, g.score());
        while (!g.pile(Game.STOCK).isEmpty()) {
            g.drawOrRecycle();
        }
        assertFalse("draw one gets a single pass in Vegas", g.canRecycle());
        assertFalse(g.drawOrRecycle());

        g.deal(6, true, Game.SCORE_VEGAS, false, -52);
        assertEquals(-104, g.score());
        for (int pass = 0; pass < 3; pass++) {
            while (!g.pile(Game.STOCK).isEmpty()) {
                g.drawOrRecycle();
            }
            if (pass < 2) {
                assertTrue(g.drawOrRecycle());
            }
        }
        assertFalse("draw three gets three passes in Vegas", g.canRecycle());
    }

    @Test
    public void vegasPaysFivePerFoundationCard() {
        Game g = new Game();
        int[] rest = new int[51];
        for (int id = 1, k = 0; id < 52; id++) {
            rest[k++] = id;
        }
        g.load(position(Game.SCORE_VEGAS, false, -52, rest, new int[] {up(Card.CLUBS, 1)}, empty(), empty(),
                empty(), empty(), empty(), empty(), empty(), empty(), empty(), empty(), empty()));
        assertTrue(g.move(Game.WASTE, 0, Game.FOUNDATION));
        assertEquals(-47, g.score());
    }

    @Test
    public void undoRestoresOneStep() {
        Game g = new Game();
        g.deal(7, true, Game.SCORE_STANDARD, false, 0);
        String before = layout(g);
        assertFalse(g.canUndo());
        g.drawOrRecycle();
        assertTrue(g.canUndo());
        assertTrue(g.undo());
        assertEquals(before, layout(g));
        assertFalse(g.canUndo());
    }

    @Test
    public void saveLoadRoundTrip() {
        Game g = new Game();
        g.deal(8, true, Game.SCORE_VEGAS, true, 10);
        g.drawOrRecycle();
        String s = g.save();
        Game h = new Game();
        assertTrue(h.load(s));
        assertEquals(s, h.save());
        assertFalse(h.load("garbage"));
        assertFalse(h.load(s.replace(";", ";9")));
        assertEquals(s, h.save());
    }

    @Test
    public void winningAddsTimeBonus() {
        Game g = new Game();
        g.load(position(Game.SCORE_STANDARD, false, 100, empty(), empty(),
                foundationUpTo(0, 13), foundationUpTo(1, 13), foundationUpTo(2, 13), foundationUpTo(3, 12),
                new int[] {up(3, 13)}, empty(), empty(), empty(), empty(), empty(), empty()));
        assertFalse(g.won());
        assertTrue(g.move(Game.TABLEAU, 0, Game.FOUNDATION + 3));
        assertTrue(g.won());
        assertEquals(7000, g.bonus()); // 700000 / 100 seconds
        assertEquals(100 + 10 + 7000, g.score());
        assertFalse(g.canUndo());
    }

    @Test
    public void playAllSweepsEverythingUp() {
        Game g = new Game();
        g.load(position(Game.SCORE_STANDARD, false, 0, empty(), new int[] {up(0, 13)},
                foundationUpTo(0, 10), foundationUpTo(1, 13), foundationUpTo(2, 13), foundationUpTo(3, 13),
                new int[] {up(0, 12)}, new int[] {up(0, 11)}, empty(), empty(), empty(), empty(), empty()));
        List<int[]> moves = new ArrayList<>();
        assertEquals(3, g.playAllToFoundations(moves));
        assertEquals(3, moves.size());
        assertTrue(g.won());
    }

    @Test
    public void timerPenalisesStandardEveryTenSeconds() {
        Game g = new Game();
        g.deal(9, false, Game.SCORE_STANDARD, true, 0);
        g.tick();
        assertEquals("clock waits for the first move", 0, g.seconds());
        g.drawOrRecycle();
        assertTrue(g.load(g.save().replaceFirst("^v1;0;0;1;0;", "v1;0;0;1;50;")));
        int base = g.score();
        assertEquals(50, base);
        for (int i = 0; i < 20; i++) {
            g.tick();
        }
        assertEquals(20, g.seconds());
        assertEquals(base - 4, g.score());
    }

    /** Random play never loses or duplicates a card, and every legal move stays legal. */
    @Test
    public void randomPlayKeepsInvariants() {
        Random rnd = new Random(42);
        for (int game = 0; game < 200; game++) {
            Game g = new Game();
            g.deal(rnd.nextLong(), rnd.nextBoolean(), rnd.nextInt(3), true, 0);
            for (int step = 0; step < 400 && !g.won(); step++) {
                int choice = rnd.nextInt(10);
                if (choice == 0) {
                    g.drawOrRecycle();
                } else if (choice == 1) {
                    g.undo();
                } else if (choice == 2) {
                    g.playAllToFoundations(null);
                } else {
                    int from = 1 + rnd.nextInt(Game.PILE_COUNT - 1);
                    if (Game.isTableau(from)) {
                        g.flip(from);
                    }
                    int size = g.pile(from).size();
                    if (size == 0) {
                        continue;
                    }
                    int idx = rnd.nextInt(size);
                    int to = Game.FOUNDATION + rnd.nextInt(11);
                    boolean legal = g.canDrop(from, idx, to);
                    assertEquals(legal, g.move(from, idx, to));
                }
                assertEquals(52, total(g));
                boolean[] seen = new boolean[52];
                for (int p = 0; p < Game.PILE_COUNT; p++) {
                    for (Card c : g.pile(p)) {
                        assertFalse(seen[c.id()]);
                        seen[c.id()] = true;
                    }
                }
                for (int f = Game.FOUNDATION; f < Game.FOUNDATION + 4; f++) {
                    List<Card> p = g.pile(f);
                    for (int i = 0; i < p.size(); i++) {
                        assertEquals(i + 1, p.get(i).rank);
                        assertEquals(p.get(0).suit, p.get(i).suit);
                    }
                }
                Game copy = new Game();
                assertTrue(copy.load(g.save()));
                assertNotNull(copy.save());
            }
        }
    }
}
