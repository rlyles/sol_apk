package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.Sprites;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.util.List;
import java.util.Random;

/**
 * Tetris. Drag sideways to move the piece, tap to rotate it, drag down to drop it faster and flick
 * down to drop it all the way.
 */
@SuppressLint("ViewConstructor")
public final class Tetris extends WinApp {
    static final int W = 10, H = 20;
    /** I, O, T, S, Z, J, L as 4x4 rotation masks (16 bits, row-major). */
    static final int[][] SHAPES = {
            {0x0F00, 0x2222, 0x00F0, 0x4444},
            {0x0660, 0x0660, 0x0660, 0x0660},
            {0x0E40, 0x4C40, 0x4E00, 0x4640},
            {0x06C0, 0x4620, 0x06C0, 0x4620},
            {0x0C60, 0x2640, 0x0C60, 0x2640},
            {0x0E20, 0x44C0, 0x8E00, 0x6440},
            {0x0E80, 0xC440, 0x2E00, 0x4460}};
    static final int[] COLORS = {0xFF00C0C0, 0xFFFFFF00, 0xFFA000C0, 0xFF00C000, 0xFFFF0000, 0xFF0000FF, 0xFFFF8000};
    private static final int[] LINE_SCORES = {0, 40, 100, 300, 1200};

    final int[] well = new int[W * H];
    int piece, rot, px, py, next;
    int score, lines, level, startLevel, startRows;
    boolean over = true, paused, preview, played;
    private float fallTimer, lockTimer;
    private final Random rng = new Random();
    private int[] flashing;
    private float flashTime;

    private float cell;
    private final RectF wellRect = new RectF(), side = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // touch
    private float downX, downY, lastX, lastY, movedX, movedY;
    private long downTime;
    private boolean gesture;

    public Tetris(Context context, Host host) {
        super(context, host, "tetris");
        startLevel = prefs.getInt("startLevel", 0);
        startRows = prefs.getInt("startRows", 0);
        preview = prefs.getBoolean("preview", true);
        next = rng.nextInt(7);
        java.util.Arrays.fill(well, -1);
        setAnimating(true);
    }

    @Override
    public String title() {
        return "Tetris";
    }

