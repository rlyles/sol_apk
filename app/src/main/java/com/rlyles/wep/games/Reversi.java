package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.WinApp;

import java.util.List;

/** Reversi against the computer, Windows 3.x style: you are red, the computer is blue. */
@SuppressLint("ViewConstructor")
public final class Reversi extends WinApp {
    static final int EMPTY = 0, HUMAN = 1, COMPUTER = 2;
    private static final String[] SKILLS = {"Beginner", "Novice", "Expert", "Master"};
    private static final int[] DEPTH = {1, 2, 3, 5};
    private static final int[] WEIGHTS = {
            100, -20, 10, 5, 5, 10, -20, 100,
            -20, -50, -2, -2, -2, -2, -50, -20,
            10, -2, 1, 1, 1, 1, -2, 10,
            5, -2, 1, 0, 0, 1, -2, 5,
            5, -2, 1, 0, 0, 1, -2, 5,
            10, -2, 1, 1, 1, 1, -2, 10,
            -20, -50, -2, -2, -2, -2, -50, -20,
            100, -20, 10, 5, 5, 10, -20, 100};

    final int[] board = new int[64];
    int skill;
    boolean thinking, over;
    private int hint = -1, lastMove = -1;
    private String status = "";

    private float cell;
    private final RectF boardRect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Reversi(Context context, Host host) {
        super(context, host, "reversi");
        skill = prefs.getInt("skill", 1);
        newGame();
    }

    @Override
    public String title() {
        return "Reversi";
    }

