package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;

/** Pegged: peg solitaire. Jump pegs over each other until only one is left. */
@SuppressLint("ViewConstructor")
public final class Pegged extends WinApp {
    static final String[] NAMES = {"&Cross", "&Plus", "&Fireplace", "Up &Arrow", "P&yramid", "&Diamond", "&Solitaire"};
    /** Each board on the 7x7 English layout: '#' peg, 'o' empty hole, ' ' no hole. */
    static final String[][] BOARDS = {
            {"  ooo  ", "  o#o  ", "oo###oo", "ooo#ooo", "ooo#ooo", "  ooo  ", "  ooo  "},
            {"  ooo  ", "  o#o  ", "ooo#ooo", "o#####o", "ooo#ooo", "  o#o  ", "  ooo  "},
            {"  ###  ", "  ###  ", "oo###oo", "oo#o#oo", "ooooooo", "  ooo  ", "  ooo  "},
            {"  o#o  ", "  ###  ", "o#####o", "ooo#ooo", "ooo#ooo", "  ###  ", "  ###  "},
            {"  ooo  ", "  o#o  ", "oo###oo", "o#####o", "#######", "  ooo  ", "  ooo  "},
            {"  o#o  ", "  ###  ", "o#####o", "###o###", "o#####o", "  ###  ", "  o#o  "},
            {"  ###  ", "  ###  ", "#######", "###o###", "#######", "  ###  ", "  ###  "}};

    int board;
    final int[] cells = new int[49];
    private final List<int[]> history = new ArrayList<>();
    private int selected = -1;
    private float cell;
    private final RectF area = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int dragFrom = -1;
    private float dragX, dragY;

    public Pegged(Context context, Host host) {
        super(context, host, "pegged");
        board = prefs.getInt("board", 6);
        newGame();
    }

    @Override
    public String title() {
        return "Pegged";
    }

