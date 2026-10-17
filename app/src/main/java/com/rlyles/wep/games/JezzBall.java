package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.Sprites;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * JezzBall: build walls to trap the bouncing atoms in ever smaller spaces. Capture 75% of the
 * field to finish a level. An atom hitting a wall while it is being built costs a life.
 */
@SuppressLint("ViewConstructor")
public final class JezzBall extends WinApp {
    static final int GW = 40, GH = 30;
    static final int OPEN = 0, FILLED = 1, BUILD_A = 2, BUILD_B = 3;

    final int[] grid = new int[GW * GH];
    final List<float[]> atoms = new ArrayList<>();
    int level, lives, score, timeLeft;
    boolean horizontal = true, over = true, levelDone, paused, fast;
    private final Random rng = new Random();
    private float spin;

    /** The wall being built: start cell, orientation, and how far each half has grown (cells). */
    private boolean building, aAlive, bAlive, aDone, bDone;
    private int wx, wy;
    private boolean wHorizontal;
    private float aLen, bLen;

    private float cell;
    private final RectF field = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float downX, downY;
    private final float slop;

    public JezzBall(Context context, Host host) {
        super(context, host, "jezzball");
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        fast = prefs.getBoolean("fast", false);
        setupLevel(1);
        over = true;
        setAnimating(true);
    }

    @Override
    public String title() {
        return "JezzBall";
    }

