package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Taipei: mahjong solitaire on the classic 144-tile turtle. Remove matching pairs of free tiles,
 * ones with nothing on top and an open left or right side. Every deal can be won.
 */
@SuppressLint("ViewConstructor")
public final class Taipei extends WinApp {
    /** Tile kinds: 0-8 dots, 9-17 bamboo, 18-26 characters, 27-30 winds, 31-33 dragons, 34-37 flowers, 38-41 seasons. */
    static final int KINDS = 42;
    private static final String[] NUMERALS = {"一", "二", "三", "四", "五", "六", "七",
            "八", "九"};
    private static final String[] WINDS = {"東", "南", "西", "北"};
    private static final String[] WIND_LETTERS = {"E", "S", "W", "N"};
    private static final String[] FLOWERS = {"梅", "蘭", "菊", "竹"};
    private static final String[] SEASONS = {"春", "夏", "秋", "冬"};
    private static final String[] FORTUNES = {
            "Stay calm, cool, and collected, and all things will fall into place.",
            "Accept the next proposition you hear.",
            "Congratulations on winning Taipei!",
            "Have you found the ancient Taipei secret?",
            "Why not try another fine Windows game?"};

    /** Positions in half-tile units: x, y, layer. */
    static final int[][] TURTLE = turtle();

    final int[] kind = new int[144];
    final boolean[] present = new boolean[144];
    private final List<int[]> history = new ArrayList<>();
    private int[] solution;
    private int gameNumber;
    private int selected = -1;
    private int[] hintPair;
    private boolean messages, darkSides, autoplaying;
    private final Random rng = new Random();

    private float tw, th, depth, originX, originY;
    private final Bitmap[] faces = new Bitmap[KINDS];
    private int faceW, faceH;
    private Integer[] order;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Taipei(Context context, Host host) {
        super(context, host, "taipei");
        messages = prefs.getBoolean("messages", true);
        darkSides = prefs.getBoolean("dark", false);
        newGame(1 + rng.nextInt(32000));
    }

    static int[][] turtle() {
        List<int[]> p = new ArrayList<>();
        int[][] rows = {{1, 12}, {3, 10}, {2, 11}, {1, 12}, {1, 12}, {2, 11}, {3, 10}, {1, 12}};
        for (int r = 0; r < 8; r++) {
            for (int x = rows[r][0]; x <= rows[r][1]; x++) {
                p.add(new int[] {x * 2, r * 2, 0});
            }
        }
        p.add(new int[] {0, 7, 0});
        p.add(new int[] {26, 7, 0});
        p.add(new int[] {28, 7, 0});
        int[][] squares = {{4, 9, 1, 6}, {5, 8, 2, 5}, {6, 7, 3, 4}};
        for (int z = 0; z < 3; z++) {
            for (int y = squares[z][2]; y <= squares[z][3]; y++) {
                for (int x = squares[z][0]; x <= squares[z][1]; x++) {
                    p.add(new int[] {x * 2, y * 2, z + 1});
                }
            }
        }
        p.add(new int[] {13, 7, 4});
        return p.toArray(new int[0][]);
    }

    @Override
    public String title() {
        return "Taipei";
    }

    @Override
    protected String caption() {
        return "Taipei  Game #" + gameNumber;
    }

