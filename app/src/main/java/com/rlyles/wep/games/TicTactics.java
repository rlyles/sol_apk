package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.Sprites;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** TicTactics: tic-tac-toe on 3x3, 3x3x3 and 4x4x4 boards against the computer. */
@SuppressLint("ViewConstructor")
public final class TicTactics extends WinApp {
    static final int RED = 1, BLUE = 2;
    private static final String[] TYPES = {"3 x 3", "3 x 3 x 3", "4 x 4 x 4"};
    private static final String[] SKILLS = {"&Beginner", "&Intermediate", "&Expert"};

    int type, n, levels;
    int[] cells;
    int[][] lines;
    private int skill, starts;
    int turn;
    boolean over;
    private int[] winLine;
    private final Random rng = new Random();
    private String status = "";
    private int redWins, blueWins, ties;

    // geometry: each level is a sheared grid
    private float cellW, cellH, shear, levelGap;
    private final RectF area = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TicTactics(Context context, Host host) {
        super(context, host, "tictac");
        type = prefs.getInt("type", 2);
        skill = prefs.getInt("skill", 1);
        starts = prefs.getInt("starts", 0);
        newGame();
    }

    @Override
    public String title() {
        return "TicTactics";
    }

    @Override
    public void save() {
        prefs.edit().putInt("type", type).putInt("skill", skill).putInt("starts", starts).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Start a new game", true, false, this::newGame));
            items.add(Item.sep());
            for (int i = 0; i < 3; i++) {
                int t = i;
                items.add(new Item(TYPES[i], "", "Play " + TYPES[i], true, type == i, () -> {
                    type = t;
                    save();
                    newGame();
                    relayout();
                }));
            }
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit TicTactics", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            for (int i = 0; i < 3; i++) {
                int s = i;
                items.add(new Item(SKILLS[i], "", "Computer plays at " + SKILLS[i].replace("&", "") + " level", true,
                        skill == i, () -> {
                            skill = s;
                            save();
                        }));
            }
            items.add(Item.sep());
            String[] who = {"&Random player starts", "&Red always starts", "&Blue always starts"};
            for (int i = 0; i < 3; i++) {
                int s = i;
                items.add(new Item(who[i], "", "Who moves first", true, starts == i, () -> {
                    starts = s;
                    save();
                }));
            }
        }));
        addHelpMenu(menus, "Get a whole line of your pieces before the computer does. You play red; the computer "
                + "plays blue.\n\nOn the 3 x 3 x 3 and 4 x 4 x 4 boards the levels are stacked one above another, and "
                + "lines can run across a level, straight down through the levels, or diagonally through them: "
                + "corner to corner of the whole cube counts too.\n\nTap a square to play there.",
                "TicTactics for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Red: " + redWins);
        panels.add("Blue: " + blueWins);
        panels.add("Ties: " + ties);
    }

    @Override
    protected String statusText() {
        return status;
    }

    // ---------------------------------------------------------------- rules

    void newGame() {
        n = type == 2 ? 4 : 3;
        levels = type == 0 ? 1 : n;
        cells = new int[n * n * levels];
        lines = buildLines(n, levels);
        over = false;
        winLine = null;
        turn = starts == 1 ? RED : starts == 2 ? BLUE : (rng.nextBoolean() ? RED : BLUE);
        status = turn == RED ? "Your move." : "Blue moves first.";
        invalidate();
        if (turn == BLUE) {
            postDelayed(this::computerMove, 400);
        }
    }

    static int[][] buildLines(int n, int levels) {
        List<int[]> out = new ArrayList<>();
        for (int dz = levels > 1 ? -1 : 0; dz <= (levels > 1 ? 1 : 0); dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    // Keep one of each pair of opposite directions.
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (dz < 0 || dz == 0 && (dy < 0 || dy == 0 && dx < 0)) {
                        continue;
                    }
                    for (int z = 0; z < levels; z++) {
                        for (int y = 0; y < n; y++) {
                            for (int x = 0; x < n; x++) {
                                int ex = x + dx * (n - 1), ey = y + dy * (n - 1), ez = z + dz * (n - 1);
                                if (ex < 0 || ey < 0 || ez < 0 || ex >= n || ey >= n || ez >= levels) {
                                    continue;
                                }
                                if (dz != 0 && levels != n) {
                                    continue;
                                }
                                int[] line = new int[n];
                                for (int i = 0; i < n; i++) {
                                    line[i] = ((z + dz * i) * n + y + dy * i) * n + x + dx * i;
                                }
                                out.add(line);
                            }
                        }
                    }
                }
            }
        }
        return out.toArray(new int[0][]);
    }

    int[] winner() {
        for (int[] line : lines) {
            int first = cells[line[0]];
            if (first == 0) {
                continue;
            }
            boolean all = true;
            for (int i : line) {
                if (cells[i] != first) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return line;
            }
        }
        return null;
    }

    boolean full() {
        for (int v : cells) {
            if (v == 0) {
                return false;
            }
        }
        return true;
    }

    boolean play(int pos, int who) {
        if (over || pos < 0 || cells[pos] != 0 || turn != who) {
            return false;
        }
        cells[pos] = who;
        sound(who == RED ? Sfx.CLICK : Sfx.POP);
        winLine = winner();
        if (winLine != null || full()) {
            over = true;
            if (winLine == null) {
                ties++;
                status = "Tie game.";
            } else if (who == RED) {
                redWins++;
                status = "You win!";
                sound(Sfx.WIN);
            } else {
                blueWins++;
                status = "Blue wins.";
                sound(Sfx.LOSE);
            }
            invalidate();
            return true;
        }
        turn = 3 - who;
        status = turn == RED ? "Your move." : "Thinking...";
        invalidate();
        if (turn == BLUE) {
            postDelayed(this::computerMove, 350);
        }
        return true;
    }

    private void computerMove() {
        if (over || turn != BLUE) {
            return;
        }
        play(chooseMove(), BLUE);
    }

    /** A cell that completes a line for {@code who}, or -1. */
    private int completing(int who) {
        for (int[] line : lines) {
            int mine = 0, empty = -1, blanks = 0;
            for (int i : line) {
                if (cells[i] == who) {
                    mine++;
                } else if (cells[i] == 0) {
                    empty = i;
                    blanks++;
                }
            }
            if (mine == n - 1 && blanks == 1) {
                return empty;
            }
        }
        return -1;
    }

    int chooseMove() {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == 0) {
                free.add(i);
            }
        }
        if (skill == 0 && rng.nextInt(3) > 0) {
            int w = completing(BLUE);
            return w >= 0 && rng.nextBoolean() ? w : free.get(rng.nextInt(free.size()));
        }
        int win = completing(BLUE);
        if (win >= 0) {
            return win;
        }
        int block = completing(RED);
        if (block >= 0) {
            return block;
        }
        if (type == 0 && skill == 2) {
            return minimax3x3();
        }
        int best = free.get(0);
        double bestScore = -1e18;
        for (int pos : free) {
            double s = cellScore(pos, BLUE) + 0.9 * cellScore(pos, RED);
            if (skill == 2) {
                // Look for moves that make two threats at once (forks), and stop red's.
                cells[pos] = BLUE;
                s += threats(BLUE) >= 2 ? 1e6 : 0;
                cells[pos] = RED;
                s += threats(RED) >= 2 ? 5e5 : 0;
                cells[pos] = 0;
            } else {
                s *= 0.8 + rng.nextDouble() * 0.4;
            }
            if (s > bestScore) {
                bestScore = s;
                best = pos;
            }
        }
        return best;
    }

    private double cellScore(int pos, int who) {
        double s = 0;
        for (int[] line : lines) {
            boolean in = false;
            int mine = 0, theirs = 0;
            for (int i : line) {
                if (i == pos) {
                    in = true;
                }
                if (cells[i] == who) {
                    mine++;
                } else if (cells[i] != 0) {
                    theirs++;
                }
            }
            if (in && theirs == 0) {
                s += Math.pow(5, mine);
            }
        }
        return s;
    }

    private int threats(int who) {
        int count = 0;
        for (int[] line : lines) {
            int mine = 0, blanks = 0;
            for (int i : line) {
                if (cells[i] == who) {
                    mine++;
                } else if (cells[i] == 0) {
                    blanks++;
                }
            }
            if (mine == n - 1 && blanks == 1) {
                count++;
            }
        }
        return count;
    }

    private int minimax3x3() {
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            if (cells[i] != 0) {
                continue;
            }
            cells[i] = BLUE;
            int s = mm(RED);
            cells[i] = 0;
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        return best;
    }

    private int mm(int who) {
        int[] w = winner();
        if (w != null) {
            return cells[w[0]] == BLUE ? 10 : -10;
        }
        if (full()) {
            return 0;
        }
        int best = who == BLUE ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (cells[i] != 0) {
                continue;
            }
            cells[i] = who;
            int s = mm(3 - who);
            cells[i] = 0;
            best = who == BLUE ? Math.max(best, s) : Math.min(best, s);
        }
        return best;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void layoutClient(RectF r) {
        float pad = 12 * dp;
        float w = r.width() - 2 * pad, h = r.height() - 2 * pad;
        if (levels == 1) {
            float s = Math.min(w, h) / n;
            cellW = cellH = s;
            shear = 0;
            levelGap = 0;
            area.set(r.centerX() - s * n / 2, r.centerY() - s * n / 2, r.centerX() + s * n / 2, r.centerY() + s * n / 2);
            return;
        }
        // Stacked, sheared levels: total height = levels * (n * cellH) + (levels - 1) * gap.
        float k = 0.45f;
        cellW = Math.min(w / (n + n * k * 0.9f), h / (levels * n * k + (levels - 1) * k * 1.1f) * 1f);
        cellH = cellW * k;
        shear = cellH * 0.9f;
        levelGap = cellH * 1.1f;
        float totalW = n * cellW + n * shear, totalH = levels * n * cellH + (levels - 1) * levelGap;
        area.set(r.centerX() - totalW / 2, r.centerY() - totalH / 2, r.centerX() + totalW / 2, r.centerY() + totalH / 2);
    }

    /** Top-left corner of cell (x, y) on level z, and its parallelogram as a path. */
    private Path cellPath(int x, int y, int z) {
        float top = area.top + z * (n * cellH + levelGap) + y * cellH;
        float left = area.left + x * cellW + (n - 1 - y) * shear;
        Path p = new Path();
        p.moveTo(left + shear, top);
        p.lineTo(left + shear + cellW, top);
        p.lineTo(left + cellW, top + cellH);
        p.lineTo(left, top + cellH);
        p.close();
        return p;
    }

    private float[] cellCentre(int x, int y, int z) {
        float top = area.top + z * (n * cellH + levelGap) + y * cellH;
        float left = area.left + x * cellW + (n - 1 - y) * shear;
        return new float[] {left + shear / 2 + cellW / 2, top + cellH / 2};
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(0xFFFFFFFF);
        for (int z = 0; z < levels; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int i = (z * n + y) * n + x;
                    Path p = cellPath(x, y, z);
                    boolean lit = winLine != null && contains(winLine, i);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(lit ? 0xFFFFFF80 : levels == 1 ? 0xFFFFFFFF : 0xFFE8E8F8);
                    c.drawPath(p, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1.5f, dp * 1.2f));
                    paint.setColor(0xFF000000);
                    c.drawPath(p, paint);
                    paint.setStyle(Paint.Style.FILL);
                    if (cells[i] != 0) {
                        drawPiece(c, cellCentre(x, y, z), cells[i]);
                    }
                }
            }
        }
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    /** The original's red and blue balls (19 pixels across) from the user's TICTAC.EXE, or null. */
    private static Bitmap[] art() {
        return Media.get().cached("tictac.art", m -> {
            Bitmap strip = m.bitmap("tictac.exe", "100");
            if (strip == null || strip.getWidth() < 37 || strip.getHeight() < 90) {
                return null;
            }
            return new Bitmap[] {ball(strip, 26), ball(strip, 43)};
        });
    }

    /** Cuts the ball centred at (18, cy) out of the strip, keeping only the round part. */
    private static Bitmap ball(Bitmap strip, int cy) {
        int size = 19, r2 = 90;
        int[] px = new int[size * size];
        strip.getPixels(px, 0, size, 9, cy - 9, size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int dx = x - 9, dy = y - 9;
                if (dx * dx + dy * dy > r2) {
                    px[y * size + x] = 0;
                }
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }

    private void drawPiece(Canvas c, float[] at, int who) {
        float rx = Math.min(cellW, cellH * 2.2f) * 0.3f, ry = Math.min(cellH, cellW) * 0.32f;
        Bitmap[] art = art();
        if (art != null) {
            float r = ry * 1.25f;
            Sprites.draw(c, art[who == RED ? 0 : 1], at[0] - r, at[1] - r, 2 * r, 2 * r);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.5f, ry * 0.25f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (who == RED) {
            paint.setColor(0xFFFF0000);
            c.drawLine(at[0] - rx, at[1] - ry, at[0] + rx, at[1] + ry, paint);
            c.drawLine(at[0] - rx, at[1] + ry, at[0] + rx, at[1] - ry, paint);
        } else {
            paint.setColor(0xFF0000FF);
            c.drawOval(at[0] - rx, at[1] - ry, at[0] + rx, at[1] + ry, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private int cellAt(float px, float py) {
        for (int z = 0; z < levels; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    RectF b = new RectF();
                    Path p = cellPath(x, y, z);
                    p.computeBounds(b, true);
                    if (!b.contains(px, py)) {
                        continue;
                    }
                    float top = b.top, rel = (py - top) / cellH;
                    float left = b.left + shear * (1 - rel);
                    if (px >= left && px < left + cellW) {
                        return (z * n + y) * n + x;
                    }
                }
            }
        }
        return -1;
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            if (over) {
                newGame();
                return;
            }
            int i = cellAt(e.getX(), e.getY());
            if (turn == RED && i >= 0 && !play(i, RED)) {
                sound(Sfx.DROP);
            }
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            newGame();
            return true;
        }
        return false;
    }
}