    @Override
    public void save() {
        prefs.edit().putInt("board", board).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Start this board again", true, false, this::newGame));
            items.add(new Item("&Backup", "Bksp", "Take back the last jump", !history.isEmpty(), false, this::backup));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Pegged", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            for (int i = 0; i < NAMES.length; i++) {
                int b = i;
                items.add(new Item(NAMES[i], "", "Play the " + NAMES[i].replace("&", "") + " board", true, board == i, () -> {
                    board = b;
                    save();
                    newGame();
                }));
            }
        }));
        addHelpMenu(menus, "Remove pegs by jumping over them. A peg can jump over a neighbouring peg (up, down, "
                + "left or right) into an empty hole just beyond it; the peg it jumps over is removed.\n\n"
                + "Drag a peg to jump it, or tap a peg and then the hole it should land in.\n\n"
                + "The goal is to finish with a single peg, ideally in the centre hole.",
                "Pegged for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Pegs: " + pegs());
    }

    @Override
    protected String statusText() {
        return NAMES[board].replace("&", "");
    }

    int pegs() {
        int n = 0;
        for (int v : cells) {
            if (v == 2) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- rules (0 = no hole, 1 = empty, 2 = peg)

    void newGame() {
        String[] rows = BOARDS[board];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                char ch = rows[y].charAt(x);
                cells[y * 7 + x] = ch == '#' ? 2 : ch == 'o' ? 1 : 0;
            }
        }
        history.clear();
        selected = -1;
        invalidate();
    }

    /** The jumped-over hole for a jump from {@code a} to {@code b}, or -1 if not a legal jump. */
    int jumped(int a, int b) {
        if (a < 0 || b < 0 || cells[a] != 2 || cells[b] != 1) {
            return -1;
        }
        int ax = a % 7, ay = a / 7, bx = b % 7, by = b / 7;
        boolean straight = ax == bx && Math.abs(ay - by) == 2 || ay == by && Math.abs(ax - bx) == 2;
        if (!straight) {
            return -1;
        }
        int mid = (a + b) / 2;
        return cells[mid] == 2 ? mid : -1;
    }

    boolean jump(int a, int b) {
        int mid = jumped(a, b);
        if (mid < 0) {
            return false;
        }
        cells[a] = 1;
        cells[mid] = 1;
        cells[b] = 2;
        history.add(new int[] {a, mid, b});
        sound(Sfx.POP);
        if (!anyJump()) {
            int left = pegs();
            sound(left == 1 ? Sfx.WIN : Sfx.LOSE);
            String msg = left == 1 ? (cells[24] == 2 ? "Perfect! One peg, right in the centre." : "Well done! Only one peg left.")
                    : "No more jumps. " + left + " pegs left.";
            postDelayed(() -> confirm("Pegged", msg + "\n\nPlay again?", this::newGame, null), 300);
        }
        return true;
    }

    boolean anyJump() {
        for (int a = 0; a < 49; a++) {
            if (cells[a] != 2) {
                continue;
            }
            int[] targets = {a - 14, a + 14, a % 7 >= 2 ? a - 2 : -1, a % 7 <= 4 ? a + 2 : -1};
            for (int b : targets) {
                if (b >= 0 && b < 49 && jumped(a, b) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void backup() {
        if (history.isEmpty()) {
            return;
        }
        int[] h = history.remove(history.size() - 1);
        cells[h[0]] = 2;
        cells[h[1]] = 2;
        cells[h[2]] = 1;
        invalidate();
    }

    // ---------------------------------------------------------------- drawing and touch

    @Override
    protected void layoutClient(RectF r) {
        float size = Math.min(r.width(), r.height()) - 20 * dp;
        cell = size / 7;
        area.set(r.centerX() - size / 2, r.centerY() - size / 2, r.centerX() + size / 2, r.centerY() + size / 2);
    }

    @Override
    protected void drawClient(Canvas c) {
        paint.setShader(new LinearGradient(0, client.top, 0, client.bottom, 0xFF3A1A08, 0xFF1A0A02, Shader.TileMode.CLAMP));
        c.drawRect(client, paint);
        paint.setShader(null);
        // The wooden board.
        float r = area.width() * 0.52f;
        paint.setShader(new RadialGradient(area.centerX() - r * 0.3f, area.centerY() - r * 0.3f, r * 1.6f,
                0xFFE0A050, 0xFF8A4A18, Shader.TileMode.CLAMP));
        c.drawCircle(area.centerX(), area.centerY(), r, paint);
        paint.setShader(null);
        for (int i = 0; i < 49; i++) {
            if (cells[i] == 0) {
                continue;
            }
            float cx = area.left + (i % 7 + 0.5f) * cell, cy = area.top + (i / 7 + 0.5f) * cell;
            paint.setColor(0xFF4A2008);
            c.drawCircle(cx, cy, cell * 0.2f, paint);
            if (cells[i] == 2 && i != dragFrom) {
                drawPeg(c, cx, cy, i == selected);
            }
        }
        if (dragFrom >= 0) {
            drawPeg(c, dragX, dragY, true);
        }
    }

    private void drawPeg(Canvas c, float cx, float cy, boolean lit) {
        float rad = cell * 0.32f;
        paint.setColor(0x60000000);
        c.drawCircle(cx + rad * 0.15f, cy + rad * 0.2f, rad, paint);
        paint.setColor(lit ? 0xFFFF6000 : 0xFF2050E0);
        c.drawCircle(cx, cy, rad, paint);
        paint.setColor(lit ? 0xFFFFA040 : 0xFF6090FF);
        c.drawCircle(cx - rad * 0.12f, cy - rad * 0.12f, rad * 0.75f, paint);
        paint.setColor(0xE0FFFFFF);
        c.drawCircle(cx - rad * 0.35f, cy - rad * 0.35f, rad * 0.22f, paint);
    }

    private int holeAt(float x, float y) {
        int gx = (int) Math.floor((x - area.left) / cell), gy = (int) Math.floor((y - area.top) / cell);
        if (gx < 0 || gy < 0 || gx >= 7 || gy >= 7 || cells[gy * 7 + gx] == 0) {
            return -1;
        }
        return gy * 7 + gx;
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int h = holeAt(x, y);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragFrom = -1;
                if (h >= 0 && cells[h] == 2) {
                    dragFrom = h;
                    dragX = x;
                    dragY = y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                dragX = x;
                dragY = y;
                break;
            case MotionEvent.ACTION_UP: {
                int from = dragFrom;
                dragFrom = -1;
                if (from >= 0 && h != from && h >= 0) {
                    jump(from, h);
                    selected = -1;
                } else if (h >= 0 && cells[h] == 2) {
                    selected = selected == h ? -1 : h;
                } else if (h >= 0 && selected >= 0) {
                    if (!jump(selected, h)) {
                        sound(Sfx.DROP);
                    }
                    selected = -1;
                }
                break;
            }
            default:
                dragFrom = -1;
                break;
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            newGame();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            backup();
            return true;
        }
        return false;
    }
}