    @Override
    public void save() {
        prefs.edit().putBoolean("fast", fast).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New Game", "F2", "Start at level 1", true, false, this::newGame));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the game", !over, false, () -> paused = !paused));
            items.add(new Item("&High Scores...", "", "Best scores", true, false, this::highScores));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit JezzBall", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Slow", "", "Slower atoms", true, !fast, () -> fast = false));
            items.add(new Item("&Fast", "", "Faster atoms", true, fast, () -> fast = true));
        }));
        addHelpMenu(menus, "Atoms bounce around the field. Build walls to fence them into smaller and smaller "
                + "spaces: any area with no atoms in it is captured. Capture 75% of the field to go on to the next "
                + "level, which has one more atom.\n\nTap to build a wall where you tap. Swipe across or up and down "
                + "to choose a horizontal or vertical wall (it is remembered for the next tap).\n\nA wall grows out in "
                + "both directions. If an atom hits a half while it is still growing, that half vanishes and you lose "
                + "a life. Each level gives you as many lives as it has atoms, and a time limit.",
                "JezzBall for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Level: " + level);
        panels.add("Lives: " + lives);
        panels.add("Area: " + percent() + "%");
        panels.add("Time: " + timeLeft);
        panels.add("Score: " + score);
    }

    @Override
    protected String statusText() {
        return horizontal ? "Wall: ↔" : "Wall: ↕";
    }

    // ---------------------------------------------------------------- game

    void newGame() {
        score = 0;
        setupLevel(1);
    }

    void setupLevel(int lv) {
        level = lv;
        lives = lv + 1;
        timeLeft = 20 + 20 * lv;
        java.util.Arrays.fill(grid, OPEN);
        for (int x = 0; x < GW; x++) {
            grid[x] = grid[(GH - 1) * GW + x] = FILLED;
        }
        for (int y = 0; y < GH; y++) {
            grid[y * GW] = grid[y * GW + GW - 1] = FILLED;
        }
        atoms.clear();
        for (int i = 0; i < lv + 1; i++) {
            float x = 2 + rng.nextFloat() * (GW - 4), y = 2 + rng.nextFloat() * (GH - 4);
            atoms.add(new float[] {x, y, rng.nextBoolean() ? 1 : -1, rng.nextBoolean() ? 1 : -1});
        }
        building = false;
        over = levelDone = paused = false;
        invalidate();
    }

    int percent() {
        int open = 0, inner = (GW - 2) * (GH - 2);
        for (int y = 1; y < GH - 1; y++) {
            for (int x = 1; x < GW - 1; x++) {
                if (grid[y * GW + x] != FILLED) {
                    open++;
                }
            }
        }
        return 100 - Math.round(100f * open / inner);
    }

    /** Starts a wall at a cell; returns false if there is already one or the cell is not open. */
    boolean startWall(int cx, int cy, boolean horiz) {
        if (over || levelDone || building || cx < 1 || cy < 1 || cx >= GW - 1 || cy >= GH - 1
                || grid[cy * GW + cx] != OPEN) {
            return false;
        }
        building = aAlive = bAlive = true;
        aDone = bDone = false;
        wx = cx;
        wy = cy;
        wHorizontal = horiz;
        aLen = bLen = 0;
        grid[cy * GW + cx] = BUILD_A;
        sound(Sfx.CLICK);
        return true;
    }

    private int wallCell(boolean halfA, int i) {
        int dx = wHorizontal ? (halfA ? -i : i) : 0, dy = wHorizontal ? 0 : (halfA ? -i : i);
        int x = wx + dx, y = wy + dy;
        return x < 0 || y < 0 || x >= GW || y >= GH ? -1 : y * GW + x;
    }

    private void growWall(float dt) {
        float speed = 14f;
        if (aAlive && !aDone) {
            aDone = grow(true, aLen, aLen + speed * dt);
            aLen += speed * dt;
        }
        if (bAlive && !bDone) {
            bDone = grow(false, bLen, bLen + speed * dt);
            bLen += speed * dt;
        }
        if ((!aAlive || aDone) && (!bAlive || bDone)) {
            finishWall();
        }
    }

    /** Extends one half from length {@code from} to {@code to}; returns true once it meets a filled cell. */
    private boolean grow(boolean halfA, float from, float to) {
        for (int i = (int) from + 1; i <= (int) to; i++) {
            int c = wallCell(halfA, i);
            if (c < 0 || grid[c] == FILLED) {
                return true;
            }
            grid[c] = halfA ? BUILD_A : BUILD_B;
        }
        return false;
    }

    private void breakHalf(boolean halfA) {
        for (int i = halfA ? 0 : 1; ; i++) {
            int c = wallCell(halfA, i);
            if (c < 0 || grid[c] != (halfA ? BUILD_A : BUILD_B)) {
                break;
            }
            grid[c] = OPEN;
        }
        if (halfA) {
            aAlive = false;
        } else {
            bAlive = false;
        }
        lives--;
        sound("jezzdead.wav", Sfx.BOOM);
        if (lives <= 0) {
            building = false;
            clearBuilding();
            over = true;
            sound("gamelost.wav", Sfx.LOSE);
            post(this::checkHighScore);
        }
    }

    private void clearBuilding() {
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == BUILD_A || grid[i] == BUILD_B) {
                grid[i] = OPEN;
            }
        }
    }

    private void finishWall() {
        building = false;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == BUILD_A && aAlive || grid[i] == BUILD_B && bAlive) {
                grid[i] = FILLED;
            } else if (grid[i] == BUILD_A || grid[i] == BUILD_B) {
                grid[i] = OPEN;
            }
        }
        captureEmptyAreas();
        if (percent() >= 75) {
            levelDone = true;
            int bonus = (percent() - 75) * 2 * level * 10 + timeLeft * level * 10 + lives * 100 * level;
            score += percent() * level * 10 + bonus;
            sound("winlevel.wav", Sfx.WIN);
            postDelayed(() -> messageBox("JezzBall", "Level " + level + " cleared!\n\nArea captured: " + percent()
                    + "%\nScore: " + score, () -> setupLevel(level + 1)), 300);
        } else {
            sound("gap.wav", Sfx.BLIP);
        }
    }

    /** Fills every open region that has no atom in it. */
    void captureEmptyAreas() {
        boolean[] reach = new boolean[grid.length];
        int[] stack = new int[grid.length];
        for (float[] a : atoms) {
            int start = (int) a[1] * GW + (int) a[0];
            if (grid[start] == FILLED || reach[start]) {
                continue;
            }
            int sp = 0;
            stack[sp++] = start;
            reach[start] = true;
            while (sp > 0) {
                int c = stack[--sp];
                int[] nb = {c - 1, c + 1, c - GW, c + GW};
                for (int n : nb) {
                    if (n >= 0 && n < grid.length && !reach[n] && grid[n] != FILLED) {
                        reach[n] = true;
                        stack[sp++] = n;
                    }
                }
            }
        }
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == OPEN && !reach[i]) {
                grid[i] = FILLED;
            }
        }
    }

    @Override
    protected void frame(float dt) {
        spin += dt * 6;
        if (over || levelDone || paused) {
            return;
        }
        float speed = fast ? 9f : 6f;
        float r = 0.45f;
        for (float[] a : atoms) {
            float nx = a[0] + a[2] * speed * dt, ny = a[1] + a[3] * speed * dt;
            if (solidAt(nx + a[2] * r, a[1])) {
                a[2] = -a[2];
                nx = a[0];
                sound("bounce.wav", Sfx.BOUNCE);
            }
            if (solidAt(a[0], ny + a[3] * r)) {
                a[3] = -a[3];
                ny = a[1];
                sound("bounce.wav", Sfx.BOUNCE);
            }
            if (solidAt(nx + a[2] * r, ny + a[3] * r) && !solidAt(nx + a[2] * r, a[1]) && !solidAt(a[0], ny + a[3] * r)) {
                a[2] = -a[2];
                a[3] = -a[3];
                nx = a[0];
                ny = a[1];
            }
            a[0] = nx;
            a[1] = ny;
            if (building) {
                for (float ox = -r; ox <= r; ox += r) {
                    for (float oy = -r; oy <= r; oy += r) {
                        int c = cellIndex(a[0] + ox, a[1] + oy);
                        if (c >= 0 && grid[c] == BUILD_A && aAlive && !aDone) {
                            breakHalf(true);
                        } else if (c >= 0 && grid[c] == BUILD_B && bAlive && !bDone) {
                            breakHalf(false);
                        }
                    }
                }
            }
        }
        if (building && !over) {
            growWall(dt);
        }
    }

    private int cellIndex(float x, float y) {
        int cx = (int) x, cy = (int) y;
        return cx < 0 || cy < 0 || cx >= GW || cy >= GH ? -1 : cy * GW + cx;
    }

    /** Atoms bounce off finished walls; growing ones are handled separately (they break). */
    private boolean solidAt(float x, float y) {
        int c = cellIndex(x, y);
        return c < 0 || grid[c] == FILLED;
    }

    @Override
    protected void tick() {
        if (over || levelDone || paused || !isActive()) {
            return;
        }
        timeLeft--;
        if (timeLeft <= 0) {
            over = true;
            building = false;
            clearBuilding();
            sound("gamelost.wav", Sfx.LOSE);
            messageBox("JezzBall", "Time's up!\n\nFinal score: " + score, this::checkHighScore);
        }
        invalidate();
    }

    @Override
    protected void onHidden() {
        if (!over) {
            paused = true;
        }
    }

    private void checkHighScore() {
        if (score <= prefs.getInt("best", 0)) {
            return;
        }
        prompt("JezzBall", "A new high score! Enter your name:", prefs.getString("bestName", "Player"), false, 16, n -> {
            prefs.edit().putInt("best", score).putString("bestName", n.isEmpty() ? "Player" : n)
                    .putInt("bestLevel", level).apply();
            highScores();
        });
    }

    private void highScores() {
        int best = prefs.getInt("best", 0);
        messageBox("High Scores", best == 0 ? "No high score yet." : prefs.getString("bestName", "") + "\n\nScore: "
                + best + "\nLevel: " + prefs.getInt("bestLevel", 1), null);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void layoutClient(RectF r) {
        cell = Math.min(r.width() / GW, r.height() / GH);
        float w = cell * GW, h = cell * GH;
        field.set(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2);
    }

    /**
     * The original's sprite strip from the user's JEZZBALL.EXE: 16x12 tiles (field, captured,
     * and the red and blue walls being built) and eleven frames of a spinning atom.
     */
    private static final class Art {
        Bitmap open, filled, wallA, wallB;
        final Bitmap[] atom = new Bitmap[11];
    }

    private static Art art() {
        return Media.get().cached("jezz.art", m -> {
            Bitmap strip = m.bitmap("jezzball.exe", "JEZZ-NEW");
            if (strip == null || strip.getWidth() < 288 || strip.getHeight() < 24) {
                return null;
            }
            Art a = new Art();
            a.open = Sprites.slice(strip, 0, 0, 16, 12);
            a.filled = Sprites.slice(strip, 256, 12, 16, 12);
            a.wallA = Sprites.slice(strip, 272, 0, 16, 12);
            a.wallB = Sprites.slice(strip, 272, 12, 16, 12);
            for (int i = 0; i < 11; i++) {
                a.atom[i] = Sprites.slice(strip, 16 + i * 16, 0, 16, 12);
            }
            return a;
        });
    }

    private void drawWithArt(Canvas c, Art a) {
        c.drawColor(0xFF000000);
        for (int i = 0; i < grid.length; i++) {
            int v = grid[i];
            float x = field.left + (i % GW) * cell, y = field.top + (i / GW) * cell;
            Sprites.draw(c, v == OPEN ? a.open : v == FILLED ? a.filled : v == BUILD_A ? a.wallA : a.wallB,
                    x, y, cell, cell);
        }
        int frame = (int) (spin / (2 * Math.PI) * 11);
        for (int n = 0; n < atoms.size(); n++) {
            float[] at = atoms.get(n);
            float r = cell * 0.75f, cx = field.left + at[0] * cell, cy = field.top + at[1] * cell;
            Sprites.draw(c, a.atom[((frame + n * 3) % 11 + 11) % 11], cx - r, cy - r, 2 * r, 2 * r);
        }
        if (over || paused) {
            ui.withFont(20, true, () -> ui.centered(c, over ? "Tap to play" : "Paused", field, 0xFFFFFFFF));
        }
    }

    @Override
    protected void drawClient(Canvas c) {
        Art art = art();
        if (art != null) {
            drawWithArt(c, art);
            return;
        }
        c.drawColor(0xFF000000);
        paint.setColor(0xFF202020);
        c.drawRect(field, paint);
        paint.setColor(0xFF303030);
        for (int x = 0; x <= GW; x++) {
            c.drawLine(field.left + x * cell, field.top, field.left + x * cell, field.bottom, paint);
        }
        for (int y = 0; y <= GH; y++) {
            c.drawLine(field.left, field.top + y * cell, field.right, field.top + y * cell, paint);
        }
        for (int i = 0; i < grid.length; i++) {
            int v = grid[i];
            if (v == OPEN) {
                continue;
            }
            float x = field.left + (i % GW) * cell, y = field.top + (i / GW) * cell;
            int color = v == FILLED ? 0xFF4060A0 : v == BUILD_A ? 0xFFFF2020 : 0xFF2040FF;
            paint.setColor(color);
            c.drawRect(x, y, x + cell, y + cell, paint);
            if (v == FILLED) {
                paint.setColor(0xFF6080C0);
                c.drawRect(x, y, x + cell, y + Math.max(1, cell / 6), paint);
            }
        }
        for (float[] a : atoms) {
            drawAtom(c, field.left + a[0] * cell, field.top + a[1] * cell, cell * 0.75f);
        }
        if (over || paused) {
            ui.withFont(20, true, () -> ui.centered(c, over ? "Tap to play" : "Paused", field, 0xFFFFFFFF));
        }
    }

    private void drawAtom(Canvas c, float cx, float cy, float r) {
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r, paint);
        paint.setColor(0xFFFF0000);
        float angle = (float) Math.toDegrees(spin);
        c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), angle, 90, true, paint);
        c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), angle + 180, 90, true, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, r / 8));
        paint.setColor(0xFF000000);
        c.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            return;
        }
        if (e.getActionMasked() != MotionEvent.ACTION_UP) {
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
        float dx = x - downX, dy = y - downY;
        if (Math.hypot(dx, dy) > Math.max(slop, cell)) {
            horizontal = Math.abs(dx) > Math.abs(dy);
        }
        float sx = Math.hypot(dx, dy) > Math.max(slop, cell) ? downX : x;
        float sy = Math.hypot(dx, dy) > Math.max(slop, cell) ? downY : y;
        startWall((int) ((sx - field.left) / cell), (int) ((sy - field.top) / cell), horizontal);
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            newGame();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_F3) {
            paused = !paused;
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
            horizontal = !horizontal;
            return true;
        }
        return false;
    }

    public boolean buildingForTest() {
        return building;
    }
}
