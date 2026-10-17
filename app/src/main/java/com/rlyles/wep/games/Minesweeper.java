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

import java.util.List;
import java.util.Random;

/** Minesweeper: tap to uncover, long-press to flag, tap a number to clear around it. */
@SuppressLint("ViewConstructor")
public final class Minesweeper extends WinApp {
    static final int HIDDEN = 0, FLAG = 1, QUESTION = 2, OPEN = 3;
    private static final int[][] LEVELS = {{9, 9, 10}, {16, 16, 40}, {30, 16, 99}};
    private static final String[] LEVEL_NAMES = {"Beginner", "Intermediate", "Expert"};
    private static final int[] NUMBER_COLORS = {0, 0xFF0000FF, 0xFF008000, 0xFFFF0000, 0xFF000080, 0xFF800000,
            0xFF008080, 0xFF000000, 0xFF808080};

    int cols, rows, mines, level;
    boolean[] mine;
    int[] state;
    private boolean marks, color;
    boolean lost, won, started;
    private int seconds, exploded = -1;
    private final Random rng = new Random();

    // geometry
    private float cell, k;
    private final RectF panel = new RectF(), field = new RectF(), face = new RectF(), counter = new RectF(),
            timer = new RectF();
    private boolean facePressed, fieldPressed;
    private int pressedCell = -1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Minesweeper(Context context, Host host) {
        super(context, host, "winmine");
        marks = prefs.getBoolean("marks", true);
        color = prefs.getBoolean("color", true);
        level = prefs.getInt("level", 0);
        cols = prefs.getInt("cols", 9);
        rows = prefs.getInt("rows", 9);
        mines = prefs.getInt("mines", 10);
        newGame();
    }

    @Override
    public String title() {
        return "Minesweeper";
    }

