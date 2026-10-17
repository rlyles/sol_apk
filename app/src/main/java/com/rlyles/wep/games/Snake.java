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
import com.rlyles.wep.win.WinApp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Snake: eat the numbers 1 to 9 in order, growing longer each time, then slither out of the door
 * into the next room. Don't run into the walls or yourself.
 */
@SuppressLint("ViewConstructor")
public final class Snake extends WinApp {
    static final int GW = 32, GH = 24;
    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
    private static final String[] SKILLS = {"&Beginner", "&Intermediate", "&Expert"};
    private static final float[] STEP = {0.14f, 0.1f, 0.065f};

    final boolean[] wall = new boolean[GW * GH];
    final Deque<Integer> body = new ArrayDeque<>();
    int dir, room = 1, lives = 3, score, number, food = -1, grow;
    private int queuedDir = -1;
    boolean over = true, paused, doorOpen;
    private int skill;
    private boolean color;
    private float timer;
    private final Random rng = new Random();

    private float cell;
    private final RectF field = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float downX, downY;
    private boolean swiped;

    public Snake(Context context, Host host) {
        super(context, host, "snake");
        skill = prefs.getInt("skill", 1);
        color = prefs.getBoolean("color", true);
        buildRoom(1);
        resetSnake();
        over = true;
        setAnimating(true);
    }

    @Override
    public String title() {
        return "Snake";
    }

