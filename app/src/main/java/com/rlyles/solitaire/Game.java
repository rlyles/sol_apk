package com.rlyles.solitaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Klondike rules and scoring, modelled on the classic Windows Solitaire.
 *
 * <p>Piles are addressed by absolute index: {@link #STOCK}, {@link #WASTE}, the four foundations
 * starting at {@link #FOUNDATION} and the seven tableau columns starting at {@link #TABLEAU}.
 * This class has no Android dependencies so it can be unit tested on the JVM.
 */
public final class Game {
    public static final int STOCK = 0, WASTE = 1, FOUNDATION = 2, TABLEAU = 6, PILE_COUNT = 13;
    public static final int SCORE_STANDARD = 0, SCORE_VEGAS = 1, SCORE_NONE = 2;

    /** Vegas: the ante charged for every deal. */
    public static final int VEGAS_ANTE = 52;

    private final Card[] deck = new Card[52];
    @SuppressWarnings("unchecked")
    private final List<Card>[] piles = new List[PILE_COUNT];

    private boolean drawThree;
    private int scoring;
    private boolean timed;

    private int score;
    private int seconds;
    private boolean started;
    private int recycles;
    /** How many cards of the last draw are still fanned out on the waste (draw three). */
    private int wasteFan;
    private boolean won;
    private int bonus;

    private Snapshot undo;

    public Game() {
        for (int s = 0; s < 4; s++) {
            for (int r = 1; r <= 13; r++) {
                deck[s * 13 + r - 1] = new Card(s, r);
            }
        }
        for (int i = 0; i < PILE_COUNT; i++) {
            piles[i] = new ArrayList<>();
        }
    }

    // ---------------------------------------------------------------- dealing

    /**
     * Shuffles and deals a new game.
     *
     * @param vegasCarry the running Vegas total to carry into this game (cumulative scoring), or 0
     */
    public void deal(long seed, boolean drawThree, int scoring, boolean timed, int vegasCarry) {
        this.drawThree = drawThree;
        this.scoring = scoring;
        this.timed = timed;
        for (List<Card> p : piles) {
            p.clear();
        }
        List<Card> cards = new ArrayList<>();
        Collections.addAll(cards, deck);
        Collections.shuffle(cards, new Random(seed));
        for (Card c : cards) {
            c.faceUp = false;
        }
        int next = 0;
        for (int col = 0; col < 7; col++) {
            for (int row = 0; row <= col; row++) {
                piles[TABLEAU + col].add(cards.get(next++));
            }
            top(TABLEAU + col).faceUp = true;
        }
        while (next < cards.size()) {
            piles[STOCK].add(cards.get(next++));
        }
        score = scoring == SCORE_VEGAS ? vegasCarry - VEGAS_ANTE : 0;
        seconds = 0;
        started = false;
        recycles = 0;
        wasteFan = 0;
        won = false;
        bonus = 0;
        undo = null;
    }

    // ---------------------------------------------------------------- queries

    public List<Card> pile(int index) {
        return piles[index];
    }

    public Card top(int pile) {
        List<Card> p = piles[pile];
        return p.isEmpty() ? null : p.get(p.size() - 1);
    }

    public static boolean isFoundation(int pile) {
        return pile >= FOUNDATION && pile < FOUNDATION + 4;
    }

    public static boolean isTableau(int pile) {
        return pile >= TABLEAU && pile < TABLEAU + 7;
    }

    public boolean drawThree() {
        return drawThree;
    }

    public int scoring() {
        return scoring;
    }

    public boolean timed() {
        return timed;
    }

    /** Turning the timer off mid-game stops the clock; turning it on starts it from where it was. */
    public void setTimed(boolean timed) {
        this.timed = timed;
    }

    public int score() {
        return score;
    }

    public int seconds() {
        return seconds;
    }

    public boolean started() {
        return started;
    }

    public boolean won() {
        return won;
    }

    public int bonus() {
        return bonus;
    }

    public int wasteFan() {
        return Math.min(wasteFan, piles[WASTE].size());
    }

    public boolean canUndo() {
        return undo != null;
    }

    /** True when the stock is empty and the waste may be turned over again. */
    public boolean canRecycle() {
        if (piles[WASTE].isEmpty()) {
            return false;
        }
        if (scoring == SCORE_VEGAS) {
            // Vegas allows one pass when drawing one card, three passes when drawing three.
            return recycles < (drawThree ? 2 : 0);
        }
        return true;
    }

    /** Whether the cards from {@code index} upward in {@code from} may be picked up. */
    public boolean canPick(int from, int index) {
        List<Card> p = piles[from];
        if (index < 0 || index >= p.size() || !p.get(index).faceUp) {
            return false;
        }
        if (from == WASTE || isFoundation(from)) {
            return index == p.size() - 1;
        }
        return isTableau(from);
    }

    public boolean canDrop(int from, int index, int to) {
        if (from == to || !canPick(from, index)) {
            return false;
        }
        Card moving = piles[from].get(index);
        Card target = top(to);
        if (isFoundation(to)) {
            if (index != piles[from].size() - 1) {
                return false;
            }
            return target == null ? moving.rank == 1
                    : target.suit == moving.suit && moving.rank == target.rank + 1;
        }
        if (isTableau(to)) {
            if (target == null) {
                return moving.rank == 13;
            }
            return target.faceUp && target.isRed() != moving.isRed() && moving.rank == target.rank - 1;
        }
        return false;
    }

    /** The foundation that would accept the top card of {@code from}, or -1. */
    public int foundationFor(int from) {
        List<Card> p = piles[from];
        if (p.isEmpty()) {
            return -1;
        }
        int index = p.size() - 1;
        for (int f = FOUNDATION; f < FOUNDATION + 4; f++) {
            if (canDrop(from, index, f)) {
                return f;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- actions

    /** Clicking the stock: draws one or three cards, or turns the waste back over. */
    public boolean drawOrRecycle() {
        List<Card> stock = piles[STOCK];
        List<Card> waste = piles[WASTE];
        if (!stock.isEmpty()) {
            saveUndo();
            int n = Math.min(drawThree ? 3 : 1, stock.size());
            for (int i = 0; i < n; i++) {
                Card c = stock.remove(stock.size() - 1);
                c.faceUp = true;
                waste.add(c);
            }
            wasteFan = n;
            started = true;
            return true;
        }
        if (!canRecycle()) {
            return false;
        }
        saveUndo();
        for (int i = waste.size() - 1; i >= 0; i--) {
            Card c = waste.get(i);
            c.faceUp = false;
            stock.add(c);
        }
        waste.clear();
        wasteFan = 0;
        recycles++;
        if (scoring == SCORE_STANDARD) {
            // Draw one costs 100 for every pass after the first; draw three costs 20 after the third.
            if (!drawThree) {
                addScore(-100);
            } else if (recycles >= 3) {
                addScore(-20);
            }
        }
        started = true;
        return true;
    }

    /** Turns over the face-down top card of a tableau column. */
    public boolean flip(int pile) {
        Card c = top(pile);
        if (!isTableau(pile) || c == null || c.faceUp) {
            return false;
        }
        saveUndo();
        c.faceUp = true;
        if (scoring == SCORE_STANDARD) {
            addScore(5);
        }
        started = true;
        return true;
    }

    /** Moves the cards from {@code index} upward in {@code from} onto {@code to}, if legal. */
    public boolean move(int from, int index, int to) {
        if (!canDrop(from, index, to)) {
            return false;
        }
        saveUndo();
        List<Card> src = piles[from];
        List<Card> moved = new ArrayList<>(src.subList(index, src.size()));
        src.subList(index, src.size()).clear();
        piles[to].addAll(moved);
        if (from == WASTE) {
            wasteFan = src.isEmpty() ? 0 : Math.max(1, wasteFan - 1);
        }
        scoreMove(from, to);
        started = true;
        checkWin();
        return true;
    }

    /**
     * Plays every card it can onto the foundations (the right-click shortcut). Each move is
     * reported to {@code movesOut} as {from, to}. The whole sweep is a single undo step.
     */
    public int playAllToFoundations(List<int[]> movesOut) {
        Snapshot before = new Snapshot();
        int count = 0;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int p = WASTE; p < PILE_COUNT; p++) {
                if (isFoundation(p)) {
                    continue;
                }
                int f = foundationFor(p);
                if (f >= 0) {
                    move(p, piles[p].size() - 1, f);
                    if (movesOut != null) {
                        movesOut.add(new int[] {p, f});
                    }
                    count++;
                    progress = true;
                }
            }
        }
        if (count > 0) {
            undo = won ? null : before;
        }
        return count;
    }

    /** Called once a second by the UI. Standard timed scoring takes 2 points every 10 seconds. */
    public void tick() {
        if (!timed || !started || won) {
            return;
        }
        seconds++;
        if (scoring == SCORE_STANDARD && seconds % 10 == 0) {
            addScore(-2);
        }
    }

    public boolean undo() {
        if (undo == null) {
            return false;
        }
        undo.restore();
        undo = null;
        return true;
    }

    // ---------------------------------------------------------------- scoring

    private void scoreMove(int from, int to) {
        if (scoring == SCORE_STANDARD) {
            if (from == WASTE && isTableau(to)) {
                addScore(5);
            } else if ((from == WASTE || isTableau(from)) && isFoundation(to)) {
                addScore(10);
            } else if (isFoundation(from) && isTableau(to)) {
                addScore(-15);
            }
        } else if (scoring == SCORE_VEGAS) {
            if (isFoundation(to) && !isFoundation(from)) {
                score += 5;
            } else if (isFoundation(from) && !isFoundation(to)) {
                score -= 5;
            }
        }
    }

    private void addScore(int delta) {
        score = Math.max(0, score + delta);
    }

    private void checkWin() {
        for (int f = FOUNDATION; f < FOUNDATION + 4; f++) {
            if (piles[f].size() != 13) {
                return;
            }
        }
        won = true;
        undo = null;
        if (timed && scoring == SCORE_STANDARD && seconds >= 30) {
            bonus = 700000 / seconds;
            score += bonus;
        }
    }

    // ---------------------------------------------------------------- undo

    private void saveUndo() {
        undo = new Snapshot();
    }

    private final class Snapshot {
        final Card[][] cards = new Card[PILE_COUNT][];
        final boolean[] faceUp = new boolean[52];
        final int score = Game.this.score;
        final int recycles = Game.this.recycles;
        final int wasteFan = Game.this.wasteFan;

        Snapshot() {
            for (int i = 0; i < PILE_COUNT; i++) {
                cards[i] = piles[i].toArray(new Card[0]);
            }
            for (Card c : deck) {
                faceUp[c.id()] = c.faceUp;
            }
        }

        void restore() {
            for (int i = 0; i < PILE_COUNT; i++) {
                piles[i].clear();
                Collections.addAll(piles[i], cards[i]);
            }
            for (Card c : deck) {
                c.faceUp = faceUp[c.id()];
            }
            Game.this.score = score;
            Game.this.recycles = recycles;
            Game.this.wasteFan = wasteFan;
        }
    }

    // ---------------------------------------------------------------- persistence

    private static final int HEADER_FIELDS = 11;

    /** Serialises the game (not the undo step) to a single string. */
    public String save() {
        StringBuilder sb = new StringBuilder("v1");
        int[] header = {drawThree ? 1 : 0, scoring, timed ? 1 : 0, score, seconds, started ? 1 : 0,
                recycles, wasteFan, won ? 1 : 0, bonus};
        for (int v : header) {
            sb.append(';').append(v);
        }
        for (List<Card> p : piles) {
            sb.append(';');
            for (int i = 0; i < p.size(); i++) {
                Card c = p.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(c.faceUp ? c.id() + 100 : c.id());
            }
        }
        return sb.toString();
    }

    /** Restores a game written by {@link #save()}; returns false (leaving this game untouched) if invalid. */
    public boolean load(String s) {
        if (s == null) {
            return false;
        }
        String[] f = s.split(";", -1);
        if (f.length != HEADER_FIELDS + PILE_COUNT || !"v1".equals(f[0])) {
            return false;
        }
        int[] n = new int[HEADER_FIELDS];
        int[][] layout = new int[PILE_COUNT][];
        try {
            for (int i = 1; i < HEADER_FIELDS; i++) {
                n[i] = Integer.parseInt(f[i]);
            }
            boolean[] seen = new boolean[52];
            int total = 0;
            for (int i = 0; i < PILE_COUNT; i++) {
                String part = f[HEADER_FIELDS + i];
                String[] codes = part.isEmpty() ? new String[0] : part.split(",");
                layout[i] = new int[codes.length];
                for (int j = 0; j < codes.length; j++) {
                    int code = Integer.parseInt(codes[j]);
                    int id = code % 100;
                    if (code < 0 || code >= 200 || id >= 52 || seen[id]) {
                        return false;
                    }
                    seen[id] = true;
                    layout[i][j] = code;
                    total++;
                }
            }
            if (total != 52) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        drawThree = n[1] == 1;
        scoring = n[2];
        timed = n[3] == 1;
        score = n[4];
        seconds = n[5];
        started = n[6] == 1;
        recycles = n[7];
        wasteFan = n[8];
        won = n[9] == 1;
        bonus = n[10];
        for (int i = 0; i < PILE_COUNT; i++) {
            piles[i].clear();
            for (int code : layout[i]) {
                Card c = deck[code % 100];
                c.faceUp = code >= 100;
                piles[i].add(c);
            }
        }
        undo = null;
        return true;
    }
}
