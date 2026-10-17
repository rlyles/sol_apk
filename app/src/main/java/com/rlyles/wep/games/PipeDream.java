package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.Sprites;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pipe Dream: lay pipe from the dispenser ahead of the flooz. Build at least the required distance
 * before it spills out of an open end.
 */
@SuppressLint("ViewConstructor")
public final class PipeDream extends WinApp {
    static final int GW = 10, GH = 7;
    static final int EMPTY = -1, H = 0, V = 1, CROSS = 2, NE = 3, NW = 4, SE = 5, SW = 6, START = 7, BLOCK = 8;
    /** Openings per piece as a bitmask of up(1), right(2), down(4), left(8). */
    static final int[] OPEN = {2 | 8, 1 | 4, 15, 1 | 2, 1 | 8, 4 | 2, 4 | 8};
    private static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};

    final int[] grid = new int[GW * GH];
    /** Fill state per cell: bit 0 horizontal pass, bit 1 vertical pass (crosses can be filled twice). */
    final int[] filled = new int[GW * GH];
    final List<Integer> queue = new ArrayList<>();
    private final Random rng = new Random();
    int level = 1, score, distance;
    boolean over = true, levelDone, paused, fast;
    private int startCell, startDir;
    private float countdown, countdownTotal;
    // flow: current cell, the side it entered from and progress across the cell
    private int flowCell, flowIn, flowOut;
    private float progress;
    private boolean flowing;
    private float bomb;
    private int bombCell = -1;

    private static final int FLOOZ = 0xFF00D000;
    private float cell;
    private final RectF board = new RectF(), dispenser = new RectF(), fastButton = new RectF();
    private boolean fastPressed;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PipeDream(Context context, Host host) {
        super(context, host, "pipe");
        setupLevel(1);
        over = true;
        setAnimating(true);
    }

    @Override
    public String title() {
        return "Pipe Dream";
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New Game", "F2", "Start at level 1", true, false, this::newGame));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the game", !over, false, () -> paused = !paused));
            items.add(new Item("&High Scores...", "", "Best scores", true, false, () -> messageBox("High Scores",
                    "Best score: " + prefs.getInt("best", 0) + "\nReached level " + prefs.getInt("bestLevel", 1), null)));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Pipe Dream", true, false, () -> host.closeApp(this)));
        }));
        addHelpMenu(menus, "Lay pipe for the flooz before it starts to flow! Tap a square to put down the next "
                + "piece from the dispenser on the left (the bottom one is next). You can tap a piece that the flooz has "
                + "not reached to blow it up and replace it, for a 50-point penalty.\n\nThe flooz starts from the "
                + "valve when the timer runs out. Each level needs a certain distance of pipe (shown in the status bar) "
                + "before the flooz spills out of an open end.\n\nScoring: 50 points for each piece the flooz runs "
                + "through, and 500 more each time it crosses back through a cross piece. Tap Fast when you're done "
                + "to speed up the flooz.", "Pipe Dream for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Level: " + level);
        panels.add("Distance: " + Math.max(0, distance));
        panels.add("Score: " + score);
    }

    @Override
    protected String statusText() {
        return over ? "Tap to play" : !flowing ? "Flooz in " + (int) Math.ceil(countdown) + "s" : "";
    }

    // ---------------------------------------------------------------- game

    void newGame() {
        score = 0;
        setupLevel(1);
    }

    void setupLevel(int lv) {
        level = lv;
        java.util.Arrays.fill(grid, EMPTY);
        java.util.Arrays.fill(filled, 0);
        int sx = 1 + rng.nextInt(GW - 2), sy = 1 + rng.nextInt(GH - 2);
        startCell = sy * GW + sx;
        startDir = rng.nextInt(4);
        // Keep the start's outlet pointing into the board.
        while (sx + DX[startDir] < 0 || sx + DX[startDir] >= GW || sy + DY[startDir] < 0 || sy + DY[startDir] >= GH) {
            startDir = (startDir + 1) % 4;
        }
        grid[startCell] = START;
        int blocks = Math.max(0, lv - 2);
        for (int i = 0; i < blocks; i++) {
            int c = rng.nextInt(GW * GH);
            int ahead = (sy + DY[startDir]) * GW + sx + DX[startDir];
            if (grid[c] == EMPTY && c != ahead) {
                grid[c] = BLOCK;
            }
        }
        queue.clear();
        for (int i = 0; i < 5; i++) {
            queue.add(randomPiece());
        }
        distance = 12 + 2 * lv;
        countdownTotal = countdown = Math.max(6f, 18f - lv);
        flowing = false;
        fast = false;
        over = levelDone = paused = false;
        bombCell = -1;
        invalidate();
    }

    private int randomPiece() {
        int r = rng.nextInt(16);
        return r < 3 ? H : r < 6 ? V : r < 7 ? CROSS : 3 + rng.nextInt(4);
    }

    /** Puts the next dispenser piece at {@code c}; returns false if not allowed. */
    boolean place(int c) {
        if (over || levelDone || paused || bomb > 0 || c < 0 || grid[c] == START || grid[c] == BLOCK || filled[c] != 0) {
            return false;
        }
        if (flowing && c == flowCell) {
            return false;
        }
        if (grid[c] != EMPTY) {
            score = Math.max(0, score - 50);
            bomb = 0.35f;
            bombCell = c;
            sound(Sfx.BOOM);
        } else {
            sound(Sfx.CLICK);
        }
        grid[c] = queue.remove(queue.size() - 1);
        queue.add(0, randomPiece());
        return true;
    }

    private float cellTime() {
        return fast ? 0.12f : Math.max(0.7f, 2.4f - level * 0.12f);
    }

    @Override
    protected void frame(float dt) {
        if (over || levelDone || paused) {
            return;
        }
        if (bomb > 0) {
            bomb -= dt;
        }
        if (!flowing) {
            countdown -= fast ? dt * 8 : dt;
            if (countdown <= 0) {
                flowing = true;
                flowCell = startCell;
                flowIn = -1;
                flowOut = startDir;
                progress = 0;
                sound(Sfx.BLIP);
            }
            return;
        }
        progress += dt / cellTime();
        if (progress < 1) {
            return;
        }
        progress = 0;
        int x = flowCell % GW + DX[flowOut], y = flowCell / GW + DY[flowOut];
        int in = (flowOut + 2) % 4;
        int next = y * GW + x;
        if (x < 0 || y < 0 || x >= GW || y >= GH || grid[next] < 0 || grid[next] > SW
                || (OPEN[grid[next]] & (1 << in)) == 0) {
            spill();
            return;
        }
        int piece = grid[next];
        int axis = in % 2 == 0 ? 2 : 1;
        if (piece == CROSS && (filled[next] & axis) != 0 || piece != CROSS && filled[next] != 0) {
            spill();
            return;
        }
        if (piece == CROSS && filled[next] != 0) {
            score += 500;
            sound(Sfx.CHIME);
        }
        filled[next] |= piece == CROSS ? axis : 3;
        score += 50;
        distance--;
        flowCell = next;
        flowIn = in;
        flowOut = exitFor(piece, in);
        sound(Sfx.POP);
    }

    static int exitFor(int piece, int in) {
        if (piece == H || piece == V || piece == CROSS) {
            return (in + 2) % 4;
        }
        int mask = OPEN[piece] & ~(1 << in);
        for (int d = 0; d < 4; d++) {
            if ((mask & (1 << d)) != 0) {
                return d;
            }
        }
        return in;
    }

    private void spill() {
        flowing = false;
        if (distance <= 0) {
            levelDone = true;
            int extra = -distance * 50;
            score += extra;
            sound(Sfx.WIN);
            postDelayed(() -> messageBox("Pipe Dream", "Level " + level + " complete!\n\nScore: " + score,
                    () -> setupLevel(level + 1)), 400);
        } else {
            over = true;
            sound(Sfx.LOSE);
            if (score > prefs.getInt("best", 0)) {
                prefs.edit().putInt("best", score).putInt("bestLevel", level).apply();
            }
            postDelayed(() -> messageBox("Pipe Dream", "The flooz spilled with " + distance
                    + " pieces still to go.\n\nGame over. Score: " + score, null), 400);
        }
    }

    @Override
    protected void onHidden() {
        if (!over) {
            paused = true;
        }
    }

    // ---------------------------------------------------------------- drawing

    /** Original bitmaps from the user's PIPE.EXE: pieces by kind, start pieces by direction. */
    private static final class Art {
        final Bitmap[] pieces = new Bitmap[7], starts = new Bitmap[4];
        Bitmap empty, block;
    }

    private static Art art() {
        return Media.get().cached("pipe.art", m -> {
            if (!m.hasArt("pipe.exe")) {
                return null;
            }
            Art a = new Art();
            int[] pieceIds = {123, 124, 129, 126, 125, 128, 127}; // H, V, cross, NE, NW, SE, SW
            int[] startIds = {111, 112, 113, 110}; // opening up, right, down, left
            for (int i = 0; i < pieceIds.length; i++) {
                a.pieces[i] = m.bitmap("pipe.exe", Integer.toString(pieceIds[i]));
            }
            for (int i = 0; i < 4; i++) {
                a.starts[i] = m.bitmap("pipe.exe", Integer.toString(startIds[i]));
            }
            a.empty = m.bitmap("pipe.exe", "120");
            a.block = m.bitmap("pipe.exe", "301");
            for (Bitmap b : a.pieces) {
                if (b == null) {
                    return null;
                }
            }
            return a.empty == null || a.block == null || a.starts[3] == null ? null : a;
        });
    }

    /** With the original art the pipes are bitmaps, and only the flooz is drawn over them. */
    private boolean floozOnly;

    @Override
    protected void layoutClient(RectF r) {
        float pad = 8 * dp;
        cell = Math.min((r.width() - 3 * pad) / (GW + 1.3f), (r.height() - 2 * pad) / GH);
        float total = (GW + 1.3f) * cell + pad;
        float left = r.centerX() - total / 2, top = r.centerY() - GH * cell / 2;
        dispenser.set(left, top, left + 1.3f * cell, top + 5.2f * cell);
        fastButton.set(left, dispenser.bottom + 0.4f * cell, left + 1.3f * cell, dispenser.bottom + 1.3f * cell);
        board.set(left + 1.3f * cell + pad, top, left + 1.3f * cell + pad + GW * cell, top + GH * cell);
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(0xFF606060);
        ui.sunken(c, new RectF(board.left - 3 * dp, board.top - 3 * dp, board.right + 3 * dp, board.bottom + 3 * dp), Win95.FACE);
        Art art = art();
        floozOnly = art != null;
        for (int i = 0; i < GW * GH; i++) {
            float x = board.left + (i % GW) * cell, y = board.top + (i / GW) * cell;
            RectF r = new RectF(x, y, x + cell, y + cell);
            if (art != null) {
                int g = grid[i];
                Sprites.draw(c, g == BLOCK ? art.block : g == START ? art.starts[startDir] : g >= 0 ? art.pieces[g]
                        : art.empty, r);
            } else {
                ui.raised(c, r);
            }
            if (grid[i] == BLOCK) {
                if (art != null) {
                    continue;
                }
                paint.setColor(0xFF404040);
                c.drawRect(x + cell * 0.12f, y + cell * 0.12f, x + cell * 0.88f, y + cell * 0.88f, paint);
                paint.setColor(0xFF707070);
                c.drawRect(x + cell * 0.12f, y + cell * 0.12f, x + cell * 0.88f, y + cell * 0.24f, paint);
            } else if (grid[i] == START) {
                drawStart(c, x, y);
            } else if (grid[i] >= 0) {
                drawPipe(c, grid[i], x, y, cell, filled[i], i);
            }
            if (i == bombCell && bomb > 0) {
                paint.setColor(0xC0FF8000);
                c.drawCircle(r.centerX(), r.centerY(), cell * 0.6f * (1 - bomb / 0.35f + 0.3f), paint);
            }
        }
        // Dispenser: next piece at the bottom.
        ui.sunken(c, dispenser, Win95.FACE);
        for (int i = 0; i < queue.size(); i++) {
            float s = cell * 0.95f;
            float x = dispenser.centerX() - s / 2, y = dispenser.top + 0.1f * cell + i * cell;
            if (art != null) {
                Sprites.draw(c, art.pieces[queue.get(i)], x, y, s, s);
            } else {
                ui.raised(c, new RectF(x, y, x + s, y + s));
            }
            drawPipe(c, queue.get(i), x, y, s, 0, -1);
            if (i == queue.size() - 1) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * dp);
                paint.setColor(0xFFFFFF00);
                c.drawRect(x + dp, y + dp, x + s - dp, y + s - dp, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        ui.button(c, fastButton, "&Fast", fastPressed, !over && !levelDone, false);
        if (!flowing && !over && !levelDone) {
            // Countdown gauge along the dispenser.
            float frac = countdown / countdownTotal;
            paint.setColor(0xFF00C000);
            c.drawRect(dispenser.right + 2 * dp, dispenser.bottom - dispenser.height() * frac, dispenser.right + 6 * dp,
                    dispenser.bottom, paint);
        }
        if (over || paused) {
            ui.withFont(20, true, () -> ui.centered(c, over ? "Tap to play" : "Paused", board, 0xFF000000));
        }
    }

    private void drawStart(Canvas c, float x, float y) {
        float cx = x + cell / 2, cy = y + cell / 2;
        boolean full = flowing || over || levelDone;
        tube(c, cx, cy, cx + DX[startDir] * cell / 2, cy + DY[startDir] * cell / 2, cell, full ? 1 : 0, false);
        if (!floozOnly) {
            paint.setColor(0xFF303030);
            c.drawCircle(cx, cy, cell * 0.28f, paint);
        }
        if (!floozOnly || full) {
            paint.setColor(full || flowing ? FLOOZ : 0xFF808080);
            c.drawCircle(cx, cy, cell * 0.2f, paint);
        }
        if (!flowing && !over && !levelDone) {
            ui.withFont(Math.max(8, cell / dp * 0.28f), true, () -> ui.centered(c, String.valueOf((int) Math.ceil(countdown)),
                    new RectF(x, y, x + cell, y + cell), 0xFFFFFFFF));
        }
    }

    private void drawPipe(Canvas c, int piece, float x, float y, float s, int fill, int index) {
        float cx = x + s / 2, cy = y + s / 2;
        boolean active = index >= 0 && flowing && index == flowCell;
        for (int d = 0; d < 4; d++) {
            if ((OPEN[piece] & (1 << d)) == 0) {
                continue;
            }
            float amount = 0;
            if (piece == CROSS) {
                int axis = d % 2 == 0 ? 2 : 1;
                amount = (fill & axis) != 0 ? 1 : 0;
                if (active && (flowIn % 2 == d % 2)) {
                    amount = d == flowIn ? Math.min(1, progress * 2) : Math.max(0, progress * 2 - 1);
                }
            } else if (fill != 0) {
                amount = 1;
                if (active) {
                    amount = d == flowIn ? Math.min(1, progress * 2) : Math.max(0, progress * 2 - 1);
                }
            }
            tube(c, cx, cy, cx + DX[d] * s / 2, cy + DY[d] * s / 2, s, amount, !active || d == flowIn);
        }
    }

    /**
     * One half-tube from the centre (x0, y0) to an edge (x1, y1), with {@code amount} of it full of flooz
     * filling from the edge inward, or from the centre outward.
     */
    private void tube(Canvas c, float x0, float y0, float x1, float y1, float s, float amount, boolean fromEdge) {
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStyle(Paint.Style.STROKE);
        if (!floozOnly) {
            paint.setStrokeWidth(s * 0.36f);
            paint.setColor(0xFF000000);
            c.drawLine(x0, y0, x1, y1, paint);
            paint.setStrokeWidth(s * 0.26f);
            paint.setColor(0xFF909090);
            c.drawLine(x0, y0, x1, y1, paint);
        }
        if (amount > 0) {
            paint.setStrokeWidth(s * (floozOnly ? 0.2f : 0.16f));
            paint.setColor(FLOOZ);
            float ax = fromEdge ? x1 : x0, ay = fromEdge ? y1 : y0;
            float sign = fromEdge ? 1 : -1;
            c.drawLine(ax, ay, ax + sign * (x0 - x1) * amount, ay + sign * (y0 - y1) * amount, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            fastPressed = fastButton.contains(x, y);
            return;
        }
        if (e.getActionMasked() != MotionEvent.ACTION_UP) {
            return;
        }
        if (fastPressed) {
            fastPressed = false;
            if (fastButton.contains(x, y) && !over) {
                fast = true;
            }
            return;
        }
        if (over) {
            newGame();
            return;
        }
        if (paused) {
            paused = false;
            return;
        }
        if (board.contains(x, y)) {
            int c = (int) ((y - board.top) / cell) * GW + (int) ((x - board.left) / cell);
            if (!place(c)) {
                sound(Sfx.DROP);
            }
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            newGame();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_F) {
            fast = true;
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_F3) {
            paused = !paused;
            return true;
        }
        return false;
    }
}
