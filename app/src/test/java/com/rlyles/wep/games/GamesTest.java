package com.rlyles.wep.games;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.rlyles.wep.cards.Card;
import com.rlyles.wep.cards.CardsDll;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.WinApp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/** Rules tests for the games, run against the real classes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GamesTest {
    private Context ctx;
    private final WinApp.Host host = new WinApp.Host() {
        private final Sfx sfx = new Sfx();

        {
            sfx.enabled = false;
        }

        @Override
        public void closeApp(WinApp app) {
        }

        @Override
        public void minimizeApp(WinApp app) {
        }

        @Override
        public void pickFile(WinApp.FileResult result) {
        }

        @Override
        public CardsDll cardsDll() {
            return null;
        }

        @Override
        public void setCardsDll(byte[] data) {
        }

        @Override
        public Sfx sfx() {
            return sfx;
        }
    };

    @Before
    public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
    }

    private static String name(Card c) {
        return "A23456789TJQK".charAt(c.rank - 1) + String.valueOf("CDHS".charAt(c.suit));
    }

    // ---------------------------------------------------------------- FreeCell

    @Test
    public void freeCellDealsMicrosoftGameOne() {
        FreeCellModel m = new FreeCellModel();
        m.deal(1);
        String[] firstRow = {"JD", "2D", "9H", "JC", "5D", "7H", "7C", "5H"};
        for (int col = 0; col < 8; col++) {
            assertEquals(firstRow[col], name(m.pile(FreeCellModel.COLUMN + col).get(0)));
        }
        // Game #1's last row is 6S 9C 2H 6H in the first four columns.
        assertEquals("6S", name(m.pile(FreeCellModel.COLUMN).get(6)));
        assertEquals("6H", name(m.pile(FreeCellModel.COLUMN + 3).get(6)));
        assertEquals(7, m.pile(FreeCellModel.COLUMN + 3).size());
        assertEquals(6, m.pile(FreeCellModel.COLUMN + 4).size());
    }

    @Test
    public void freeCellSupermoveLimit() {
        FreeCellModel m = new FreeCellModel();
        m.deal(1);
        assertEquals(5, m.maxMove(FreeCellModel.COLUMN));
        String s = m.save();
        FreeCellModel copy = new FreeCellModel();
        assertTrue(copy.load(s));
        assertEquals(s, copy.save());
    }

    // ---------------------------------------------------------------- board games

    @Test
    public void ticTacticsLineCounts() {
        assertEquals(8, TicTactics.buildLines(3, 1).length);
        assertEquals(49, TicTactics.buildLines(3, 3).length);
        assertEquals(76, TicTactics.buildLines(4, 4).length);
    }

    @Test
    public void reversiOpening() {
        Reversi r = new Reversi(ctx, host);
        int moves = 0;
        for (int i = 0; i < 64; i++) {
            if (Reversi.flips(r.board, i, Reversi.HUMAN, false) > 0) {
                moves++;
            }
        }
        assertEquals(4, moves);
        assertTrue(Reversi.bestMove(r.board, Reversi.COMPUTER, 3) >= 0);
    }

    @Test
    public void taipeiDealsCanAlwaysBeWon() {
        Taipei t = new Taipei(ctx, host);
        assertEquals(144, Taipei.TURTLE.length);
        for (int game = 1; game <= 5; game++) {
            t.newGame(game);
            int[] sol = t.solutionForTest();
            for (int p = 0; p < 72; p++) {
                assertTrue("game " + game + " pair " + p, t.remove(sol[p * 2], sol[p * 2 + 1]));
            }
            assertEquals(0, t.tilesLeft());
        }
    }

    @Test
    public void minesweeperFirstClickIsSafe() {
        Minesweeper m = new Minesweeper(ctx, host);
        for (int round = 0; round < 50; round++) {
            m.newGame();
            m.reveal(40);
            assertFalse(m.lost);
            assertEquals(Minesweeper.OPEN, m.state[40]);
        }
        int mines = 0;
        for (boolean b : m.mine) {
            if (b) {
                mines++;
            }
        }
        assertEquals(m.mines, mines);
    }

    @Test
    public void tetraVexPuzzleHasASolution() {
        TetraVex t = new TetraVex(ctx, host);
        int n = t.n;
        for (int round = 0; round < n * n; round++) {
            for (int i = 0; i < n * n; i++) {
                if (t.right[i] != null) {
                    t.place(i, t.right[i].solution);
                }
            }
        }
        assertTrue(t.won);
    }

    @Test
    public void peggedJumps() {
        Pegged p = new Pegged(ctx, host);
        assertEquals(32, p.pegs());
        // From the English start, the peg two above the centre can jump down into it.
        assertTrue(p.jump(10, 24));
        assertEquals(31, p.pegs());
        assertFalse(p.jump(10, 24));
        for (String[] board : Pegged.BOARDS) {
            assertEquals(7, board.length);
        }
    }

    // ---------------------------------------------------------------- action games

    @Test
    public void tetrisScoresLines() {
        Tetris t = new Tetris(ctx, host);
        t.newGame();
        // Fill the bottom row except where a vertical I piece will land.
        java.util.Arrays.fill(t.well, -1);
        for (int x = 0; x < Tetris.W - 1; x++) {
            t.well[(Tetris.H - 1) * Tetris.W + x] = 1;
        }
        t.piece = 0;
        t.rot = 1;
        t.px = Tetris.W - 3;
        t.py = 0;
        t.hardDrop();
        assertEquals(1, t.lines);
        assertTrue(t.score >= 40);
    }

    @Test
    public void pipeDreamElbowsTurn() {
        assertEquals(1, PipeDream.exitFor(PipeDream.NE, 0));
        assertEquals(0, PipeDream.exitFor(PipeDream.NE, 1));
        assertEquals(2, PipeDream.exitFor(PipeDream.CROSS, 0));
        PipeDream p = new PipeDream(ctx, host);
        p.newGame();
        int empty = -1;
        for (int i = 0; i < p.grid.length; i++) {
            if (p.grid[i] == PipeDream.EMPTY) {
                empty = i;
                break;
            }
        }
        assertTrue(p.place(empty));
        assertTrue(p.grid[empty] >= 0);
    }

    @Test
    public void jezzBallCapturesEmptyRegions() {
        JezzBall j = new JezzBall(ctx, host);
        j.newGame();
        j.atoms.clear();
        j.atoms.add(new float[] {5.5f, 5.5f, 1, 1});
        for (int y = 0; y < JezzBall.GH; y++) {
            j.grid[y * JezzBall.GW + 20] = JezzBall.FILLED;
        }
        j.captureEmptyAreas();
        assertTrue(j.percent() > 45);
    }

    @Test
    public void rodentPushesRowsOfBlocks() {
        RodentsRevenge r = new RodentsRevenge(ctx, host);
        r.newGame();
        int start = r.mouse;
        // Every direction from the centre is a block; pushing east moves the whole row.
        assertEquals(RodentsRevenge.BLOCK, r.grid[start + 1]);
        assertTrue(r.step(0));
        assertEquals(start + 1, r.mouse);
    }

    @Test
    public void snakeEatsAndTurns() {
        Snake s = new Snake(ctx, host);
        s.newGame();
        int len = s.body.size();
        s.turn(2);
        assertEquals("cannot reverse into itself", 0, s.dir);
        s.food = s.body.peekFirst() + 1;
        s.stepForTest(0.5f);
        assertTrue(s.body.size() > len);
        assertEquals(2, s.number);
    }

    @Test
    public void skiFreeYetiArrives() {
        SkiFree s = new SkiFree(ctx, host);
        s.y = 19990;
        s.skiDown(1f);
        assertTrue(s.yetiActive);
    }

    // ---------------------------------------------------------------- Chip's Challenge

    @Test
    public void chipsBuiltInLevelsAreWellFormed() {
        List<ChipsEngine.Level> levels = ChipsLevels.builtIn();
        assertEquals(10, levels.size());
        for (ChipsEngine.Level lv : levels) {
            int players = 0, exits = 0;
            for (int t : lv.top) {
                players += t >= 0x6C && t <= 0x6F ? 1 : 0;
                exits += t == ChipsEngine.EXIT ? 1 : 0;
            }
            assertEquals(lv.title, 1, players);
            assertEquals(lv.title, 1, exits);
            assertTrue(lv.title, lv.chips > 0);
        }
    }

    private static ChipsEngine.Level tiny(String... rows) {
        ChipsEngine.Level lv = new ChipsEngine.Level();
        java.util.Arrays.fill(lv.top, ChipsEngine.WALL);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char ch = rows[y].charAt(x);
                int code = ch == '@' ? 0x6E : ch == '~' ? ChipsEngine.WATER : ch == 'c' ? ChipsEngine.CHIP
                        : ch == 'S' ? ChipsEngine.SOCKET : ch == 'E' ? ChipsEngine.EXIT : ch == '=' ? ChipsEngine.BLOCK_TILE
                        : ch == 'r' ? ChipsEngine.KEY_RED : ch == 'R' ? ChipsEngine.DOOR_RED : ch == 'i' ? ChipsEngine.ICE
                        : ch == '#' ? ChipsEngine.WALL : ChipsEngine.FLOOR;
                lv.top[(y + 1) * ChipsEngine.SIZE + x + 1] = code;
                if (code == ChipsEngine.CHIP) {
                    lv.chips++;
                }
            }
        }
        return lv;
    }

    private static void walk(ChipsEngine e, int dir, int steps) {
        for (int i = 0; i < steps; i++) {
            e.setInput(dir);
            e.tick();
            e.setInput(-1);
            e.tick();
            e.tick();
        }
    }

    @Test
    public void chipsCollectsChipsOpensSocketAndExits() {
        ChipsEngine e = new ChipsEngine();
        e.load(tiny("@cSE"));
        walk(e, 1, 1);
        assertEquals(0, e.chipsLeft);
        walk(e, 1, 2);
        assertTrue(e.won);
    }

    @Test
    public void chipsDrownsWithoutFlippers() {
        ChipsEngine e = new ChipsEngine();
        e.load(tiny("@~"));
        walk(e, 1, 1);
        assertTrue(e.dead);
    }

    @Test
    public void chipsBlockFillsWater() {
        ChipsEngine e = new ChipsEngine();
        e.load(tiny("@=~ E"));
        walk(e, 1, 1);
        assertEquals(ChipsEngine.DIRT, e.terrain[1 * ChipsEngine.SIZE + 3]);
        walk(e, 1, 3);
        assertFalse(e.dead);
        assertTrue(e.won);
    }

    @Test
    public void chipsKeysOpenDoorsAndIceSlides() {
        ChipsEngine e = new ChipsEngine();
        e.load(tiny("@rR iiiE"));
        walk(e, 1, 3);
        assertEquals(0, e.keyCount[1]);
        walk(e, 1, 1);
        // One step onto the ice slides Chip all the way to the exit.
        for (int i = 0; i < 10 && !e.won; i++) {
            e.tick();
        }
        assertTrue(e.won);
    }

    @Test
    public void chipsDatParserReadsLayersAndTitles() throws Exception {
        // A one-level file: a 32x32 floor with Chip, written with the standard run-length scheme.
        java.io.ByteArrayOutputStream level = new java.io.ByteArrayOutputStream();
        writeShort(level, 1);
        writeShort(level, 100);
        writeShort(level, 0);
        writeShort(level, 1);
        byte[] top = {(byte) 0x6E, (byte) 0xFF, (byte) 255, 0, (byte) 0xFF, (byte) 255, 0, (byte) 0xFF, (byte) 255, 0,
                (byte) 0xFF, (byte) 255, 0, (byte) 0xFF, (byte) 1, 0x15};
        writeShort(level, top.length);
        level.write(top);
        byte[] bottom = {(byte) 0xFF, (byte) 255, 0, (byte) 0xFF, (byte) 255, 0, (byte) 0xFF, (byte) 255, 0,
                (byte) 0xFF, (byte) 255, 0, (byte) 0xFF, (byte) 4, 0};
        writeShort(level, bottom.length);
        level.write(bottom);
        byte[] title = "TEST\0".getBytes("US-ASCII");
        writeShort(level, 2 + title.length);
        level.write(3);
        level.write(title.length);
        level.write(title);
        java.io.ByteArrayOutputStream file = new java.io.ByteArrayOutputStream();
        file.write(new byte[] {(byte) 0xAC, (byte) 0xAA, 2, 0});
        writeShort(file, 1);
        writeShort(file, level.size());
        file.write(level.toByteArray());
        List<ChipsEngine.Level> levels = ChipsLevels.parseDat(file.toByteArray());
        assertEquals(1, levels.size());
        assertEquals("TEST", levels.get(0).title);
        assertEquals(0x6E, levels.get(0).top[0]);
        assertEquals(ChipsEngine.EXIT, levels.get(0).top[1021]);
        ChipsEngine e = new ChipsEngine();
        e.load(levels.get(0));
        assertNotNull(e.chip);
    }

    private static void writeShort(java.io.ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    // ---------------------------------------------------------------- card games

    @Test
    public void golfPlaysOneUpOrDown() {
        Golf g = new Golf(ctx, host);
        g.deal();
        assertEquals(35, g.score());
        assertEquals(16, g.piles.get(Golf.STOCK).size());
    }

    @Test
    public void cruelStartsWithAcesUp() {
        Cruel c = new Cruel(ctx, host);
        c.newGame();
        for (int f = Cruel.FOUND; f < Cruel.TOTAL; f++) {
            assertEquals(1, c.piles.get(f).get(0).rank);
        }
        assertEquals(48, c.cardsLeft());
        assertFalse("cannot redeal before moving", c.movedSinceDeal);
    }

    @Test
    public void triPeaksCoverage() {
        TriPeaks t = new TriPeaks(ctx, host);
        t.deal();
        int uncovered = 0;
        for (int i = 0; i < 28; i++) {
            if (!t.covered(i)) {
                uncovered++;
            }
        }
        assertEquals(10, uncovered);
    }

    @Test
    public void tutsTombPyramidShape() {
        TutsTomb t = new TutsTomb(ctx, host);
        assertEquals(6, TutsTomb.row(27));
        assertEquals(0, TutsTomb.row(0));
        int open = 0;
        for (int i = 0; i < 28; i++) {
            if (!t.covered(i)) {
                open++;
            }
        }
        assertEquals(7, open);
    }

    @Test
    public void blackJackValues() {
        List<Card> hand = new java.util.ArrayList<>();
        hand.add(up(new Card(0, 1)));
        hand.add(up(new Card(1, 13)));
        assertEquals(21, BlackJack.value(hand));
        hand.add(up(new Card(2, 5)));
        assertEquals(16, BlackJack.value(hand));
        BlackJack b = new BlackJack(ctx, host);
        int bank = b.bank;
        b.deal();
        assertTrue(b.bank <= bank);
        while (b.inRound) {
            b.stay();
        }
        assertFalse(b.inRound);
    }

    private static Card up(Card c) {
        c.faceUp = true;
        return c;
    }
}