    @Override
    public void save() {
        prefs.edit().putBoolean("marks", marks).putBoolean("color", color).putInt("level", level)
                .putInt("cols", cols).putInt("rows", rows).putInt("mines", mines).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Start a new game", true, false, this::newGame));
            items.add(Item.sep());
            for (int i = 0; i < 3; i++) {
                int lv = i;
                items.add(new Item("&" + LEVEL_NAMES[i], "", LEVEL_NAMES[i] + " level", true, level == i,
                        () -> setLevel(lv, LEVELS[lv][0], LEVELS[lv][1], LEVELS[lv][2])));
            }
            items.add(new Item("&Custom...", "", "Choose the size and mines", true, level == 3, this::custom));
            items.add(Item.sep());
            items.add(new Item("&Marks (?)", "", "Long-press twice for a question mark", true, marks, () -> marks = !marks));
            items.add(new Item("Co&lor", "", "Colour or black and white", true, color, () -> color = !color));
            items.add(Item.sep());
            items.add(new Item("&Best Times...", "", "Fastest times", true, false, this::bestTimes));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Minesweeper", true, false, () -> host.closeApp(this)));
        }));
        addHelpMenu(menus, "Find the mines without uncovering any of them.\n\nTap a square to uncover it. A number "
                + "tells you how many mines touch that square. Long-press a square to flag it as a mine (with Marks on, "
                + "long-press again for a question mark).\n\nTap an uncovered number that already has that many flags "
                + "around it to uncover all its other neighbours at once.\n\nTap the face to start a new game. "
                + "Your first square is never a mine.", "Minesweeper for the Entertainment Pack.");
    }

    @Override
    protected boolean wantsLongPress() {
        return true;
    }

    private void setLevel(int lv, int c, int r, int m) {
        level = lv;
        cols = c;
        rows = r;
        mines = Math.min(m, c * r - 1);
        save();
        newGame();
        relayout();
    }

    private void custom() {
        Win95.Dialog d = new Win95.Dialog("Custom Field", 260, 170);
        Win95.Edit[] fields = new Win95.Edit[3];
        String[] labels = {"&Height:", "&Width:", "&Mines:"};
        int[] values = {rows, cols, mines};
        for (int i = 0; i < 3; i++) {
            d.add(new Win95.Text(labels[i], false)).at(12, 14 + i * 40, 70, 30);
            Win95.Edit e = d.add(new Win95.Edit(String.valueOf(values[i])));
            e.at(84, 8 + i * 40, 70, 32);
            int idx = i;
            e.onEdit = () -> prompt("Custom Field", labels[idx].replace("&", ""), e.value, true, 3, v -> e.value = v);
            fields[i] = e;
        }
        d.add(new Win95.Button("OK", true, () -> {
            try {
                int r = Math.max(8, Math.min(24, Integer.parseInt(fields[0].value)));
                int c = Math.max(8, Math.min(30, Integer.parseInt(fields[1].value)));
                int m = Math.max(10, Math.min((r - 1) * (c - 1), Integer.parseInt(fields[2].value)));
                closeDialog(d);
                setLevel(3, c, r, m);
            } catch (NumberFormatException e) {
                closeDialog(d);
            }
        })).at(168, 12, 80, 34);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(168, 56, 80, 34);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    private void bestTimes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(LEVEL_NAMES[i]).append(":   ").append(prefs.getInt("best" + i, 999)).append(" seconds   ")
                    .append(prefs.getString("name" + i, "Anonymous")).append('\n');
        }
        Win95.Dialog d = new Win95.Dialog("Fastest Mine Sweepers", 320, 150);
        d.add(new Win95.Text(sb.toString().trim(), false)).at(14, 12, 296, 80);
        d.add(new Win95.Button("&Reset Scores", false, () -> {
            for (int i = 0; i < 3; i++) {
                prefs.edit().remove("best" + i).remove("name" + i).apply();
            }
            closeDialog(d);
            bestTimes();
        })).at(30, 106, 120, 36);
        d.add(new Win95.Button("OK", true, () -> closeDialog(d))).at(170, 106, 120, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    // ---------------------------------------------------------------- game

    void newGame() {
        mine = new boolean[cols * rows];
        state = new int[cols * rows];
        lost = won = started = false;
        seconds = 0;
        exploded = -1;
        invalidate();
    }

    /** Lays mines anywhere except the first square tapped and its neighbours (when there is room). */
    void layMines(int safe) {
        int placed = 0;
        int sx = safe % cols, sy = safe / cols;
        boolean roomy = cols * rows - mines >= 9;
        while (placed < mines) {
            int i = rng.nextInt(cols * rows);
            int x = i % cols, y = i / cols;
            if (mine[i] || i == safe || roomy && Math.abs(x - sx) <= 1 && Math.abs(y - sy) <= 1) {
                continue;
            }
            mine[i] = true;
            placed++;
        }
        started = true;
    }

    int around(int i) {
        int n = 0, x = i % cols, y = i / cols;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < cols && ny < rows && mine[ny * cols + nx]) {
                    n++;
                }
            }
        }
        return n;
    }

    private int flagsAround(int i) {
        int n = 0, x = i % cols, y = i / cols;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < cols && ny < rows && state[ny * cols + nx] == FLAG) {
                    n++;
                }
            }
        }
        return n;
    }

    void reveal(int i) {
        if (lost || won || state[i] == FLAG || state[i] == OPEN) {
            return;
        }
        if (!started) {
            layMines(i);
        }
        if (mine[i]) {
            lost = true;
            exploded = i;
            sound(Sfx.BOOM);
            return;
        }
        int[] stack = new int[cols * rows];
        boolean[] queued = new boolean[cols * rows];
        int sp = 0;
        stack[sp++] = i;
        queued[i] = true;
        while (sp > 0) {
            int c = stack[--sp];
            if (state[c] == OPEN || state[c] == FLAG) {
                continue;
            }
            state[c] = OPEN;
            if (around(c) == 0) {
                int x = c % cols, y = c / cols;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        int n = ny * cols + nx;
                        if (nx >= 0 && ny >= 0 && nx < cols && ny < rows && state[n] != OPEN && !queued[n]) {
                            queued[n] = true;
                            stack[sp++] = n;
                        }
                    }
                }
            }
        }
        checkWin();
    }

    void chord(int i) {
        if (state[i] != OPEN || around(i) == 0 || flagsAround(i) != around(i)) {
            return;
        }
        int x = i % cols, y = i / cols;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < cols && ny < rows) {
                    reveal(ny * cols + nx);
                }
            }
        }
    }

    void toggleFlag(int i) {
        if (lost || won || state[i] == OPEN) {
            return;
        }
        state[i] = state[i] == HIDDEN ? FLAG : state[i] == FLAG ? (marks ? QUESTION : HIDDEN) : HIDDEN;
        sound(Sfx.CLICK);
    }

    private void checkWin() {
        for (int i = 0; i < mine.length; i++) {
            if (!mine[i] && state[i] != OPEN) {
                return;
            }
        }
        won = true;
        for (int i = 0; i < mine.length; i++) {
            if (mine[i]) {
                state[i] = FLAG;
            }
        }
        sound(Sfx.WIN);
        if (level < 3 && seconds < prefs.getInt("best" + level, 999)) {
            int lv = level;
            int time = seconds;
            post(() -> prompt("Congratulations", "You have the fastest time for " + LEVEL_NAMES[lv].toLowerCase(java.util.Locale.US)
                    + " level. Please enter your name.", prefs.getString("name" + lv, "Anonymous"), false, 24, name -> {
                        prefs.edit().putInt("best" + lv, time).putString("name" + lv, name.isEmpty() ? "Anonymous" : name)
                                .apply();
                        bestTimes();
                    }));
        }
    }

    int flagsPlaced() {
        int n = 0;
        for (int s : state) {
            if (s == FLAG) {
                n++;
            }
        }
        return n;
    }

    @Override
    protected void tick() {
        if (started && !lost && !won && seconds < 999) {
            seconds++;
            invalidate();
        }
    }

    // ---------------------------------------------------------------- layout and drawing

    @Override
    protected void layoutClient(RectF r) {
        // Classic proportions: 16-pixel squares, a 12-pixel border and a 37-pixel counter panel.
        float unitsW = cols * 16 + 24, unitsH = rows * 16 + 67;
        k = Math.min(r.width() / unitsW, r.height() / unitsH);
        cell = 16 * k;
        float w = unitsW * k, h = unitsH * k;
        float left = r.left + (r.width() - w) / 2, top = r.top + (r.height() - h) / 2;
        panel.set(left + 9 * k, top + 9 * k, left + w - 9 * k, top + 46 * k);
        field.set(left + 12 * k, top + 55 * k, left + 12 * k + cols * cell, top + 55 * k + rows * cell);
        float ledH = 25 * k, ledW = 41 * k;
        counter.set(panel.left + 6 * k, panel.centerY() - ledH / 2, panel.left + 6 * k + ledW, panel.centerY() + ledH / 2);
        timer.set(panel.right - 6 * k - ledW, counter.top, panel.right - 6 * k, counter.bottom);
        face.set(panel.centerX() - 13 * k, panel.centerY() - 13 * k, panel.centerX() + 13 * k, panel.centerY() + 13 * k);
    }

    private void bevel(Canvas c, RectF r, float width, boolean raised) {
        int a = raised ? Win95.HILITE : Win95.SHADOW, b = raised ? Win95.SHADOW : Win95.HILITE;
        paint.setColor(a);
        c.drawRect(r.left - width, r.top - width, r.right + width, r.top, paint);
        c.drawRect(r.left - width, r.top - width, r.left, r.bottom + width, paint);
        paint.setColor(b);
        c.drawRect(r.left - width, r.bottom, r.right + width, r.bottom + width, paint);
        c.drawRect(r.right, r.top - width, r.right + width, r.bottom + width, paint);
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(Win95.FACE);
        RectF outer = new RectF(panel.left - 9 * k, panel.top - 9 * k, field.right + 12 * k, field.bottom + 12 * k);
        bevel(c, new RectF(outer.left + 3 * k, outer.top + 3 * k, outer.right - 3 * k, outer.bottom - 3 * k), 3 * k, true);
        bevel(c, panel, 2 * k, false);
        bevel(c, field, 3 * k, false);
        ui.led(c, counter, mines - flagsPlaced(), 3);
        ui.led(c, timer, seconds, 3);
        drawFace(c);
        for (int i = 0; i < cols * rows; i++) {
            drawCell(c, i);
        }
    }

    private void drawFace(Canvas c) {
        RectF f = face;
        paint.setColor(Win95.SHADOW);
        c.drawRect(f.left - k, f.top - k, f.right + k, f.bottom + k, paint);
        paint.setColor(Win95.FACE);
        c.drawRect(f, paint);
        bevel(c, new RectF(f.left + 2 * k, f.top + 2 * k, f.right - 2 * k, f.bottom - 2 * k), 2 * k, !facePressed);
        float off = facePressed ? k : 0;
        float cx = f.centerX() + off, cy = f.centerY() + off, rad = 8.5f * k;
        paint.setColor(0xFFFFFF00);
        c.drawCircle(cx, cy, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(k);
        paint.setColor(0xFF000000);
        c.drawCircle(cx, cy, rad, paint);
        paint.setStyle(Paint.Style.FILL);
        if (lost) {
            for (int s = -1; s <= 1; s += 2) {
                float ex = cx + s * 3 * k, ey = cy - 2.5f * k;
                c.drawLine(ex - 1.5f * k, ey - 1.5f * k, ex + 1.5f * k, ey + 1.5f * k, strokePaint());
                c.drawLine(ex - 1.5f * k, ey + 1.5f * k, ex + 1.5f * k, ey - 1.5f * k, strokePaint());
            }
            c.drawArc(new RectF(cx - 4 * k, cy + 2 * k, cx + 4 * k, cy + 7 * k), 200, 140, false, strokePaint());
        } else if (won) {
            c.drawRect(cx - 6 * k, cy - 3.5f * k, cx + 6 * k, cy - 2.5f * k, paint);
            c.drawOval(cx - 6 * k, cy - 3.5f * k, cx - 0.8f * k, cy, paint);
            c.drawOval(cx + 0.8f * k, cy - 3.5f * k, cx + 6 * k, cy, paint);
            c.drawArc(new RectF(cx - 4 * k, cy, cx + 4 * k, cy + 5 * k), 20, 140, false, strokePaint());
        } else {
            c.drawCircle(cx - 3 * k, cy - 2.5f * k, 1.1f * k, paint);
            c.drawCircle(cx + 3 * k, cy - 2.5f * k, 1.1f * k, paint);
            if (fieldPressed) {
                c.drawCircle(cx, cy + 3.5f * k, 2 * k, strokePaint());
            } else {
                c.drawArc(new RectF(cx - 4 * k, cy, cx + 4 * k, cy + 5 * k), 20, 140, false, strokePaint());
            }
        }
    }

    private Paint strokePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 1.1f * k));
        p.setColor(0xFF000000);
        return p;
    }

    private void drawCell(Canvas c, int i) {
        float x = field.left + (i % cols) * cell, y = field.top + (i / cols) * cell;
        RectF r = new RectF(x, y, x + cell, y + cell);
        int s = state[i];
        boolean pressed = i == pressedCell && (s == HIDDEN || s == QUESTION);
        boolean showMine = (lost && mine[i] && s != FLAG);
        if (s == OPEN || pressed || showMine || lost && s == FLAG && !mine[i]) {
            paint.setColor(i == exploded ? 0xFFFF0000 : Win95.FACE);
            c.drawRect(r, paint);
            paint.setColor(Win95.SHADOW);
            c.drawRect(x, y, x + cell, y + Math.max(1, k), paint);
            c.drawRect(x, y, x + Math.max(1, k), y + cell, paint);
            if (showMine || lost && s == FLAG) {
                drawMine(c, r);
                if (s == FLAG) {
                    Paint red = strokePaint();
                    red.setColor(0xFFFF0000);
                    red.setStrokeWidth(1.6f * k);
                    c.drawLine(x + 3 * k, y + 3 * k, x + cell - 3 * k, y + cell - 3 * k, red);
                    c.drawLine(x + 3 * k, y + cell - 3 * k, x + cell - 3 * k, y + 3 * k, red);
                }
            } else if (s == OPEN) {
                int n = around(i);
                if (n > 0) {
                    paint.setColor(color ? NUMBER_COLORS[n] : 0xFF000000);
                    paint.setTextSize(cell * 0.82f);
                    paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
                    paint.setTextAlign(Paint.Align.CENTER);
                    Paint.FontMetrics fm = paint.getFontMetrics();
                    c.drawText(String.valueOf(n), r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                }
            }
            return;
        }
        paint.setColor(Win95.FACE);
        c.drawRect(r, paint);
        float b = 2 * k;
        paint.setColor(Win95.HILITE);
        c.drawRect(x, y, x + cell, y + b, paint);
        c.drawRect(x, y, x + b, y + cell, paint);
        paint.setColor(Win95.SHADOW);
        c.drawRect(x, y + cell - b, x + cell, y + cell, paint);
        c.drawRect(x + cell - b, y, x + cell, y + cell, paint);
        if (s == FLAG) {
            paint.setColor(0xFF000000);
            c.drawRect(x + 4 * k, y + 11 * k, x + 12 * k, y + 13 * k, paint);
            c.drawRect(x + 7.5f * k, y + 3 * k, x + 8.5f * k, y + 11 * k, paint);
            paint.setColor(0xFFFF0000);
            Path flag = new Path();
            flag.moveTo(x + 8.5f * k, y + 3 * k);
            flag.lineTo(x + 3.5f * k, y + 5.5f * k);
            flag.lineTo(x + 8.5f * k, y + 8 * k);
            flag.close();
            c.drawPath(flag, paint);
        } else if (s == QUESTION) {
            paint.setColor(0xFF000000);
            paint.setTextSize(cell * 0.8f);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText("?", r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawMine(Canvas c, RectF r) {
        float cx = r.centerX(), cy = r.centerY(), rad = r.width() * 0.27f;
        Paint p = strokePaint();
        p.setStrokeWidth(r.width() * 0.08f);
        for (int a = 0; a < 4; a++) {
            double ang = Math.PI * a / 4;
            float dx = (float) Math.cos(ang) * rad * 1.45f, dy = (float) Math.sin(ang) * rad * 1.45f;
            c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, p);
        }
        paint.setColor(0xFF000000);
        c.drawCircle(cx, cy, rad, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawRect(cx - rad * 0.55f, cy - rad * 0.55f, cx - rad * 0.15f, cy - rad * 0.15f, paint);
    }

    // ---------------------------------------------------------------- touch

    private int cellAt(float x, float y) {
        if (!field.contains(x, y)) {
            return -1;
        }
        int cx = (int) ((x - field.left) / cell), cy = (int) ((y - field.top) / cell);
        return cx >= 0 && cy >= 0 && cx < cols && cy < rows ? cy * cols + cx : -1;
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                facePressed = face.contains(x, y);
                pressedCell = lost || won ? -1 : cellAt(x, y);
                fieldPressed = pressedCell >= 0;
                break;
            case MotionEvent.ACTION_MOVE:
                if (fieldPressed) {
                    pressedCell = cellAt(x, y);
                } else if (facePressed && !face.contains(x, y)) {
                    facePressed = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (facePressed && face.contains(x, y)) {
                    newGame();
                } else if (fieldPressed) {
                    int i = cellAt(x, y);
                    if (i >= 0) {
                        if (state[i] == OPEN) {
                            chord(i);
                        } else {
                            reveal(i);
                        }
                    }
                }
                facePressed = fieldPressed = false;
                pressedCell = -1;
                break;
            default:
                facePressed = fieldPressed = false;
                pressedCell = -1;
                break;
        }
    }

    @Override
    protected void clientLongPress(float x, float y) {
        int i = cellAt(x, y);
        if (i >= 0) {
            toggleFlag(i);
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }
        pressedCell = -1;
        fieldPressed = false;
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