    @Override
    public void save() {
        prefs.edit().putInt("skill", skill).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&Hint", "", "Show a good move", !thinking && !over, false, this::hint));
            items.add(new Item("&Pass", "", "Let the computer move", !thinking && !over && !hasMoves(board, HUMAN), false,
                    this::pass));
            items.add(new Item("&New", "", "Start a new game", true, false, this::newGame));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Reversi", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Skill", items -> {
            for (int i = 0; i < SKILLS.length; i++) {
                int s = i;
                items.add(new Item("&" + SKILLS[i], "", SKILLS[i] + " computer player", true, skill == i, () -> {
                    skill = s;
                    save();
                }));
            }
        }));
        addHelpMenu(menus, "Take turns placing pieces. Each piece you place must trap one or more of the "
                + "computer's pieces in a straight line (across, down or diagonally) between it and another of "
                + "yours; the trapped pieces turn to your colour.\n\nYou play red and move first. If you cannot "
                + "move, choose Pass. The game ends when neither side can move, and whoever has more pieces wins.",
                "Reversi for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Red: " + count(HUMAN));
        panels.add("Blue: " + count(COMPUTER));
    }

    @Override
    protected String statusText() {
        return status;
    }

    int count(int who) {
        int n = 0;
        for (int v : board) {
            if (v == who) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- rules

    void newGame() {
        java.util.Arrays.fill(board, EMPTY);
        board[27] = board[36] = COMPUTER;
        board[28] = board[35] = HUMAN;
        over = thinking = false;
        hint = lastMove = -1;
        status = "Your move.";
        invalidate();
    }

    static int flips(int[] b, int pos, int who, boolean apply) {
        if (b[pos] != EMPTY) {
            return 0;
        }
        int other = 3 - who, total = 0;
        int x0 = pos % 8, y0 = pos / 8;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int x = x0 + dx, y = y0 + dy, n = 0;
                while (x >= 0 && y >= 0 && x < 8 && y < 8 && b[y * 8 + x] == other) {
                    x += dx;
                    y += dy;
                    n++;
                }
                if (n > 0 && x >= 0 && y >= 0 && x < 8 && y < 8 && b[y * 8 + x] == who) {
                    total += n;
                    if (apply) {
                        for (int i = 1; i <= n; i++) {
                            b[(y0 + dy * i) * 8 + x0 + dx * i] = who;
                        }
                    }
                }
            }
        }
        if (apply && total > 0) {
            b[pos] = who;
        }
        return total;
    }

    static boolean hasMoves(int[] b, int who) {
        for (int i = 0; i < 64; i++) {
            if (flips(b, i, who, false) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int evaluate(int[] b) {
        int s = 0, mine = 0, theirs = 0;
        for (int i = 0; i < 64; i++) {
            if (b[i] == COMPUTER) {
                s += WEIGHTS[i];
                mine++;
            } else if (b[i] == HUMAN) {
                s -= WEIGHTS[i];
                theirs++;
            }
        }
        if (mine + theirs > 54) {
            s += (mine - theirs) * 8;
        }
        return s;
    }

    private static int search(int[] b, int depth, int alpha, int beta, int who) {
        boolean me = hasMoves(b, who);
        if (depth == 0 || !me && !hasMoves(b, 3 - who)) {
            if (!me && !hasMoves(b, 3 - who)) {
                int diff = 0;
                for (int v : b) {
                    diff += v == COMPUTER ? 1 : v == HUMAN ? -1 : 0;
                }
                return diff * 1000;
            }
            return evaluate(b);
        }
        if (!me) {
            return search(b, depth - 1, alpha, beta, 3 - who);
        }
        int[] copy = new int[64];
        if (who == COMPUTER) {
            int best = Integer.MIN_VALUE;
            for (int i = 0; i < 64; i++) {
                System.arraycopy(b, 0, copy, 0, 64);
                if (flips(copy, i, who, true) == 0) {
                    continue;
                }
                best = Math.max(best, search(copy, depth - 1, alpha, beta, HUMAN));
                alpha = Math.max(alpha, best);
                if (beta <= alpha) {
                    break;
                }
            }
            return best;
        }
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < 64; i++) {
            System.arraycopy(b, 0, copy, 0, 64);
            if (flips(copy, i, who, true) == 0) {
                continue;
            }
            best = Math.min(best, search(copy, depth - 1, alpha, beta, COMPUTER));
            beta = Math.min(beta, best);
            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }

    /** The best move for {@code who} at the given depth, or -1. */
    static int bestMove(int[] b, int who, int depth) {
        int best = -1, bestScore = 0;
        int[] copy = new int[64];
        for (int i = 0; i < 64; i++) {
            System.arraycopy(b, 0, copy, 0, 64);
            if (flips(copy, i, who, true) == 0) {
                continue;
            }
            int score = search(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, 3 - who);
            if (who == HUMAN) {
                score = -score;
            }
            if (best < 0 || score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    void humanMove(int pos) {
        if (thinking || over || flips(board, pos, HUMAN, true) == 0) {
            if (!thinking && !over) {
                sound(Sfx.DROP);
            }
            return;
        }
        hint = -1;
        lastMove = pos;
        sound(Sfx.CLICK);
        computerTurn();
    }

    private void pass() {
        if (hasMoves(board, HUMAN)) {
            status = "You can still move.";
            return;
        }
        computerTurn();
    }

    private void computerTurn() {
        if (checkOver()) {
            return;
        }
        if (!hasMoves(board, COMPUTER)) {
            status = "I have to pass. Your move.";
            invalidate();
            return;
        }
        thinking = true;
        status = "Thinking...";
        invalidate();
        postDelayed(() -> {
            int m = bestMove(board, COMPUTER, DEPTH[skill]);
            if (m >= 0) {
                flips(board, m, COMPUTER, true);
                lastMove = m;
                sound(Sfx.POP);
            }
            thinking = false;
            if (!checkOver()) {
                status = hasMoves(board, HUMAN) ? "Your move." : "You must pass.";
            }
            invalidate();
        }, 350);
    }

    private boolean checkOver() {
        if (hasMoves(board, HUMAN) || hasMoves(board, COMPUTER)) {
            return false;
        }
        over = true;
        int r = count(HUMAN), b = count(COMPUTER);
        status = r > b ? "You won by " + (r - b) + "!" : r < b ? "You lost by " + (b - r) + "." : "Tie game.";
        sound(r > b ? Sfx.WIN : Sfx.LOSE);
        messageBox("Reversi", status, null);
        invalidate();
        return true;
    }

    private void hint() {
        hint = bestMove(board, HUMAN, Math.max(2, DEPTH[skill]));
        invalidate();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void layoutClient(RectF r) {
        float size = Math.min(r.width(), r.height()) - 16 * dp;
        cell = size / 8;
        boardRect.set(r.centerX() - size / 2, r.centerY() - size / 2, r.centerX() + size / 2, r.centerY() + size / 2);
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(0xFF808080);
        ui.sunken(c, new RectF(boardRect.left - 4 * dp, boardRect.top - 4 * dp, boardRect.right + 4 * dp,
                boardRect.bottom + 4 * dp), 0xFFC0C0C0);
        for (int i = 0; i < 64; i++) {
            float x = boardRect.left + (i % 8) * cell, y = boardRect.top + (i / 8) * cell;
            RectF sq = new RectF(x, y, x + cell, y + cell);
            ui.raised(c, sq);
            if (i == hint) {
                paint.setColor(0xFF000000);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * dp);
                c.drawRect(sq.left + 4 * dp, sq.top + 4 * dp, sq.right - 4 * dp, sq.bottom - 4 * dp, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (board[i] != EMPTY) {
                int base = board[i] == HUMAN ? 0xFFFF0000 : 0xFF0000FF;
                float cx = sq.centerX(), cy = sq.centerY(), rad = cell * 0.38f;
                paint.setColor(0x60000000);
                c.drawCircle(cx + 2 * dp, cy + 2 * dp, rad, paint);
                paint.setShader(new RadialGradient(cx - rad * 0.35f, cy - rad * 0.35f, rad * 1.3f,
                        0xFFFFFFFF, base, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rad, paint);
                paint.setShader(null);
                if (i == lastMove) {
                    paint.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy, rad * 0.12f, paint);
                }
            }
        }
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP || !boardRect.contains(e.getX(), e.getY())) {
            return;
        }
        int x = (int) ((e.getX() - boardRect.left) / cell), y = (int) ((e.getY() - boardRect.top) / cell);
        if (x >= 0 && y >= 0 && x < 8 && y < 8) {
            humanMove(y * 8 + x);
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
