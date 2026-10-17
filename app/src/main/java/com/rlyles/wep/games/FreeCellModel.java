package com.rlyles.wep.games;

import com.rlyles.wep.cards.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * FreeCell rules. Piles: 0-3 free cells, 4-7 home cells, 8-15 columns. Deals use Microsoft's
 * numbered-game algorithm, so game numbers match the original FreeCell.
 */
public final class FreeCellModel {
    public static final int CELL = 0, HOME = 4, COLUMN = 8, PILES = 16;

    private final Card[] deck = new Card[52];
    @SuppressWarnings("unchecked")
    private final List<Card>[] piles = new List[PILES];
    private int gameNumber;
    private int moves;
    private final List<int[]> history = new ArrayList<>();

    public FreeCellModel() {
        for (int s = 0; s < 4; s++) {
            for (int r = 1; r <= 13; r++) {
                deck[s * 13 + r - 1] = new Card(s, r);
                deck[s * 13 + r - 1].faceUp = true;
            }
        }
        for (int i = 0; i < PILES; i++) {
            piles[i] = new ArrayList<>();
        }
    }

    /** Deals game {@code number} (1 to 32000, or -1 and -2 for the two famous special deals). */
    public void deal(int number) {
        gameNumber = number;
        moves = 0;
        history.clear();
        for (List<Card> p : piles) {
            p.clear();
        }
        int[] t = new int[52];
        for (int i = 0; i < 52; i++) {
            t[i] = 51 - i;
        }
        long seed = number < 0 ? 0 : number;
        for (int i = 0; i < 51; i++) {
            seed = (seed * 214013 + 2531011) & 0x7FFFFFFFL;
            int rnd = (int) (seed >> 16);
            int j = 51 - rnd % (52 - i);
            int tmp = t[i];
            t[i] = t[j];
            t[j] = tmp;
        }
        for (int i = 0; i < 52; i++) {
            // Microsoft numbers cards rank*4 + suit, suits in clubs, diamonds, hearts, spades order.
            int rank = t[i] / 4 + 1, suit = t[i] % 4;
            piles[COLUMN + i % 8].add(deck[suit * 13 + rank - 1]);
        }
    }

    public int gameNumber() {
        return gameNumber;
    }

    public List<Card> pile(int i) {
        return piles[i];
    }

    public Card top(int pile) {
        List<Card> p = piles[pile];
        return p.isEmpty() ? null : p.get(p.size() - 1);
    }

    public static boolean isCell(int p) {
        return p >= CELL && p < CELL + 4;
    }

    public static boolean isHome(int p) {
        return p >= HOME && p < HOME + 4;
    }

    public static boolean isColumn(int p) {
        return p >= COLUMN && p < COLUMN + 8;
    }

    public int cardsLeft() {
        int n = 52;
        for (int h = HOME; h < HOME + 4; h++) {
            n -= piles[h].size();
        }
        return n;
    }

    public boolean won() {
        return cardsLeft() == 0;
    }

    public int moves() {
        return moves;
    }