    @Override
    public void save() {
        prefs.edit().putInt("skill", skill).putBoolean("color", color).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Start a new game", true, false, this::newGame));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the game", !over, false, () -> paused = !paused));
            items.add(new Item("&Sound", "F4", "Sound effects", true, host.sfx().enabled,
                    () -> host.sfx().enabled = !host.sfx().enabled));
            items.add(new Item("&Color", "", "Colour or black and white", true, color, () -> color = !color));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Snake", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Skill", items -> {
            for (int i = 0; i < 3; i++) {
                int s = i;
                items.add(new Item(SKILLS[i], "", "Snake speed", true, skill == i, () -> {
                    skill = s;
                    save();
                }));
            }
            items.add(Item.sep());
            items.add(new Item("&Room...", "", "Start in another room", true, false, () -> prompt("Room",
                    "Start in Room (1 to 8):", String.valueOf(room), true, 1, t -> {
                        if (!t.isEmpty()) {
                            int r = Math.max(1, Math.min(8, Integer.parseInt(t)));
                            score = 0;
                            lives = 3;
                            buildRoom(r);
                            resetSnake();
                        }
                    })));
        }));
        addHelpMenu(menus, "Guide the snake to eat the numbers in order, from 1 to 9. Every number makes the "
                + "snake longer. After the 9, a door opens in the wall: go through it to reach the next room.\n\n"
                + "Swipe in the direction you want to go, or tap beside the snake's head to turn toward your finger. "
                + "Arrow keys work too.\n\nRunning into a wall or into yourself costs a life.",
                "Snake for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Room: " + room);
        panels.add("Lives: " + lives);
        panels.add("Score: " + score);
    }

    @Override
    protected String statusText() {
        return over ? "Tap to play" : paused ? "Paused" : doorOpen ? "The door is open!" : "Eat the " + number;
    }

    // ---------------------------------------------------------------- rooms

    void newGame() {
        score = 0;
        lives = 3;
        buildRoom(1);
        resetSnake();
    }

    void buildRoom(int r) {
        room = r;
        java.util.Arrays.fill(wall, false);
        for (int x = 0; x < GW; x++) {
            wall[x] = wall[(GH - 1) * GW + x] = true;
        }
        for (int y = 0; y < GH; y++) {
            wall[y * GW] = wall[y * GW + GW - 1] = true;
        }
        int cx = GW / 2, cy = GH / 2;
        switch ((r - 1) % 8) {
            case 1:
                for (int x = 8; x < GW - 8; x++) {
                    wall[cy * GW + x] = true;
                }
                break;
            case 2:
                for (int y = 5; y < GH - 5; y++) {
                    wall[y * GW + 9] = wall[y * GW + GW - 10] = true;
                }
                break;
            case 3:
                for (int x = 6; x < GW - 6; x++) {
                    wall[6 * GW + x] = wall[(GH - 7) * GW + x] = true;
                }
                break;
            case 4:
                for (int i = -5; i <= 5; i++) {
                    wall[(cy + i) * GW + cx] = true;
                    wall[cy * GW + cx + i * 2] = true;
                }
                break;
            case 5:
                for (int y = 1; y < GH - 6; y++) {
                    wall[y * GW + 10] = true;
                }
                for (int y = 6; y < GH - 1; y++) {
                    wall[y * GW + GW - 11] = true;
                }
                break;
            case 6:
                for (int x = 5; x < GW - 5; x += 4) {
                    for (int y = 5; y < GH - 5; y += 4) {
                        wall[y * GW + x] = wall[y * GW + x + 1] = true;
                    }
                }
                break;
            case 7:
                for (int x = 4; x < GW - 4; x++) {
                    if (x < cx - 2 || x > cx + 2) {
                        wall[5 * GW + x] = wall[(GH - 6) * GW + x] = true;
                    }
                }
                for (int y = 5; y < GH - 5; y++) {
                    if (y < cy - 2 || y > cy + 2) {
                        wall[y * GW + 4] = wall[y * GW + GW - 5] = true;
                    }
                }
                break;
            default:
                break;
        }
    }

    void resetSnake() {
        body.clear();
        int y = GH / 2 + 3, x0 = 4;
        if ((room - 1) % 8 == 4) {
            y = GH - 4;
        }
        for (int i = 0; i < 4; i++) {
            body.addFirst(y * GW + x0 + i);
        }
        dir = 0;
        queuedDir = -1;
        grow = 0;
        number = 1;
        doorOpen = false;
        over = paused = false;
        placeFood();
        invalidate();
    }

    private void placeFood() {
        for (int tries = 0; tries < 2000; tries++) {
            int c = (1 + rng.nextInt(GH - 2)) * GW + 1 + rng.nextInt(GW - 2);
            if (!wall[c] && !body.contains(c)) {
                food = c;
                return;
            }
        }
    }

    private int doorCell() {
        return (GH / 2) * GW + GW - 1;
    }

    void turn(int d) {
        int current = queuedDir >= 0 ? queuedDir : dir;
        if (d != (current + 2) % 4 && d != current) {
            queuedDir = d;
        }
    }

    @Override
    protected void frame(float dt) {
        if (over || paused) {
            return;
        }
        timer += dt;
        if (timer < STEP[skill]) {
            return;
        }
        timer = 0;
        if (queuedDir >= 0) {
            dir = queuedDir;
            queuedDir = -1;
        }
        int head = body.peekFirst();
        int x = head % GW + DX[dir], y = head / GW + DY[dir];
        int next = y * GW + x;
        if (doorOpen && next == doorCell()) {
            score += 1000;
            sound(Sfx.WIN);
            buildRoom(room % 8 + 1);
            resetSnake();
            return;
        }
        if (x < 0 || y < 0 || x >= GW || y >= GH || wall[next] || body.contains(next) && next != body.peekLast()) {
            die();
            return;
        }
        body.addFirst(next);
        if (next == food) {
            score += number * 10 * (skill + 1);
            grow += number * 2;
            sound(Sfx.BLIP);
            number++;
            if (number > 9) {
                food = -1;
                doorOpen = true;
                wall[doorCell()] = false;
                sound(Sfx.CHIME);
            } else {
                placeFood();
            }
        }
        if (grow > 0) {
            grow--;
        } else {
            body.removeLast();
        }
    }

    private void die() {
        lives--;
        sound(Sfx.BOOM);
        if (lives <= 0) {
            over = true;
            if (score > prefs.getInt("best", 0)) {
                prefs.edit().putInt("best", score).apply();
            }
            messageBox("Snake", "Game over!\n\nScore: " + score + "\nBest: " + prefs.getInt("best", 0), null);
        } else {
            buildRoom(room);
            resetSnake();
            paused = true;
        }
    }

    @Override
    protected void onHidden() {
        if (!over) {
            paused = true;
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void layoutClient(RectF r) {
        cell = Math.min(r.width() / GW, r.height() / GH);
        float w = cell * GW, h = cell * GH;
        field.set(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2);
    }

    @Override
    protected void drawClient(Canvas c) {
        drawField(c);
    }

    /** The original's 8x8 snake pieces from the user's SNAKE.EXE (colour or black and white), or null. */
    private static Bitmap[] art(boolean color) {
        return Media.get().cached("snake.art" + color, m -> {
            Bitmap strip = m.bitmap("snake.exe", color ? "200" : "201");
            if (strip == null || strip.getHeight() < 18 * 8) {
                return null;
            }
            Bitmap[] a = new Bitmap[18];
            for (int i = 0; i < 18; i++) {
                a[i] = Sprites.slice(strip, 0, i * 8, 8, 8);
            }
            return a;
        });
    }

    /** Direction from cell a to its neighbour b: 0 up, 1 right, 2 down, 3 left. */
    private static int dirTo(int a, int b) {
        return b == a - GW ? 0 : b == a + 1 ? 1 : b == a + GW ? 2 : 3;
    }

    /**
     * Strip pieces: heads facing up/right/down/left are 4-7, tails joined up/right/down/left 8-11,
     * corners up+right, right+down, down+left, up+left 12-15, and straight up-down 16, left-right 17.
     */
    private void drawBody(Canvas c, Bitmap[] a) {
        Integer[] segs = body.toArray(new Integer[0]);
        for (int k = 0; k < segs.length; k++) {
            int seg = segs[k], frame;
            if (k == 0) {
                frame = segs.length > 1 ? 4 + dirTo(segs[1], seg) : 4;
            } else if (k == segs.length - 1) {
                frame = 8 + dirTo(seg, segs[k - 1]);
            } else {
                int d1 = dirTo(seg, segs[k - 1]), d2 = dirTo(seg, segs[k + 1]);
                int mask = 1 << d1 | 1 << d2;
                frame = mask == 5 ? 16 : mask == 10 ? 17 : mask == 3 ? 12 : mask == 6 ? 13 : mask == 12 ? 14 : 15;
            }
            Sprites.draw(c, a[frame], field.left + (seg % GW) * cell, field.top + (seg / GW) * cell, cell, cell);
        }
    }

    private void drawField(Canvas c) {
        c.drawColor(0xFF000000);
        paint.setColor(color ? 0xFF000080 : 0xFF000000);
        c.drawRect(field, paint);
        for (int i = 0; i < wall.length; i++) {
            if (wall[i]) {
                float x = field.left + (i % GW) * cell, y = field.top + (i / GW) * cell;
                paint.setColor(color ? 0xFFC04000 : 0xFFC0C0C0);
                c.drawRect(x, y, x + cell, y + cell, paint);
                paint.setColor(color ? 0xFF802000 : 0xFF808080);
                c.drawRect(x, y + cell * 0.75f, x + cell, y + cell, paint);
            }
        }
        if (doorOpen) {
            int d = doorCell();
            paint.setColor(0xFFFFFF00);
            float x = field.left + (d % GW) * cell, y = field.top + (d / GW) * cell;
            c.drawRect(x, y, x + cell, y + cell, paint);
        }
        if (food >= 0) {
            float x = field.left + (food % GW) * cell, y = field.top + (food / GW) * cell;
            paint.setColor(color ? 0xFFFF2020 : 0xFFFFFFFF);
            c.drawCircle(x + cell / 2, y + cell / 2, cell * 0.6f, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(cell * 0.9f);
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            paint.setColor(color ? 0xFFFFFFFF : 0xFF000000);
            c.drawText(String.valueOf(number), x + cell / 2, y + cell / 2 - (fm.ascent + fm.descent) / 2, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
        }
        Bitmap[] art = art(color);
        if (art != null) {
            drawBody(c, art);
        }
        boolean first = true;
        for (int seg : art != null ? new ArrayDeque<Integer>() : body) {
            float x = field.left + (seg % GW) * cell, y = field.top + (seg / GW) * cell;
            paint.setColor(first ? (color ? 0xFFFFFF00 : 0xFFFFFFFF) : color ? 0xFF00E000 : 0xFFC0C0C0);
            c.drawRoundRect(x + 1, y + 1, x + cell - 1, y + cell - 1, cell * 0.3f, cell * 0.3f, paint);
            first = false;
        }
        if (over || paused) {
            ui.withFont(20, true, () -> ui.centered(c, over ? "Tap to play" : "Tap to go", field, 0xFFFFFFFF));
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            swiped = false;
            return;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = x - downX, dy = y - downY;
            if (Math.hypot(dx, dy) > cell * 1.5f) {
                turn(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 0 : 2) : (dy > 0 ? 1 : 3));
                swiped = true;
                downX = x;
                downY = y;
            }
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
        if (!swiped && Math.hypot(x - downX, y - downY) < cell) {
            // A tap turns toward the finger, across the current direction of travel.
            int head = body.peekFirst();
            float hx = field.left + (head % GW + 0.5f) * cell, hy = field.top + (head / GW + 0.5f) * cell;
            if (dir % 2 == 0) {
                turn(y > hy ? 1 : 3);
            } else {
                turn(x > hx ? 0 : 2);
            }
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: turn(0); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: turn(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: turn(2); return true;
            case KeyEvent.KEYCODE_DPAD_UP: turn(3); return true;
            case KeyEvent.KEYCODE_F2: newGame(); return true;
            case KeyEvent.KEYCODE_F3: paused = !paused; return true;
            default: return false;
        }
    }
}
