package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * TetraVex: move the square tiles from the right into the grid on the left so that every pair of
 * touching edges shows the same number.
 */
@SuppressLint("ViewConstructor")
public final class TetraVex extends WinApp {
    /** Resistor colour code, one colour per digit. */
    private static final int[] COLORS = {0xFF000000, 0xFF8B4513, 0xFFFF0000, 0xFFFF8C00, 0xFFFFFF00,
            0xFF00A000, 0xFF0000FF, 0xFF8000C0, 0xFF808080, 0xFFFFFFFF};
    private static final String[][] DIGITS = {
            {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"},
            {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"},
            {"α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ"},
            {"♠", "♥", "♦", "♣", "★", "●", "▲", "■", "☺", "♪"}};
    private static final String[] DIGIT_NAMES = {"&Numbers", "&Letters", "&Greek", "S&ymbols"};

    /** A tile's edges: top, right, bottom, left. */
    static final class Tile {
        final int[] e = new int[4];
        int solution;
    }

    int n;
    private int digits;
    private boolean timerOn, paused;
    final Tile[] left = new Tile[36], right = new Tile[36];
    private final Random rng = new Random();
    int seconds;
    boolean won, started;

    private float size;
    private final RectF leftRect = new RectF(), rightRect = new RectF();
    private Tile dragging;
    private boolean dragFromLeft;
    private int dragFrom;
    private float dragX, dragY, grabX, grabY;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TetraVex(Context context, Host host) {
        super(context, host, "tetravex");
        n = prefs.getInt("size", 3);
        digits = prefs.getInt("digits", 0);
        timerOn = prefs.getBoolean("timer", true);
        newGame();
    }

    @Override
    public String title() {
        return "TetraVex";
    }

    @Override
    public void save() {
        prefs.edit().putInt("size", n).putInt("digits", digits).putBoolean("timer", timerOn).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New Game", "F2", "Start a new puzzle", true, false, this::newGame));
            items.add(new Item("High &Scores...", "", "Best times", true, false, this::highScores));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the timer", !won, false, () -> {
                paused = !paused;
                invalidate();
            }));
            items.add(new Item("&Hint", "Ctrl+H", "Place one tile correctly", !won, false, this::hint));
            items.add(new Item("Sol&ve", "Ctrl+S", "Show the solution", !won, false, this::solve));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit TetraVex", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Timer", "", "Time each puzzle", true, timerOn, () -> timerOn = !timerOn));
            items.add(Item.sep());
            for (int s = 2; s <= 6; s++) {
                int sz = s;
                items.add(new Item("&" + s + "x" + s, "", "Puzzle size " + s + " by " + s, true, n == s, () -> {
                    n = sz;
                    save();
                    newGame();
                    relayout();
                }));
            }
            items.add(Item.sep());
            for (int d = 0; d < DIGIT_NAMES.length; d++) {
                int dd = d;
                items.add(new Item(DIGIT_NAMES[d], "", "Show the edges as " + DIGIT_NAMES[d].replace("&", "").toLowerCase(java.util.Locale.US),
                        true, digits == d, () -> {
                            digits = dd;
                            save();
                        }));
            }
        }));
        addHelpMenu(menus, "Move every tile from the right-hand grid into the left-hand grid so that wherever "
                + "two tiles touch, the numbers on the touching edges match.\n\nDrag a tile to move it. A tile "
                + "will only go where its edges match the tiles already beside it. Drag tiles back to the right "
                + "to rearrange them.\n\nThe faster you finish, the better.", "TetraVex for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        if (timerOn) {
            panels.add("Time: " + seconds);
        }
    }

    @Override
    protected String statusText() {
        return paused ? "Paused" : won ? "Solved!" : "";
    }

    @Override
    protected void tick() {
        if (timerOn && started && !won && !paused) {
            seconds++;
            invalidate();
        }
    }

    // ---------------------------------------------------------------- game

    void newGame() {
        int[][] h = new int[n][n + 1], v = new int[n + 1][n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x <= n; x++) {
                h[y][x] = rng.nextInt(10);
            }
        }
        for (int y = 0; y <= n; y++) {
            for (int x = 0; x < n; x++) {
                v[y][x] = rng.nextInt(10);
            }
        }
        List<Tile> tiles = new ArrayList<>();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                Tile t = new Tile();
                t.e[0] = v[y][x];
                t.e[2] = v[y + 1][x];
                t.e[3] = h[y][x];
                t.e[1] = h[y][x + 1];
                t.solution = y * n + x;
                tiles.add(t);
            }
        }
        Collections.shuffle(tiles, rng);
        java.util.Arrays.fill(left, null);
        java.util.Arrays.fill(right, null);
        for (int i = 0; i < tiles.size(); i++) {
            right[i] = tiles.get(i);
        }
        seconds = 0;
        won = started = paused = false;
        invalidate();
    }

    /** Whether {@code t} may sit at {@code pos} of the left grid, given its neighbours there. */
    boolean fits(Tile t, int pos) {
        int x = pos % n, y = pos / n;
        Tile up = y > 0 ? left[pos - n] : null, down = y < n - 1 ? left[pos + n] : null;
        Tile l = x > 0 ? left[pos - 1] : null, r = x < n - 1 ? left[pos + 1] : null;
        return (up == null || up.e[2] == t.e[0]) && (down == null || down.e[0] == t.e[2])
                && (l == null || l.e[1] == t.e[3]) && (r == null || r.e[3] == t.e[1]);
    }

    private void checkWin() {
        for (int i = 0; i < n * n; i++) {
            if (left[i] == null) {
                return;
            }
        }
        won = true;
        sound(Sfx.WIN);
        int best = prefs.getInt("best" + n, 9999);
        if (timerOn && seconds < best) {
            int time = seconds;
            post(() -> prompt("TetraVex", "New best time for " + n + "x" + n + "! Enter your name:",
                    prefs.getString("name" + n, "Anonymous"), false, 20, name -> {
                        prefs.edit().putInt("best" + n, time).putString("name" + n, name).apply();
                        highScores();
                    }));
        } else {
            messageBox("TetraVex", "Puzzle solved in " + seconds + " seconds!", null);
        }
    }

    private void highScores() {
        StringBuilder sb = new StringBuilder();
        for (int s = 2; s <= 6; s++) {
            int b = prefs.getInt("best" + s, 9999);
            sb.append(s).append('x').append(s).append(":   ")
                    .append(b == 9999 ? "--" : b + " seconds   " + prefs.getString("name" + s, "")).append('\n');
        }
        messageBox("High Scores", sb.toString().trim(), null);
    }

    private void hint() {
        for (int i = 0; i < n * n; i++) {
            Tile t = right[i];
            if (t == null) {
                continue;
            }
            // A wrong tile in this one's place swaps over to the right.
            right[i] = left[t.solution];
            left[t.solution] = t;
            started = true;
            // Send back any tile that no longer fits beside it.
            for (int q = 0; q < n * n; q++) {
                if (left[q] != null && !fits(left[q], q)) {
                    moveBack(q);
                }
            }
            sound(Sfx.CHIME);
            checkWin();
            invalidate();
            return;
        }
    }

    private void moveBack(int pos) {
        for (int i = 0; i < n * n; i++) {
            if (right[i] == null) {
                right[i] = left[pos];
                left[pos] = null;
                return;
            }
        }
    }

    private void solve() {
        List<Tile> all = new ArrayList<>();
        for (int i = 0; i < n * n; i++) {
            if (left[i] != null) {
                all.add(left[i]);
            }
            if (right[i] != null) {
                all.add(right[i]);
            }
        }
        java.util.Arrays.fill(left, null);
        java.util.Arrays.fill(right, null);
        for (Tile t : all) {
            left[t.solution] = t;
        }
        won = true;
        invalidate();
    }

    // ---------------------------------------------------------------- layout and drawing

    @Override
    protected void layoutClient(RectF r) {
        float pad = 12 * dp;
        size = Math.min((r.width() - 3 * pad) / (2 * n), (r.height() - 2 * pad) / n);
        float total = 2 * n * size + pad;
        float x = r.centerX() - total / 2, y = r.centerY() - n * size / 2;
        leftRect.set(x, y, x + n * size, y + n * size);
        rightRect.set(x + n * size + pad, y, x + 2 * n * size + pad, y + n * size);
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(Win95.FACE);
        ui.sunken(c, inflate(leftRect), 0xFF808080);
        ui.sunken(c, inflate(rightRect), 0xFF808080);
        if (paused) {
            ui.withFont(20, true, () -> ui.centered(c, "Paused", client, 0xFF000000));
            return;
        }
        for (int i = 0; i < n * n; i++) {
            if (left[i] != null && left[i] != dragging) {
                drawTile(c, left[i], leftRect.left + (i % n) * size, leftRect.top + (i / n) * size);
            }
            if (right[i] != null && right[i] != dragging) {
                drawTile(c, right[i], rightRect.left + (i % n) * size, rightRect.top + (i / n) * size);
            }
        }
        if (dragging != null) {
            drawTile(c, dragging, dragX, dragY);
        }
    }

    private RectF inflate(RectF r) {
        return new RectF(r.left - 3 * dp, r.top - 3 * dp, r.right + 3 * dp, r.bottom + 3 * dp);
    }

    private void drawTile(Canvas c, Tile t, float x, float y) {
        float s = size, cx = x + s / 2, cy = y + s / 2;
        float[][] corners = {{x, y, x + s, y}, {x + s, y, x + s, y + s}, {x + s, y + s, x, y + s}, {x, y + s, x, y}};
        float[][] labelPos = {{cx, y + s * 0.2f}, {x + s * 0.8f, cy}, {cx, y + s * 0.8f}, {x + s * 0.2f, cy}};
        ui.raised(c, new RectF(x, y, x + s, y + s));
        // Triangles go inside the raised bevel.
        float b = 2 * ui.px;
        c.save();
        c.clipRect(x + b, y + b, x + s - b, y + s - b);
        for (int side = 0; side < 4; side++) {
            Path p = new Path();
            p.moveTo(cx, cy);
            p.lineTo(corners[side][0], corners[side][1]);
            p.lineTo(corners[side][2], corners[side][3]);
            p.close();
            paint.setColor(COLORS[t.e[side]]);
            c.drawPath(p, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF000000);
        c.drawLine(x, y, x + s, y + s, paint);
        c.drawLine(x + s, y, x, y + s, paint);
        paint.setStyle(Paint.Style.FILL);
        c.restore();
        paint.setTextSize(s * 0.24f);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        for (int side = 0; side < 4; side++) {
            int d = t.e[side];
            paint.setColor(d == 0 || d == 1 || d == 6 || d == 7 ? 0xFFFFFFFF : 0xFF000000);
            c.drawText(DIGITS[digits][d], labelPos[side][0], labelPos[side][1] - (fm.ascent + fm.descent) / 2, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ---------------------------------------------------------------- touch

    private int slotAt(RectF grid, float x, float y) {
        if (!grid.contains(x, y)) {
            return -1;
        }
        int gx = (int) ((x - grid.left) / size), gy = (int) ((y - grid.top) / size);
        return gx >= 0 && gy >= 0 && gx < n && gy < n ? gy * n + gx : -1;
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        if (won || paused) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP && paused) {
                paused = false;
            }
            return;
        }
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int l = slotAt(leftRect, x, y), r = slotAt(rightRect, x, y);
                if (l >= 0 && left[l] != null) {
                    dragging = left[l];
                    dragFromLeft = true;
                    dragFrom = l;
                    grabX = x - (leftRect.left + (l % n) * size);
                    grabY = y - (leftRect.top + (l / n) * size);
                } else if (r >= 0 && right[r] != null) {
                    dragging = right[r];
                    dragFromLeft = false;
                    dragFrom = r;
                    grabX = x - (rightRect.left + (r % n) * size);
                    grabY = y - (rightRect.top + (r / n) * size);
                }
                dragX = x - grabX;
                dragY = y - grabY;
                break;
            }
            case MotionEvent.ACTION_MOVE:
                dragX = x - grabX;
                dragY = y - grabY;
                break;
            case MotionEvent.ACTION_UP:
                if (dragging != null) {
                    drop(dragX + size / 2, dragY + size / 2);
                }
                dragging = null;
                break;
            default:
                dragging = null;
                break;
        }
    }

    private void drop(float cx, float cy) {
        Tile t = dragging;
        int l = slotAt(leftRect, cx, cy), r = slotAt(rightRect, cx, cy);
        Tile[] src = dragFromLeft ? left : right;
        src[dragFrom] = null;
        if (l >= 0 && left[l] == null && fits(t, l)) {
            left[l] = t;
            started = true;
            sound(Sfx.CLICK);
            checkWin();
        } else if (r >= 0 && right[r] == null) {
            right[r] = t;
        } else {
            src[dragFrom] = t;
            if (l >= 0) {
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

    /** Test hook: places tile {@code i} of the right grid at {@code pos} if it fits. */
    boolean place(int i, int pos) {
        if (right[i] == null || left[pos] != null || !fits(right[i], pos)) {
            return false;
        }
        left[pos] = right[i];
        right[i] = null;
        checkWin();
        return true;
    }
}