    @Override
    public void save() {
        prefs.edit().putBoolean("messages", messages).putBoolean("dark", darkSides).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Start a new game", true, false, () -> newGame(1 + rng.nextInt(32000))));
            items.add(new Item("&Select Game...", "", "Choose a game by number", true, false, this::selectGame));
            items.add(new Item("&Backup", "BkSp", "Put back the last pair", !history.isEmpty(), false, this::backup));
            items.add(new Item("&Hint", "H", "Show a pair you can remove", true, false, this::hint));
            items.add(new Item("Start &Over", "", "Put every tile back", true, false, this::startOver));
            items.add(new Item("&Autoplay", "", "Watch the computer finish", !autoplaying, false, this::autoplay));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Taipei", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Lighten Tile Sides", "", "Light tile edges", true, !darkSides, () -> {
                darkSides = false;
                invalidate();
            }));
            items.add(new Item("&Darken Tile Sides", "", "Dark tile edges", true, darkSides, () -> {
                darkSides = true;
                invalidate();
            }));
            items.add(new Item("&Messages", "", "Explain moves that are not allowed", true, messages,
                    () -> messages = !messages));
        }));
        addHelpMenu(menus, "Remove all the tiles from the board in matching pairs.\n\nYou can only take a tile "
                + "that is free: nothing may be on top of it, and it must be able to slide out to the left or to the "
                + "right. Tap one free tile, then a matching free tile.\n\nTiles match when they are identical, "
                + "except that any Flower matches any other Flower, and any Season matches any other Season.\n\n"
                + "The suits are Dots (circles), Bamboo (sticks) and Characters (a number over the sign for ten "
                + "thousand), plus the four Winds and three Dragons.\n\nEvery game can be won. Backup takes back a "
                + "pair and Hint shows one you can take.", "Taipei for the Entertainment Pack.\n\nThe original Taipei "
                + "was written in 1987.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Tiles: " + tilesLeft());
    }

    int tilesLeft() {
        int n = 0;
        for (boolean b : present) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- rules

    static boolean matches(int a, int b) {
        return a == b || a >= 34 && a <= 37 && b >= 34 && b <= 37 || a >= 38 && b >= 38;
    }

    /** Whether tile {@code i} is free among the tiles marked in {@code on}. */
    static boolean free(int i, boolean[] on) {
        int[] t = TURTLE[i];
        boolean leftBlocked = false, rightBlocked = false;
        for (int j = 0; j < TURTLE.length; j++) {
            if (j == i || !on[j]) {
                continue;
            }
            int[] u = TURTLE[j];
            int dx = u[0] - t[0], dy = Math.abs(u[1] - t[1]);
            if (u[2] == t[2] + 1 && Math.abs(dx) < 2 && dy < 2) {
                return false;
            }
            if (u[2] == t[2] && dy < 2) {
                if (dx == -2) {
                    leftBlocked = true;
                } else if (dx == 2) {
                    rightBlocked = true;
                }
            }
        }
        return !leftBlocked || !rightBlocked;
    }

    boolean isFree(int i) {
        return present[i] && free(i, present);
    }

    /** Deals by removing random free pairs from a full board, so the reverse is a winning line. */
    void newGame(int number) {
        gameNumber = number;
        Random r = new Random(number);
        List<int[]> pairs = new ArrayList<>();
        for (int k = 0; k < 34; k++) {
            pairs.add(new int[] {k, k});
            pairs.add(new int[] {k, k});
        }
        pairs.add(new int[] {34, 35});
        pairs.add(new int[] {36, 37});
        pairs.add(new int[] {38, 39});
        pairs.add(new int[] {40, 41});
        int[] sol = new int[144];
        while (true) {
            Collections.shuffle(pairs, r);
            boolean[] on = new boolean[144];
            java.util.Arrays.fill(on, true);
            boolean ok = true;
            for (int p = 0; p < pairs.size() && ok; p++) {
                List<Integer> frees = new ArrayList<>();
                for (int i = 0; i < 144; i++) {
                    if (on[i] && free(i, on)) {
                        frees.add(i);
                    }
                }
                if (frees.size() < 2) {
                    ok = false;
                    break;
                }
                int a = frees.remove(r.nextInt(frees.size()));
                // Both come from the tiles that were free before either was taken.
                int b = frees.get(r.nextInt(frees.size()));
                kind[a] = pairs.get(p)[0];
                kind[b] = pairs.get(p)[1];
                on[a] = on[b] = false;
                sol[p * 2] = a;
                sol[p * 2 + 1] = b;
            }
            if (ok) {
                break;
            }
        }
        solution = sol;
        java.util.Arrays.fill(present, true);
        history.clear();
        selected = -1;
        hintPair = null;
        autoplaying = false;
        invalidate();
    }

    private void startOver() {
        java.util.Arrays.fill(present, true);
        history.clear();
        selected = -1;
        hintPair = null;
        invalidate();
    }

    private void selectGame() {
        prompt("Select Game", "Select Game Number:", String.valueOf(gameNumber), true, 5, t -> {
            try {
                newGame(Math.max(1, Integer.parseInt(t)));
            } catch (NumberFormatException ignored) {
                // nothing entered
            }
        });
    }

    boolean remove(int a, int b) {
        if (a == b || !isFree(a) || !isFree(b) || !matches(kind[a], kind[b])) {
            return false;
        }
        present[a] = present[b] = false;
        history.add(new int[] {a, b});
        hintPair = null;
        sound(Sfx.CLICK);
        afterRemove();
        return true;
    }

    private void afterRemove() {
        if (tilesLeft() == 0) {
            autoplaying = false;
            sound(Sfx.WIN);
            postDelayed(() -> messageBox("Taipei", FORTUNES[rng.nextInt(FORTUNES.length)], null), 300);
        } else if (findPair() == null && !autoplaying) {
            sound(Sfx.LOSE);
            postDelayed(() -> confirm("Taipei", "There are no more moves.\n\nBack up to try again?", this::backup, null), 300);
        }
        invalidate();
    }

    int[] findPair() {
        List<Integer> frees = new ArrayList<>();
        for (int i = 0; i < 144; i++) {
            if (isFree(i)) {
                frees.add(i);
            }
        }
        for (int a = 0; a < frees.size(); a++) {
            for (int b = a + 1; b < frees.size(); b++) {
                if (matches(kind[frees.get(a)], kind[frees.get(b)])) {
                    return new int[] {frees.get(a), frees.get(b)};
                }
            }
        }
        return null;
    }

    private void hint() {
        hintPair = findPair();
        if (hintPair == null && messages) {
            messageBox("Taipei", "There are no more moves.", null);
        }
        invalidate();
    }

    private void backup() {
        if (history.isEmpty()) {
            return;
        }
        int[] h = history.remove(history.size() - 1);
        present[h[0]] = present[h[1]] = true;
        selected = -1;
        invalidate();
    }

    /** Plays out the winning line from the current position, a pair at a time. */
    private void autoplay() {
        startOver();
        autoplaying = true;
        autoStep(0);
    }

    private void autoStep(int p) {
        if (!autoplaying || p >= 72) {
            autoplaying = false;
            return;
        }
        postDelayed(() -> {
            if (autoplaying && isActive()) {
                remove(solution[p * 2], solution[p * 2 + 1]);
                autoStep(p + 1);
            }
        }, 300);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void layoutClient(RectF r) {
        float pad = 8 * dp;
        // 15 tiles across plus up to four layers of depth; 8 tiles down.
        float byW = (r.width() - 2 * pad) / (15 + 4 * 0.14f);
        float byH = (r.height() - 2 * pad) / (8 * 1.28f + 4 * 0.14f);
        tw = Math.min(byW, byH);
        th = tw * 1.28f;
        depth = tw * 0.14f;
        float boardW = 15 * tw + 4 * depth, boardH = 8 * th + 4 * depth;
        originX = r.centerX() - boardW / 2 + depth;
        originY = r.centerY() - boardH / 2 + 4 * depth;
        int fw = Math.max(4, (int) (tw - depth)), fh = Math.max(4, (int) (th - depth));
        if (fw != faceW || fh != faceH) {
            faceW = fw;
            faceH = fh;
            java.util.Arrays.fill(faces, null);
        }
        order = new Integer[144];
        for (int i = 0; i < 144; i++) {
            order[i] = i;
        }
        // Draw lower layers first; within a layer, right to left and top to bottom so near tiles overlap far ones.
        java.util.Arrays.sort(order, (a, b) -> {
            int[] u = TURTLE[a], v = TURTLE[b];
            if (u[2] != v[2]) {
                return u[2] - v[2];
            }
            if (u[0] != v[0]) {
                return v[0] - u[0];
            }
            return u[1] - v[1];
        });
    }

    /** Top-left of tile {@code i}'s face. */
    private float[] facePos(int i) {
        int[] t = TURTLE[i];
        return new float[] {originX + t[0] / 2f * tw + t[2] * depth, originY + t[1] / 2f * th - t[2] * depth};
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(0xFF004080);
        if (order == null) {
            return;
        }
        for (int i : order) {
            if (present[i]) {
                drawTile(c, i);
            }
        }
    }

    private void drawTile(Canvas c, int i) {
        float[] p = facePos(i);
        float x = p[0], y = p[1];
        int side = darkSides ? 0xFF8A7A50 : 0xFFE8DCB0;
        int edge = darkSides ? 0xFF5A4A28 : 0xFFB8A878;
        // Thickness to the left and below.
        Path body = new Path();
        body.moveTo(x, y);
        body.lineTo(x - depth, y + depth);
        body.lineTo(x - depth, y + faceH + depth);
        body.lineTo(x + faceW - depth, y + faceH + depth);
        body.lineTo(x + faceW, y + faceH);
        body.lineTo(x, y + faceH);
        body.close();
        paint.setColor(side);
        c.drawPath(body, paint);
        paint.setColor(edge);
        c.drawRect(x - depth, y + faceH, x + faceW - depth, y + faceH + depth, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, tw / 60f));
        paint.setColor(0xFF000000);
        c.drawPath(body, paint);
        paint.setStyle(Paint.Style.FILL);
        Bitmap face = faces[kind[i]];
        if (face == null) {
            face = faces[kind[i]] = renderFace(kind[i], faceW, faceH);
        }
        c.drawBitmap(face, x, y, null);
        boolean lit = i == selected || hintPair != null && (hintPair[0] == i || hintPair[1] == i);
        if (lit) {
            paint.setColor(i == selected ? 0x80000080 : 0x80FF8000);
            c.drawRect(x, y, x + faceW, y + faceH, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF000000);
        c.drawRect(x, y, x + faceW, y + faceH, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private Bitmap renderFace(int k, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(0xFFFFFCEC);
        float cx = w / 2f;
        Typeface cjk = Typeface.create(Typeface.SERIF, Typeface.BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        if (k < 9) {
            drawDots(c, k + 1, w, h, p);
        } else if (k < 18) {
            drawBamboo(c, k - 8, w, h, p);
        } else if (k < 27) {
            p.setTypeface(cjk);
            p.setColor(0xFF000080);
            p.setTextSize(h * 0.36f);
            c.drawText(NUMERALS[k - 18], cx, h * 0.42f, p);
            p.setColor(0xFFD00000);
            p.setTextSize(h * 0.4f);
            c.drawText("萬", cx, h * 0.88f, p);
            corner(c, String.valueOf(k - 17), w, h, p);
        } else if (k < 31) {
            p.setTypeface(cjk);
            p.setColor(0xFF000000);
            p.setTextSize(h * 0.55f);
            c.drawText(WINDS[k - 27], cx, h * 0.7f, p);
            corner(c, WIND_LETTERS[k - 27], w, h, p);
        } else if (k == 31) {
            p.setTypeface(cjk);
            p.setColor(0xFFD00000);
            p.setTextSize(h * 0.6f);
            c.drawText("中", cx, h * 0.72f, p);
        } else if (k == 32) {
            p.setTypeface(cjk);
            p.setColor(0xFF008000);
            p.setTextSize(h * 0.6f);
            c.drawText("發", cx, h * 0.72f, p);
        } else if (k == 33) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.07f);
            p.setColor(0xFF0040C0);
            c.drawRect(w * 0.18f, h * 0.15f, w * 0.82f, h * 0.85f, p);
            p.setStrokeWidth(w * 0.03f);
            c.drawRect(w * 0.3f, h * 0.25f, w * 0.7f, h * 0.75f, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            boolean flower = k < 38;
            int idx = flower ? k - 34 : k - 38;
            int[] petals = {0xFFFF60A0, 0xFFA060FF, 0xFFFFC000, 0xFF20A040};
            p.setColor(petals[idx]);
            for (int a = 0; a < 5; a++) {
                double ang = a * Math.PI * 2 / 5 - Math.PI / 2;
                c.drawCircle(cx + (float) Math.cos(ang) * w * 0.14f, h * 0.3f + (float) Math.sin(ang) * w * 0.14f, w * 0.1f, p);
            }
            p.setColor(0xFFFFE000);
            c.drawCircle(cx, h * 0.3f, w * 0.07f, p);
            p.setTypeface(cjk);
            p.setColor(flower ? 0xFF006000 : 0xFF800000);
            p.setTextSize(h * 0.32f);
            c.drawText(flower ? FLOWERS[idx] : SEASONS[idx], cx, h * 0.85f, p);
            corner(c, flower ? "F" + (idx + 1) : "S" + (idx + 1), w, h, p);
        }
        return b;
    }

    private static void corner(Canvas c, String s, int w, int h, Paint p) {
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(h * 0.16f);
        p.setColor(0xFF000000);
        c.drawText(s, w * 0.06f, h * 0.17f, p);
        p.setTextAlign(Paint.Align.CENTER);
    }

    private static final float[][][] DOT_LAYOUTS = {
            {{.5f, .5f}},
            {{.5f, .28f}, {.5f, .72f}},
            {{.25f, .2f}, {.5f, .5f}, {.75f, .8f}},
            {{.3f, .28f}, {.7f, .28f}, {.3f, .72f}, {.7f, .72f}},
            {{.27f, .22f}, {.73f, .22f}, {.5f, .5f}, {.27f, .78f}, {.73f, .78f}},
            {{.3f, .2f}, {.7f, .2f}, {.3f, .5f}, {.7f, .5f}, {.3f, .8f}, {.7f, .8f}},
            {{.22f, .15f}, {.5f, .25f}, {.78f, .35f}, {.3f, .6f}, {.7f, .6f}, {.3f, .85f}, {.7f, .85f}},
            {{.3f, .14f}, {.7f, .14f}, {.3f, .38f}, {.7f, .38f}, {.3f, .62f}, {.7f, .62f}, {.3f, .86f}, {.7f, .86f}},
            {{.22f, .2f}, {.5f, .2f}, {.78f, .2f}, {.22f, .5f}, {.5f, .5f}, {.78f, .5f}, {.22f, .8f}, {.5f, .8f}, {.78f, .8f}}};

    private static void drawDots(Canvas c, int n, int w, int h, Paint p) {
        float r = n == 1 ? w * 0.3f : n <= 4 ? w * 0.17f : w * 0.12f;
        int[] colors = {0xFF0040C0, 0xFF008000, 0xFFD00000};
        float[][] layout = DOT_LAYOUTS[n - 1];
        for (int i = 0; i < layout.length; i++) {
            float x = layout[i][0] * w, y = layout[i][1] * h;
            p.setColor(colors[(i + n) % 3]);
            c.drawCircle(x, y, r, p);
            p.setColor(0xFFFFFCEC);
            c.drawCircle(x, y, r * 0.55f, p);
            p.setColor(colors[(i + n + 1) % 3]);
            c.drawCircle(x, y, r * 0.3f, p);
        }
    }

    private static void drawBamboo(Canvas c, int n, int w, int h, Paint p) {
        if (n == 1) {
            // The one of bamboo is traditionally a bird.
            p.setColor(0xFF008000);
            c.drawOval(w * 0.25f, h * 0.35f, w * 0.75f, h * 0.75f, p);
            c.drawCircle(w * 0.62f, h * 0.3f, w * 0.13f, p);
            p.setColor(0xFFD00000);
            Path tail = new Path();
            tail.moveTo(w * 0.3f, h * 0.55f);
            tail.lineTo(w * 0.08f, h * 0.4f);
            tail.lineTo(w * 0.12f, h * 0.7f);
            tail.close();
            c.drawPath(tail, p);
            c.drawCircle(w * 0.66f, h * 0.28f, w * 0.03f, p);
            return;
        }
        int cols = n <= 3 ? n : n <= 6 ? 3 : n == 8 ? 4 : 3;
        int rows = (n + cols - 1) / cols;
        float sw = w * 0.8f / cols, sh = h * 0.84f / rows;
        int k = 0;
        for (int r = 0; r < rows; r++) {
            int inRow = Math.min(cols, n - r * cols);
            float x0 = (w - inRow * sw) / 2;
            for (int col = 0; col < inRow; col++, k++) {
                float x = x0 + col * sw + sw * 0.25f, y = h * 0.08f + r * sh;
                p.setColor(k % 4 == 1 ? 0xFFD00000 : 0xFF008000);
                c.drawRoundRect(x, y, x + sw * 0.5f, y + sh * 0.9f, sw * 0.2f, sw * 0.2f, p);
                p.setColor(0xFFFFFCEC);
                c.drawRect(x, y + sh * 0.43f, x + sw * 0.5f, y + sh * 0.47f, p);
            }
        }
    }

    // ---------------------------------------------------------------- touch

    private int tileAt(float x, float y) {
        if (order == null) {
            return -1;
        }
        for (int k = order.length - 1; k >= 0; k--) {
            int i = order[k];
            if (!present[i]) {
                continue;
            }
            float[] p = facePos(i);
            if (x >= p[0] - depth && x < p[0] + faceW && y >= p[1] && y < p[1] + faceH + depth) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP || autoplaying) {
            return;
        }
        int i = tileAt(e.getX(), e.getY());
        if (i < 0) {
            selected = -1;
            return;
        }
        if (!isFree(i)) {
            sound(Sfx.DROP);
            return;
        }
        if (selected < 0 || selected == i) {
            selected = selected == i ? -1 : i;
            return;
        }
        if (!remove(selected, i)) {
            if (messages && !matches(kind[selected], kind[i])) {
                sound(Sfx.DROP);
            }
            selected = i;
        } else {
            selected = -1;
        }
    }

    @Override
    protected void onHidden() {
        autoplaying = false;
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_F2:
                newGame(1 + rng.nextInt(32000));
                return true;
            case KeyEvent.KEYCODE_H:
                hint();
                return true;
            case KeyEvent.KEYCODE_DEL:
                backup();
                return true;
            default:
                return false;
        }
    }

    int[] solutionForTest() {
        return solution;
    }
}