    @Override
    public void save() {
        prefs.edit().putInt("startLevel", startLevel).putInt("startRows", startRows).putBoolean("preview", preview).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Start a new game", true, false, this::newGame));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the game", !over, false, () -> paused = !paused));
            items.add(new Item("&High Scores...", "", "Best scores", true, false, this::highScores));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Tetris", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Skill", items -> {
            items.add(new Item("Starting &Level...", "", "Level " + startLevel, true, false, () ->
                    prompt("Starting Level", "Starting level (0 to 9):", String.valueOf(startLevel), true, 1, t -> {
                        if (!t.isEmpty()) {
                            startLevel = Math.min(9, Integer.parseInt(t));
                            save();
                        }
                    })));
            items.add(new Item("Starting &Rows...", "", startRows + " rows of junk", true, false, () ->
                    prompt("Starting Rows", "Rows of junk to start with (0 to 12):", String.valueOf(startRows), true, 2, t -> {
                        if (!t.isEmpty()) {
                            startRows = Math.min(12, Integer.parseInt(t));
                            save();
                        }
                    })));
        }));
        menus.add(new Menu("&Options", items -> items.add(new Item("Piece Pre&view", "", "Show the next piece", true,
                preview, () -> preview = !preview))));
        addHelpMenu(menus, "Fit the falling pieces together to make complete rows across the well. Complete rows "
                + "disappear; the game ends when the pile reaches the top.\n\nDrag left or right to move the piece. "
                + "Tap to rotate it. Drag down to make it fall faster, or flick down to drop it at once.\n\n"
                + "Keyboard: arrow keys move, Up rotates, Space drops.\n\nOne row scores 40 points times (level + 1), "
                + "two rows 100, three rows 300 and four rows (a Tetris!) 1200. Every ten rows the level goes up and "
                + "the pieces fall faster.", "Tetris for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected String statusText() {
        return over ? "Tap or choose Game > New to play." : paused ? "Paused" : "";
    }

    // ---------------------------------------------------------------- rules

    void newGame() {
        java.util.Arrays.fill(well, -1);
        for (int r = 0; r < startRows; r++) {
            int hole = rng.nextInt(W);
            for (int x = 0; x < W; x++) {
                if (x != hole && rng.nextInt(10) < 7) {
                    well[(H - 1 - r) * W + x] = rng.nextInt(7);
                }
            }
        }
        score = lines = 0;
        played = true;
        level = startLevel;
        over = paused = false;
        flashing = null;
        spawn();
        invalidate();
    }

    private void spawn() {
        piece = next;
        next = rng.nextInt(7);
        rot = 0;
        px = 3;
        py = 0;
        fallTimer = 0;
        lockTimer = 0;
        if (!fits(piece, rot, px, py)) {
            over = true;
            sound(Sfx.LOSE);
            checkHighScore();
        }
    }

    static boolean cellOf(int shape, int rot, int x, int y) {
        return (SHAPES[shape][rot] & (0x8000 >> (y * 4 + x))) != 0;
    }

    boolean fits(int shape, int r, int x0, int y0) {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (!cellOf(shape, r, x, y)) {
                    continue;
                }
                int wx = x0 + x, wy = y0 + y;
                if (wx < 0 || wx >= W || wy >= H || wy >= 0 && well[wy * W + wx] >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean move(int dx) {
        if (!over && !paused && fits(piece, rot, px + dx, py)) {
            px += dx;
            lockTimer = 0;
            return true;
        }
        return false;
    }

    void rotate() {
        if (over || paused) {
            return;
        }
        int r = (rot + 1) % 4;
        for (int kick : new int[] {0, -1, 1, -2, 2}) {
            if (fits(piece, r, px + kick, py)) {
                rot = r;
                px += kick;
                lockTimer = 0;
                sound(Sfx.CLICK);
                return;
            }
        }
    }

    boolean softDrop() {
        if (!over && !paused && fits(piece, rot, px, py + 1)) {
            py++;
            score++;
            return true;
        }
        return false;
    }

    void hardDrop() {
        if (over || paused) {
            return;
        }
        while (fits(piece, rot, px, py + 1)) {
            py++;
            score += 2;
        }
        lock();
    }

    private void lock() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (cellOf(piece, rot, x, y) && py + y >= 0) {
                    well[(py + y) * W + px + x] = piece;
                }
            }
        }
        int[] full = new int[4];
        int count = 0;
        for (int y = 0; y < H; y++) {
            boolean complete = true;
            for (int x = 0; x < W; x++) {
                if (well[y * W + x] < 0) {
                    complete = false;
                    break;
                }
            }
            if (complete && count < 4) {
                full[count++] = y;
            }
        }
        if (count > 0) {
            flashing = java.util.Arrays.copyOf(full, count);
            flashTime = 0.3f;
            score += LINE_SCORES[count] * (level + 1);
            lines += count;
            level = Math.max(level, startLevel + lines / 10);
            sound(count == 4 ? Sfx.WIN : Sfx.LINE);
        } else {
            sound(Sfx.DROP);
            spawn();
        }
    }

    private void clearFlashing() {
        for (int row : flashing) {
            for (int y = row; y > 0; y--) {
                System.arraycopy(well, (y - 1) * W, well, y * W, W);
            }
            java.util.Arrays.fill(well, 0, W, -1);
        }
        flashing = null;
        spawn();
    }

    float fallInterval() {
        return Math.max(0.05f, 0.8f - level * 0.07f);
    }

    @Override
    protected void frame(float dt) {
        if (over || paused) {
            return;
        }
        if (flashing != null) {
            flashTime -= dt;
            if (flashTime <= 0) {
                clearFlashing();
            }
            return;
        }
        fallTimer += dt;
        if (fallTimer >= fallInterval()) {
            fallTimer = 0;
            if (fits(piece, rot, px, py + 1)) {
                py++;
            } else {
                lockTimer += fallInterval();
                if (lockTimer >= 0.4f) {
                    lock();
                }
            }
        }
    }

    @Override
    protected void onHidden() {
        if (!over) {
            paused = true;
        }
    }

    private void checkHighScore() {
        int[] best = scores();
        if (score > 0 && score > best[best.length - 1]) {
            post(() -> prompt("Tetris", "Congratulations! You made the high score list. Enter your cool name:",
                    prefs.getString("lastName", "Player"), false, 16, name -> {
                        String n = name.isEmpty() ? "Player" : name;
                        prefs.edit().putString("lastName", n).apply();
                        insertScore(n, score);
                        highScores();
                    }));
        }
    }

    private int[] scores() {
        int[] s = new int[5];
        for (int i = 0; i < 5; i++) {
            s[i] = prefs.getInt("score" + i, 0);
        }
        return s;
    }

    private void insertScore(String name, int value) {
        int[] s = scores();
        String[] names = new String[5];
        for (int i = 0; i < 5; i++) {
            names[i] = prefs.getString("name" + i, "");
        }
        int at = 4;
        while (at > 0 && value > s[at - 1]) {
            at--;
        }
        android.content.SharedPreferences.Editor e = prefs.edit();
        for (int i = 4; i > at; i--) {
            e.putInt("score" + i, s[i - 1]).putString("name" + i, names[i - 1]);
        }
        e.putInt("score" + at, value).putString("name" + at, name).apply();
    }

    private void highScores() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            int s = prefs.getInt("score" + i, 0);
            sb.append(i + 1).append(".   ").append(s == 0 ? "--" : s + "   " + prefs.getString("name" + i, "")).append('\n');
        }
        messageBox("Tetris High Scores", sb.toString().trim(), null);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void layoutClient(RectF r) {
        float pad = 10 * dp;
        cell = Math.min((r.height() - 2 * pad) / H, (r.width() - 3 * pad) / (W + 6));
        float wellW = W * cell, total = wellW + 6 * cell + pad;
        float left = r.centerX() - total / 2;
        wellRect.set(left, r.centerY() - H * cell / 2, left + wellW, r.centerY() + H * cell / 2);
        side.set(wellRect.right + pad, wellRect.top, wellRect.right + pad + 6 * cell, wellRect.bottom);
    }

    @Override
    protected void drawClient(Canvas c) {
        // The original changes its tiled backdrop (bitmaps 1-8 in TETRIS.EXE) with every level.
        android.graphics.Bitmap backdrop = Media.get().bitmap("tetris.exe", Integer.toString(level % 8 + 1));
        if (backdrop != null) {
            Sprites.tile(c, backdrop, client, 2 * dp, client.left, client.top);
        } else {
            paint.setShader(new LinearGradient(0, client.top, 0, client.bottom, 0xFF000060, 0xFF0060A0,
                    Shader.TileMode.CLAMP));
            c.drawRect(client, paint);
            paint.setShader(null);
        }
        ui.sunken(c, new RectF(wellRect.left - 3 * dp, wellRect.top - 3 * dp, wellRect.right + 3 * dp, wellRect.bottom + 3 * dp),
                0xFF000000);
        for (int y = 0; y < H; y++) {
            boolean flash = false;
            if (flashing != null) {
                for (int f : flashing) {
                    flash |= f == y;
                }
            }
            for (int x = 0; x < W; x++) {
                int v = well[y * W + x];
                if (v >= 0) {
                    block(c, wellRect.left + x * cell, wellRect.top + y * cell, cell,
                            flash && ((int) (flashTime * 20) & 1) == 0 ? 0xFFFFFFFF : COLORS[v]);
                }
            }
        }
        if (!over && flashing == null && !paused) {
            int ghost = py;
            while (fits(piece, rot, px, ghost + 1)) {
                ghost++;
            }
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    if (cellOf(piece, rot, x, y)) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(Math.max(1, cell / 14));
                        paint.setColor(0x80FFFFFF);
                        float gx = wellRect.left + (px + x) * cell, gy = wellRect.top + (ghost + y) * cell;
                        c.drawRect(gx + 2, gy + 2, gx + cell - 2, gy + cell - 2, paint);
                        paint.setStyle(Paint.Style.FILL);
                        if (py + y >= 0) {
                            block(c, wellRect.left + (px + x) * cell, wellRect.top + (py + y) * cell, cell, COLORS[piece]);
                        }
                    }
                }
            }
        }
        // Side panel: next piece and the numbers.
        RectF nextBox = new RectF(side.left, side.top, side.right, side.top + 5 * cell);
        ui.raised(c, new RectF(side.left, side.top, side.right, side.bottom));
        ui.sunken(c, new RectF(nextBox.left + 6 * dp, nextBox.top + 22 * dp, nextBox.right - 6 * dp, nextBox.bottom), 0xFF000000);
        ui.withFont(13, true, () -> ui.label(c, "Next", side.left + 8 * dp, side.top + 17 * dp, Win95.DARK));
        if (preview) {
            float s = cell * 0.8f;
            float ox = nextBox.centerX() - 2 * s, oy = nextBox.top + 22 * dp + (nextBox.height() - 22 * dp) / 2 - 1.5f * s;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    if (cellOf(next, 0, x, y)) {
                        block(c, ox + x * s, oy + y * s, s, COLORS[next]);
                    }
                }
            }
        }
        String[][] stats = {{"Score", String.valueOf(score)}, {"Lines", String.valueOf(lines)}, {"Level", String.valueOf(level)}};
        float y0 = nextBox.bottom + 16 * dp;
        for (int i = 0; i < stats.length; i++) {
            float y = y0 + i * 52 * dp;
            int fi = i;
            ui.withFont(13, true, () -> ui.label(c, stats[fi][0], side.left + 8 * dp, y + 12 * dp, Win95.DARK));
            RectF box = new RectF(side.left + 6 * dp, y + 18 * dp, side.right - 6 * dp, y + 44 * dp);
            ui.sunken(c, box, 0xFFFFFFFF);
            ui.withFont(15, false, () -> ui.label(c, stats[fi][1], box.right - 6 * dp - ui.textWidth(stats[fi][1]),
                    ui.baseline(box), Win95.DARK));
        }
        if (over || paused) {
            ui.withFont(22, true, () -> ui.centered(c, over ? (played ? "GAME OVER" : "Tap to play") : "PAUSED", wellRect,
                    0xFFFFFFFF));
        }
    }

    private void block(Canvas c, float x, float y, float s, int color) {
        paint.setColor(color);
        c.drawRect(x, y, x + s, y + s, paint);
        float b = Math.max(1, s / 7);
        paint.setColor(0x90FFFFFF);
        c.drawRect(x, y, x + s, y + b, paint);
        c.drawRect(x, y, x + b, y + s, paint);
        paint.setColor(0x80000000);
        c.drawRect(x, y + s - b, x + s, y + s, paint);
        c.drawRect(x + s - b, y, x + s, y + s, paint);
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x;
                downY = lastY = y;
                movedX = movedY = 0;
                downTime = SystemClock.uptimeMillis();
                gesture = false;
                break;
            case MotionEvent.ACTION_MOVE:
                movedX += x - lastX;
                movedY += y - lastY;
                while (movedX > cell) {
                    move(1);
                    movedX -= cell;
                    gesture = true;
                }
                while (movedX < -cell) {
                    move(-1);
                    movedX += cell;
                    gesture = true;
                }
                while (movedY > cell) {
                    softDrop();
                    movedY -= cell;
                    gesture = true;
                }
                if (movedY < -cell) {
                    movedY = 0;
                }
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_UP: {
                long dt = SystemClock.uptimeMillis() - downTime;
                float dy = y - downY, dx = x - downX;
                if (over) {
                    newGame();
                } else if (paused) {
                    paused = false;
                } else if (dy > 3 * cell && dt < 250 && Math.abs(dy) > 2 * Math.abs(dx)) {
                    hardDrop();
                } else if (!gesture && Math.hypot(dx, dy) < cell) {
                    rotate();
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: move(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: rotate(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: softDrop(); return true;
            case KeyEvent.KEYCODE_SPACE: hardDrop(); return true;
            case KeyEvent.KEYCODE_F2: newGame(); return true;
            case KeyEvent.KEYCODE_F3: paused = !paused; return true;
            default: return false;
        }
    }
}
