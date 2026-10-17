package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rodent's Revenge: push the blocks to trap the cats. A cat that cannot move turns into cheese
 * when all its friends are stuck too. Don't get caught!
 */
@SuppressLint("ViewConstructor")
public final class RodentsRevenge extends WinApp {
    static final int N = 23;
    static final int EMPTY = 0, WALL = 1, BLOCK = 2, CHEESE = 3, SINK = 4;
    private static final String[] SPEEDS = {"S&nail", "&Slow", "&Medium", "&Fast", "&Blazing"};
    private static final float[] CAT_STEP = {1.2f, 0.9f, 0.65f, 0.45f, 0.3f};
    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1}, DY = {0, 1, 1, 1, 0, -1, -1, -1};

    final int[] grid = new int[N * N];
    final List<int[]> cats = new ArrayList<>();
    int mouse;
    int level = 1, lives = 3, score, wavesLeft;
    private int speed;
    boolean over = true, paused;
    private float catTimer, stuckTime, sunk, deadTime;
    private int stuckTicks;
    private final Random rng = new Random();

    private float cell;
    private final RectF board = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean holding;
    private float holdX, holdY, holdTimer;

    public RodentsRevenge(Context context, Host host) {
        super(context, host, "rodent");
        speed = prefs.getInt("speed", 2);
        setupLevel(prefs.getInt("startLevel", 1));
        over = true;
        setAnimating(true);
    }

    @Override
    public String title() {
        return "Rodent's Revenge";
    }

    @Override
    public void save() {
        prefs.edit().putInt("speed", speed).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New Game", "F2", "Start a new game", true, false, this::newGame));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the game", !over, false, () -> paused = !paused));
            items.add(new Item("&High Scores...", "", "Best scores", true, false, () -> messageBox("High Scores",
                    "Best score: " + prefs.getInt("best", 0) + "\nReached level " + prefs.getInt("bestLevel", 1), null)));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Rodent's Revenge", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Level...", "", "Choose a starting level", true, false, () -> prompt("Level",
                    "Start at level (1 to 50):", String.valueOf(level), true, 2, t -> {
                        if (!t.isEmpty()) {
                            int lv = Math.max(1, Math.min(50, Integer.parseInt(t)));
                            prefs.edit().putInt("startLevel", lv).apply();
                            score = 0;
                            lives = 3;
                            setupLevel(lv);
                        }
                    })));
            items.add(Item.sep());
            for (int i = 0; i < SPEEDS.length; i++) {
                int s = i;
                items.add(new Item(SPEEDS[i], "", "How fast the cats move", true, speed == i, () -> {
                    speed = s;
                    save();
                }));
            }
        }));
        addHelpMenu(menus, "You are the mouse. Push the blocks to trap the cats! When every cat on the board "
                + "is stuck so it cannot move, they all turn into cheese, which you can eat for 100 points each. Then "
                + "the next wave of cats arrives. Clear all the waves to finish the level.\n\nTouch and hold anywhere: "
                + "the mouse runs toward your finger, in any of eight directions. Pushing into a block pushes the whole "
                + "row of blocks behind it, as long as there is room at the end. The grey walls cannot be moved.\n\n"
                + "Watch out for sink holes on later levels: they hold you for a few seconds.",
                "Rodent's Revenge for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Level: " + level);
        panels.add("Lives: " + lives);
        panels.add("Score: " + score);
    }

    @Override
    protected String statusText() {
        return over ? "Tap to play" : paused ? "Paused" : "Cats to come: " + wavesLeft;
    }

    // ---------------------------------------------------------------- level

    void newGame() {
        score = 0;
        lives = 3;
        setupLevel(prefs.getInt("startLevel", 1));
    }

    void setupLevel(int lv) {
        level = lv;
        Random r = new Random(lv * 7919L);
        java.util.Arrays.fill(grid, EMPTY);
        for (int i = 0; i < N; i++) {
            grid[i] = grid[(N - 1) * N + i] = grid[i * N] = grid[i * N + N - 1] = WALL;
        }
        int lo = 3, hi = N - 4;
        for (int y = lo; y <= hi; y++) {
            for (int x = lo; x <= hi; x++) {
                grid[y * N + x] = BLOCK;
            }
        }
        int walls = Math.min(40, (lv - 1) * 2);
        for (int i = 0; i < walls; i++) {
            grid[(lo + r.nextInt(hi - lo + 1)) * N + lo + r.nextInt(hi - lo + 1)] = WALL;
        }
        int sinks = lv >= 6 ? Math.min(8, lv / 6) : 0;
        for (int i = 0; i < sinks; i++) {
            grid[(1 + r.nextInt(N - 2)) * N + 1 + r.nextInt(N - 2)] = SINK;
        }
        mouse = (N / 2) * N + N / 2;
        grid[mouse] = EMPTY;
        cats.clear();
        wavesLeft = 2 + (lv - 1) / 10;
        spawnWave();
        over = paused = false;
        sunk = deadTime = 0;
        stuckTicks = 0;
        invalidate();
    }

    private int catsPerWave() {
        return Math.min(4, 1 + (level - 1) / 8);
    }

    private void spawnWave() {
        wavesLeft--;
        for (int i = 0; i < catsPerWave(); i++) {
            for (int tries = 0; tries < 200; tries++) {
                int x = 1 + rng.nextInt(N - 2), y = 1 + rng.nextInt(N - 2);
                int c = y * N + x;
                boolean edge = x <= 2 || y <= 2 || x >= N - 3 || y >= N - 3;
                if (edge && grid[c] == EMPTY && Math.abs(x - mouse % N) + Math.abs(y - mouse / N) > 8 && catAt(c) < 0) {
                    cats.add(new int[] {c});
                    break;
                }
            }
        }
    }

    int catAt(int c) {
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i)[0] == c) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- mouse

    /** Moves the mouse one step in direction {@code d} (0-7, clockwise from east). */
    boolean step(int d) {
        if (over || paused || sunk > 0 || deadTime > 0) {
            return false;
        }
        int x = mouse % N + DX[d], y = mouse / N + DY[d];
        int t = y * N + x;
        if (catAt(t) >= 0) {
            caught();
            return false;
        }
        if (grid[t] == WALL) {
            return false;
        }
        if (grid[t] == BLOCK) {
            int ex = x, ey = y;
            while (grid[ey * N + ex] == BLOCK) {
                ex += DX[d];
                ey += DY[d];
            }
            int end = ey * N + ex;
            if (grid[end] != EMPTY && grid[end] != CHEESE || catAt(end) >= 0) {
                return false;
            }
            grid[end] = BLOCK;
            grid[t] = EMPTY;
            sound(Sfx.CLICK);
        }
        if (grid[t] == CHEESE) {
            grid[t] = EMPTY;
            score += 100;
            sound(Sfx.CHIME);
        }
        mouse = t;
        if (grid[t] == SINK) {
            sunk = 3f;
            sound(Sfx.DROP);
        }
        return true;
    }

    private void caught() {
        lives--;
        deadTime = 1.2f;
        sound(Sfx.LOSE);
        if (lives <= 0) {
            over = true;
            if (score > prefs.getInt("best", 0)) {
                prefs.edit().putInt("best", score).putInt("bestLevel", level).apply();
            }
            postDelayed(() -> messageBox("Rodent's Revenge", "The cats got you!\n\nScore: " + score, null), 500);
        }
    }

    // ---------------------------------------------------------------- cats

    private boolean free(int c) {
        return grid[c] == EMPTY || grid[c] == SINK;
    }

    private void moveCats() {
        boolean anyMoved = false;
        int mx = mouse % N, my = mouse / N;
        for (int[] cat : cats) {
            int cx = cat[0] % N, cy = cat[0] / N;
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int d = 0; d < 8; d++) {
                int nx = cx + DX[d], ny = cy + DY[d];
                int n = ny * N + nx;
                if (n == mouse) {
                    if (deadTime <= 0 && !over) {
                        caught();
                    }
                    best = -2;
                    break;
                }
                if (!free(n) || catAt(n) >= 0) {
                    continue;
                }
                double dist = Math.hypot(nx - mx, ny - my) + rng.nextDouble() * 0.3;
                if (dist < bestD) {
                    bestD = dist;
                    best = n;
                }
            }
            if (best >= 0) {
                cat[0] = best;
                anyMoved = true;
            } else if (best == -2) {
                anyMoved = true;
            }
        }
        // When every cat is boxed in, they all turn into cheese.
        if (!cats.isEmpty() && !anyMoved) {
            stuckTicks++;
            if (stuckTicks >= 2) {
                for (int[] cat : cats) {
                    grid[cat[0]] = CHEESE;
                }
                cats.clear();
                stuckTicks = 0;
                sound(Sfx.WIN);
                if (wavesLeft > 0) {
                    spawnWave();
                } else {
                    levelComplete();
                }
            }
        } else {
            stuckTicks = 0;
        }
    }

    private void levelComplete() {
        score += 500 + level * 100;
        paused = true;
        postDelayed(() -> messageBox("Rodent's Revenge", "Level " + level + " complete!\n\nScore: " + score, () -> {
            setupLevel(Math.min(50, level + 1));
        }), 400);
    }

    @Override
    protected void frame(float dt) {
        if (over || paused) {
            return;
        }
        if (deadTime > 0) {
            deadTime -= dt;
            if (deadTime <= 0 && !over) {
                // Respawn somewhere away from the cats.
                for (int tries = 0; tries < 500; tries++) {
                    int c = (1 + rng.nextInt(N - 2)) * N + 1 + rng.nextInt(N - 2);
                    boolean safe = grid[c] == EMPTY;
                    for (int[] cat : cats) {
                        safe &= Math.abs(cat[0] % N - c % N) + Math.abs(cat[0] / N - c / N) > 6;
                    }
                    if (safe) {
                        mouse = c;
                        break;
                    }
                }
            }
            return;
        }
        if (sunk > 0) {
            sunk -= dt;
        }
        catTimer += dt;
        if (catTimer >= CAT_STEP[speed]) {
            catTimer = 0;
            moveCats();
        }
        if (holding) {
            holdTimer -= dt;
            if (holdTimer <= 0) {
                holdTimer = 0.16f;
                int d = directionTo(holdX, holdY);
                if (d >= 0) {
                    step(d);
                }
            }
        }
    }

    private int directionTo(float x, float y) {
        float mx = board.left + (mouse % N + 0.5f) * cell, my = board.top + (mouse / N + 0.5f) * cell;
        float dx = x - mx, dy = y - my;
        if (Math.hypot(dx, dy) < cell * 0.6f) {
            return -1;
        }
        double a = Math.atan2(dy, dx);
        return (int) Math.round(a / (Math.PI / 4) + 8) % 8;
    }

    @Override
    protected void onHidden() {
        if (!over) {
            paused = true;
        }
        holding = false;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void layoutClient(RectF r) {
        cell = Math.min(r.width(), r.height()) / N;
        float s = cell * N;
        board.set(r.centerX() - s / 2, r.centerY() - s / 2, r.centerX() + s / 2, r.centerY() + s / 2);
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(0xFF808000);
        paint.setColor(0xFFFFFFC0);
        c.drawRect(board, paint);
        for (int i = 0; i < N * N; i++) {
            float x = board.left + (i % N) * cell, y = board.top + (i / N) * cell;
            RectF r = new RectF(x, y, x + cell, y + cell);
            switch (grid[i]) {
                case WALL:
                    paint.setColor(0xFF808080);
                    c.drawRect(r, paint);
                    paint.setColor(0xFF404040);
                    c.drawRect(x, y + cell * 0.8f, x + cell, y + cell, paint);
                    c.drawRect(x + cell * 0.8f, y, x + cell, y + cell, paint);
                    break;
                case BLOCK:
                    ui.raised(c, r);
                    paint.setColor(0xFFC0C000);
                    c.drawRect(x + cell * 0.2f, y + cell * 0.2f, x + cell * 0.8f, y + cell * 0.8f, paint);
                    break;
                case CHEESE:
                    drawCheese(c, r);
                    break;
                case SINK:
                    paint.setColor(0xFF402000);
                    c.drawOval(x + cell * 0.1f, y + cell * 0.25f, x + cell * 0.9f, y + cell * 0.75f, paint);
                    break;
                default:
                    break;
            }
        }
        for (int[] cat : cats) {
            drawCat(c, board.left + (cat[0] % N + 0.5f) * cell, board.top + (cat[0] / N + 0.5f) * cell);
        }
        if (deadTime <= 0 || ((int) (deadTime * 8) & 1) == 0) {
            drawMouse(c, board.left + (mouse % N + 0.5f) * cell, board.top + (mouse / N + 0.5f) * cell);
        }
        for (int i = 0; i < lives; i++) {
            drawMouse(c, board.left + (i + 0.5f) * cell, board.top + 0.5f * cell);
        }
        if (over || paused) {
            ui.withFont(20, true, () -> ui.centered(c, over ? "Tap to play" : "Paused", board, Win95.DARK));
        }
    }

    private void drawCheese(Canvas c, RectF r) {
        Path p = new Path();
        p.moveTo(r.left + r.width() * 0.1f, r.bottom - r.height() * 0.2f);
        p.lineTo(r.right - r.width() * 0.1f, r.bottom - r.height() * 0.2f);
        p.lineTo(r.right - r.width() * 0.1f, r.top + r.height() * 0.3f);
        p.close();
        paint.setColor(0xFFFFD000);
        c.drawPath(p, paint);
        paint.setColor(0xFFC09000);
        c.drawCircle(r.centerX() + r.width() * 0.15f, r.centerY() + r.height() * 0.1f, r.width() * 0.08f, paint);
    }

    private void drawCat(Canvas c, float cx, float cy) {
        float s = cell * 0.45f;
        paint.setColor(0xFFFF8000);
        c.drawCircle(cx, cy, s * 0.8f, paint);
        Path ears = new Path();
        ears.moveTo(cx - s * 0.75f, cy - s * 0.2f);
        ears.lineTo(cx - s * 0.6f, cy - s);
        ears.lineTo(cx - s * 0.15f, cy - s * 0.65f);
        ears.moveTo(cx + s * 0.75f, cy - s * 0.2f);
        ears.lineTo(cx + s * 0.6f, cy - s);
        ears.lineTo(cx + s * 0.15f, cy - s * 0.65f);
        c.drawPath(ears, paint);
        paint.setColor(0xFF00A000);
        c.drawCircle(cx - s * 0.3f, cy - s * 0.1f, s * 0.15f, paint);
        c.drawCircle(cx + s * 0.3f, cy - s * 0.1f, s * 0.15f, paint);
        paint.setColor(0xFF000000);
        c.drawCircle(cx, cy + s * 0.2f, s * 0.1f, paint);
    }

    private void drawMouse(Canvas c, float cx, float cy) {
        float s = cell * 0.45f;
        paint.setColor(0xFF606060);
        c.drawCircle(cx - s * 0.55f, cy - s * 0.55f, s * 0.35f, paint);
        c.drawCircle(cx + s * 0.55f, cy - s * 0.55f, s * 0.35f, paint);
        c.drawCircle(cx, cy, s * 0.7f, paint);
        paint.setColor(0xFFFFA0A0);
        c.drawCircle(cx - s * 0.55f, cy - s * 0.55f, s * 0.2f, paint);
        c.drawCircle(cx + s * 0.55f, cy - s * 0.55f, s * 0.2f, paint);
        paint.setColor(0xFF000000);
        c.drawCircle(cx - s * 0.25f, cy - s * 0.1f, s * 0.1f, paint);
        c.drawCircle(cx + s * 0.25f, cy - s * 0.1f, s * 0.1f, paint);
        paint.setColor(0xFFFF6080);
        c.drawCircle(cx, cy + s * 0.3f, s * 0.12f, paint);
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void clientTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (over) {
                    newGame();
                    return;
                }
                if (paused) {
                    paused = false;
                    return;
                }
                holding = true;
                holdX = e.getX();
                holdY = e.getY();
                holdTimer = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                holdX = e.getX();
                holdY = e.getY();
                break;
            default:
                holding = false;
                break;
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        int d = -1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: d = 0; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: d = 2; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: d = 4; break;
            case KeyEvent.KEYCODE_DPAD_UP: d = 6; break;
            case KeyEvent.KEYCODE_F2: newGame(); return true;
            case KeyEvent.KEYCODE_F3: paused = !paused; return true;
            default: return false;
        }
        step(d);
        return true;
    }
}