    private int emptyCells() {
        int n = 0;
        for (int i = CELL; i < CELL + 4; i++) {
            if (piles[i].isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private int emptyColumns(int except) {
        int n = 0;
        for (int i = COLUMN; i < COLUMN + 8; i++) {
            if (i != except && piles[i].isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** How many cards can move at once onto {@code to} using free cells and empty columns. */
    public int maxMove(int to) {
        int cols = emptyColumns(to);
        return (emptyCells() + 1) << cols;
    }

    private static boolean stacks(Card lower, Card upper) {
        return lower.isRed() != upper.isRed() && upper.rank == lower.rank - 1;
    }

    /** Index where the ordered run at the bottom of a column starts. */
    public int runStart(int column) {
        List<Card> p = piles[column];
        int i = p.size() - 1;
        while (i > 0 && stacks(p.get(i - 1), p.get(i))) {
            i--;
        }
        return i;
    }

    public boolean canPick(int from, int index) {
        List<Card> p = piles[from];
        if (index < 0 || index >= p.size() || isHome(from)) {
            return false;
        }
        if (isCell(from)) {
            return index == 0;
        }
        return index >= runStart(from);
    }

    public boolean canMove(int from, int index, int to) {
        if (from == to || !canPick(from, index)) {
            return false;
        }
        List<Card> src = piles[from];
        int count = src.size() - index;
        Card moving = src.get(index);
        if (isCell(to)) {
            return count == 1 && piles[to].isEmpty();
        }
        if (isHome(to)) {
            Card t = top(to);
            return count == 1 && (t == null ? moving.rank == 1 : t.suit == moving.suit && moving.rank == t.rank + 1);
        }
        Card t = top(to);
        if (t != null && !stacks(t, moving)) {
            return false;
        }
        return count <= maxMove(to);
    }

    /**
     * For a click-to-move from column {@code from} onto {@code to}, the index of the longest run
     * that can legally go there, or -1.
     */
    public int bestRunFor(int from, int to) {
        List<Card> src = piles[from];
        if (src.isEmpty()) {
            return -1;
        }
        if (!isColumn(from)) {
            return canMove(from, src.size() - 1, to) ? src.size() - 1 : -1;
        }
        for (int i = runStart(from); i < src.size(); i++) {
            if (canMove(from, i, to)) {
                return i;
            }
        }
        return -1;
    }

    public boolean move(int from, int index, int to) {
        if (!canMove(from, index, to)) {
            return false;
        }
        List<Card> src = piles[from];
        List<Card> run = new ArrayList<>(src.subList(index, src.size()));
        src.subList(index, src.size()).clear();
        piles[to].addAll(run);
        history.add(new int[] {from, to, run.size()});
        moves++;
        return true;
    }

    /** The next card that may go home automatically (Microsoft's "safe" rule), as {from, home}, or null. */
    public int[] autoMove() {
        for (int p = CELL; p < PILES; p++) {
            if (isHome(p)) {
                continue;
            }
            Card c = top(p);
            if (c == null || !safeHome(c)) {
                continue;
            }
            for (int h = HOME; h < HOME + 4; h++) {
                if (canMove(p, piles[p].size() - 1, h)) {
                    return new int[] {p, h};
                }
            }
        }
        return null;
    }

    private boolean safeHome(Card c) {
        if (c.rank <= 2) {
            return true;
        }
        int opposite = 0;
        for (int h = HOME; h < HOME + 4; h++) {
            Card u = top(h);
            if (u != null && u.isRed() != c.isRed() && u.rank >= c.rank - 1) {
                opposite++;
            }
        }
        return opposite >= 2;
    }

    /** True when no move at all is possible. */
    public boolean stuck() {
        for (int from = CELL; from < PILES; from++) {
            if (isHome(from) || piles[from].isEmpty()) {
                continue;
            }
            int top = piles[from].size() - 1;
            for (int to = CELL; to < PILES; to++) {
                if (canMove(from, top, to)) {
                    return false;
                }
            }
        }
        return true;
    }

    public String save() {
        StringBuilder sb = new StringBuilder().append(gameNumber).append(';').append(moves);
        for (List<Card> p : piles) {
            sb.append(';');
            for (int i = 0; i < p.size(); i++) {
                sb.append(i > 0 ? "," : "").append(p.get(i).id());
            }
        }
        return sb.toString();
    }

    public boolean load(String s) {
        if (s == null) {
            return false;
        }
        String[] f = s.split(";", -1);
        if (f.length != PILES + 2) {
            return false;
        }
        try {
            int[][] layout = new int[PILES][];
            boolean[] seen = new boolean[52];
            int total = 0;
            for (int i = 0; i < PILES; i++) {
                String[] ids = f[i + 2].isEmpty() ? new String[0] : f[i + 2].split(",");
                layout[i] = new int[ids.length];
                for (int j = 0; j < ids.length; j++) {
                    int id = Integer.parseInt(ids[j]);
                    if (id < 0 || id >= 52 || seen[id]) {
                        return false;
                    }
                    seen[id] = true;
                    layout[i][j] = id;
                    total++;
                }
            }
            if (total != 52) {
                return false;
            }
            gameNumber = Integer.parseInt(f[0]);
            moves = Integer.parseInt(f[1]);
            for (int i = 0; i < PILES; i++) {
                piles[i].clear();
                for (int id : layout[i]) {
                    piles[i].add(deck[id]);
                }
            }
            history.clear();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Restores the deal (Restart Game). */
    public void restart() {
        deal(gameNumber);
    }
}
