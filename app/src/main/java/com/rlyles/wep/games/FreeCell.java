package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.rlyles.wep.cards.Card;
import com.rlyles.wep.cards.CardGame;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** FreeCell, with Microsoft's numbered deals, the king who watches your moves, and statistics. */
@SuppressLint("ViewConstructor")
public final class FreeCell extends CardGame {
    private static final int M = FreeCellModel.PILES;

    public final FreeCellModel model = new FreeCellModel();
    private final Random rng = new Random();
    private final float touchSlop;
    private boolean messages;

    // geometry
    private final PointF[] base = new PointF[M];
    private float fanUp, columnTop, bottom;
    private final RectF king = new RectF();
    private boolean kingLooksLeft = true;

    // interaction
    private int selected = -1;
    private float downX, downY;
    private int downPile = -1, downIndex = -1;
    private boolean dragging;
    private int dragPile, dragIndex;
    private final List<Card> dragCards = new ArrayList<>();
    private float dragX, dragY, grabDX, grabDY;
    private long lastTap;
    private int lastTapPile = -1;
    private boolean counted;
    private int sessionWon, sessionLost;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FreeCell(Context context, Host host) {
        super(context, host, "freecell");
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        messages = prefs.getBoolean("messages", true);
        for (int i = 0; i < M; i++) {
            base[i] = new PointF();
        }
        if (!model.load(prefs.getString("game", null))) {
            model.deal(1 + rng.nextInt(32000));
        }
        counted = model.won() || model.moves() == 0;
    }

    @Override
    public String title() {
        return "FreeCell";
    }

    @Override
    protected String menuBarText() {
        return "Cards Left: " + model.cardsLeft();
    }

    @Override
    public void save() {
        prefs.edit().putString("game", model.save()).putBoolean("messages", messages).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New Game", "F2", "Start a new random game", true, false, () -> newGame(1 + rng.nextInt(32000))));
            items.add(new Item("&Select Game...", "F3", "Choose a game by number", true, false, this::selectGame));
            items.add(new Item("&Restart Game", "", "Play this game again from the start", true, false, this::restart));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit FreeCell", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Statistics...", "F4", "Show your record", true, false, this::statistics));
            items.add(new Item("&Messages", "", "Tell me why a move is not allowed", true, messages, () -> messages = !messages));
            items.add(Item.sep());
            items.add(new Item("De&ck...", "", "Choose the card back", true, false, this::showDeckDialog));
        }));
        addHelpMenu(menus, HELP, "FreeCell for the Entertainment Pack. Game numbers 1 to 32000 deal the same "
                + "layouts as the original, and almost all of them can be won.");
    }

    private static final String HELP = "Move all the cards to the four home cells at the upper right, building "
            + "each suit up from Ace to King.\n\n"
            + "In the columns, stack cards down in alternating colours. The four free cells at the upper left each "
            + "hold one card. Any card can go in an empty column.\n\n"
            + "Tap a card to select it, then tap where it should go; or drag it. Double-tap a card to move it to a "
            + "free cell. You can move a run of cards at once if there are enough free cells and empty columns to "
            + "do it one card at a time.\n\n"
            + "Cards that are no longer needed in the columns go home by themselves.";

    // ---------------------------------------------------------------- games and statistics

    private void newGame(int number) {
        recordLossIfAbandoned();
        model.deal(number);
        startedGame();
    }

    private void restart() {
        recordLossIfAbandoned();
        model.restart();
        startedGame();
    }

    private void startedGame() {
        counted = false;
        selected = -1;
        finishFlights();
        java.util.Arrays.fill(hidden, false);
        save();
        invalidate();
    }

    private void recordLossIfAbandoned() {
        if (!counted && model.moves() > 0 && !model.won()) {
            record(false);
        }
        counted = true;
    }

    private void record(boolean won) {
        if (won) {
            sessionWon++;
        } else {
            sessionLost++;
        }
        int streak = prefs.getInt("streak", 0);
        streak = won ? Math.max(1, streak + 1) : Math.min(-1, streak - 1);
        prefs.edit().putInt(won ? "won" : "lost", prefs.getInt(won ? "won" : "lost", 0) + 1)
                .putInt("streak", streak)
                .putInt("bestWins", Math.max(prefs.getInt("bestWins", 0), streak))
                .putInt("bestLosses", Math.max(prefs.getInt("bestLosses", 0), -streak)).apply();
    }

    private void selectGame() {
        prompt("Game Number", "Select a game number from 1 to 32000:", String.valueOf(1 + rng.nextInt(32000)), true, 5,
                text -> {
                    try {
                        int n = Integer.parseInt(text);
                        if (n >= 1 && n <= 32000) {
                            newGame(n);
                        }
                    } catch (NumberFormatException ignored) {
                        // nothing entered
                    }
                });
    }

    private void statistics() {
        int won = prefs.getInt("won", 0), lost = prefs.getInt("lost", 0);
        int pct = won + lost == 0 ? 0 : Math.round(100f * won / (won + lost));
        int streak = prefs.getInt("streak", 0);
        int sessionTotal = sessionWon + sessionLost;
        String body = "This session:   " + sessionWon + " won, " + sessionLost + " lost"
                + (sessionTotal > 0 ? "  (" + Math.round(100f * sessionWon / sessionTotal) + "%)" : "") + "\n"
                + "Total:   " + won + " won, " + lost + " lost  (" + pct + "%)\n\n"
                + "Streaks\n"
                + "Wins:   " + prefs.getInt("bestWins", 0) + "\n"
                + "Losses:   " + prefs.getInt("bestLosses", 0) + "\n"
                + "Current:   " + Math.abs(streak) + (streak > 0 ? " wins" : streak < 0 ? " losses" : "");
        Win95.Dialog d = new Win95.Dialog("FreeCell Statistics", 300, 250);
        d.add(new Win95.Text(body, false)).at(14, 10, 276, 190);
        d.add(new Win95.Button("OK", true, () -> closeDialog(d))).at(36, 204, 100, 36);
        d.add(new Win95.Button("&Clear", false, () -> {
            prefs.edit().remove("won").remove("lost").remove("streak").remove("bestWins").remove("bestLosses").apply();
            sessionWon = sessionLost = 0;
            closeDialog(d);
        })).at(164, 204, 100, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void layoutClient(RectF r) {
        float gapK = 0.13f;
        float byWidth = r.width() / (8 + 9 * gapK);
        float byHeight = r.height() / 3.4f * 71f / 96f;
        setCardWidth(Math.min(byWidth, byHeight));
        float gap = cw * gapK;
        float left = r.left + (r.width() - (8 * cw + 7 * gap)) / 2;
        float top = r.top + gap * 0.6f;
        float rowLeft = r.left + (r.width() - (8 * cw + 9 * gap)) / 2;
        for (int i = 0; i < 4; i++) {
            base[FreeCellModel.CELL + i].set(rowLeft + i * cw, top);
            base[FreeCellModel.HOME + i].set(rowLeft + 9 * gap + (4 + i) * cw, top);
        }
        float ks = Math.min(9 * gap * 0.8f, ch * 0.7f);
        float kx = rowLeft + 4 * cw + 4.5f * gap;
        king.set(kx - ks / 2, top + ch / 2 - ks / 2, kx + ks / 2, top + ch / 2 + ks / 2);
        columnTop = top + ch + gap * 1.2f;
        for (int i = 0; i < 8; i++) {
            base[FreeCellModel.COLUMN + i].set(Math.round(left + i * (cw + gap)), columnTop);
        }
        bottom = r.bottom - gap * 0.5f;
        fanUp = ch * 0.28f;
    }

    private float step(int pile) {
        int n = model.pile(pile).size();
        if (n <= 1) {
            return fanUp;
        }
        return Math.min(fanUp, (bottom - columnTop - ch) / (n - 1));
    }

    private PointF positionOf(int pile, int index) {
        PointF b = base[pile];
        if (FreeCellModel.isColumn(pile)) {
            return new PointF(b.x, b.y + index * step(pile));
        }
        return new PointF(b.x, b.y);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(FELT);
        for (int p = 0; p < M; p++) {
            List<Card> pile = model.pile(p);
            if (FreeCellModel.isColumn(p)) {
                for (int i = 0; i < pile.size(); i++) {
                    Card card = pile.get(i);
                    if (hidden[card.id()]) {
                        continue;
                    }
                    PointF pos = positionOf(p, i);
                    if (p == selected && i == pile.size() - 1) {
                        art.drawFaceInverted(c, card, pos.x, pos.y);
                    } else {
                        art.drawFace(c, card, pos.x, pos.y);
                    }
                }
            } else {
                PointF pos = base[p];
                drawCell(c, pos.x, pos.y);
                for (int i = pile.size() - 1; i >= 0; i--) {
                    if (!hidden[pile.get(i).id()]) {
                        if (p == selected) {
                            art.drawFaceInverted(c, pile.get(i), pos.x, pos.y);
                        } else {
                            art.drawFace(c, pile.get(i), pos.x, pos.y);
                        }
                        break;
                    }
                }
            }
        }
        drawKing(c);
        drawFlights(c);
        if (dragging) {
            for (int i = 0; i < dragCards.size(); i++) {
                art.drawFace(c, dragCards.get(i), dragX, dragY + i * step(dragPile));
            }
        }
    }

    /** Free cells and home cells are drawn as sunken black-and-green boxes, as in the original. */
    private void drawCell(Canvas c, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, cw / 45f));
        paint.setColor(0xFF000000);
        c.drawLine(x, y, x + cw, y, paint);
        c.drawLine(x, y, x, y + ch, paint);
        paint.setColor(0xFF00FF00);
        c.drawLine(x + cw, y, x + cw, y + ch, paint);
        c.drawLine(x, y + ch, x + cw, y + ch, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** The king between the free cells and home cells turns to look at your last move. */
    private void drawKing(Canvas c) {
        c.save();
        if (!kingLooksLeft) {
            c.scale(-1, 1, king.centerX(), king.centerY());
        }
        float s = king.width() / 32f;
        c.translate(king.left, king.top);
        c.scale(s, s);
        boolean happy = model.won();
        paint.setColor(0xFFFFD000);
        Path crown = new Path();
        crown.moveTo(8, 10);
        crown.lineTo(9, 2);
        crown.lineTo(13, 7);
        crown.lineTo(16, 1);
        crown.lineTo(19, 7);
        crown.lineTo(23, 2);
        crown.lineTo(24, 10);
        crown.close();
        c.drawPath(crown, paint);
        paint.setColor(0xFFFFD8B0);
        c.drawOval(9, 9, 23, 25, paint);
        paint.setColor(0xFFA0522D);
        c.drawRect(9, 21, 23, 31, paint);
        paint.setColor(0xFFFFD8B0);
        c.drawOval(11, 17, 21, 24, paint);
        paint.setColor(0xFF000000);
        c.drawCircle(happy ? 16 : 13.5f, 15, 1.2f, paint);
        c.drawCircle(happy ? 19 : 16.5f, 15, 1.2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        c.drawArc(new RectF(13, 17, 19, 22), happy ? 20 : 200, 140, false, paint);
        paint.setStyle(Paint.Style.FILL);
        c.restore();
    }

    // ---------------------------------------------------------------- touch

    private int[] hitTest(float x, float y) {
        for (int p = 0; p < 8; p++) {
            if (inCard(x, y, base[p].x, base[p].y)) {
                return new int[] {p, model.pile(p).size() - 1};
            }
        }
        for (int p = FreeCellModel.COLUMN; p < M; p++) {
            PointF b = base[p];
            if (x < b.x || x >= b.x + cw || y < b.y) {
                continue;
            }
            List<Card> pile = model.pile(p);
            for (int i = pile.size() - 1; i >= 0; i--) {
                PointF pos = positionOf(p, i);
                if (y >= pos.y && (y < pos.y + (i == pile.size() - 1 ? ch : step(p)))) {
                    return new int[] {p, i};
                }
            }
            return new int[] {p, -1};
        }
        return new int[] {-1, -1};
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                finishFlights();
                downX = x;
                downY = y;
                int[] hit = hitTest(x, y);
                downPile = hit[0];
                downIndex = hit[1];
                dragging = false;
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (!dragging && downPile >= 0 && model.canPick(downPile, downIndex)
                        && Math.hypot(x - downX, y - downY) > touchSlop) {
                    startDrag();
                }
                if (dragging) {
                    dragX = x - grabDX;
                    dragY = y - grabDY;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    drop();
                } else {
                    tap(downPile);
                }
                break;
            default:
                cancelDrag();
                break;
        }
        invalidate();
    }

    private void startDrag() {
        selected = -1;
        dragging = true;
        dragPile = downPile;
        dragIndex = downIndex;
        List<Card> src = model.pile(dragPile);
        dragCards.clear();
        dragCards.addAll(src.subList(dragIndex, src.size()));
        for (Card c : dragCards) {
            hidden[c.id()] = true;
        }
        PointF p = positionOf(dragPile, dragIndex);
        grabDX = downX - p.x;
        grabDY = downY - p.y;
        dragX = p.x;
        dragY = p.y;
    }

    private void cancelDrag() {
        for (Card c : dragCards) {
            hidden[c.id()] = false;
        }
        dragCards.clear();
        dragging = false;
    }

    private void drop() {
        RectF dr = new RectF(dragX, dragY, dragX + cw, dragY + ch);
        int best = -1;
        float bestArea = 0;
        for (int p = 0; p < M; p++) {
            if (p == dragPile) {
                continue;
            }
            List<Card> pile = model.pile(p);
            PointF t = positionOf(p, Math.max(0, pile.size() - 1));
            RectF tr = new RectF(t.x, t.y, t.x + cw, t.y + ch);
            if (tr.intersect(dr) && tr.width() * tr.height() > bestArea && model.canMove(dragPile, dragIndex, p)) {
                bestArea = tr.width() * tr.height();
                best = p;
            }
        }
        List<Card> moving = new ArrayList<>(dragCards);
        float fx = dragX, fy = dragY, st = step(dragPile);
        int from = dragPile, index = dragIndex;
        cancelDrag();
        if (best >= 0) {
            int destStart = model.pile(best).size();
            model.move(from, index, best);
            for (int i = 0; i < moving.size(); i++) {
                PointF to = positionOf(best, destStart + i);
                fly(moving.get(i), fx, fy + i * st, to.x, to.y, 0, 90);
            }
            afterMove(best);
        } else {
            for (int i = 0; i < moving.size(); i++) {
                PointF to = positionOf(from, index + i);
                fly(moving.get(i), fx, fy + i * st, to.x, to.y, 0, 140);
            }
            if (model.canPick(from, index) && messages && moving.size() > 1) {
                notAllowed(moving.size());
            }
        }
    }

    private void tap(int pile) {
        if (pile < 0) {
            selected = -1;
            return;
        }
        long now = SystemClock.uptimeMillis();
        boolean twice = now - lastTap < 350 && pile == lastTapPile;
        lastTap = twice ? 0 : now;
        lastTapPile = pile;
        if (twice && !FreeCellModel.isCell(pile) && !FreeCellModel.isHome(pile) && !model.pile(pile).isEmpty()) {
            selected = -1;
            for (int cell = FreeCellModel.CELL; cell < FreeCellModel.CELL + 4; cell++) {
                if (model.pile(cell).isEmpty()) {
                    moveAnimated(pile, model.pile(pile).size() - 1, cell);
                    return;
                }
            }
            return;
        }
        if (selected < 0) {
            if (!model.pile(pile).isEmpty() && !FreeCellModel.isHome(pile)) {
                selected = pile;
                sound(Sfx.CLICK);
            }
            return;
        }
        if (pile == selected) {
            selected = -1;
            return;
        }
        int from = selected;
        selected = -1;
        if (FreeCellModel.isColumn(pile) && model.pile(pile).isEmpty() && FreeCellModel.isColumn(from)) {
            int run = model.bestRunFor(from, pile);
            int top = model.pile(from).size() - 1;
            if (run >= 0 && run < top) {
                Win95.Dialog d = new Win95.Dialog("Move to Empty Column...", 280, 100);
                d.add(new Win95.Button("Move &column", true, () -> {
                    closeDialog(d);
                    moveAnimated(from, run, pile);
                })).at(12, 20, 124, 40);
                d.add(new Win95.Button("Move &single card", false, () -> {
                    closeDialog(d);
                    moveAnimated(from, top, pile);
                })).at(144, 20, 124, 40);
                d.onCancel = () -> closeDialog(d);
                showDialog(d);
                return;
            }
        }
        int run = model.bestRunFor(from, pile);
        if (run >= 0) {
            moveAnimated(from, run, pile);
        } else if (messages) {
            notAllowed(1);
        }
    }

    private void notAllowed(int cards) {
        sound(Sfx.DROP);
        messageBox("FreeCell", cards > 1 ? "That move requires moving " + cards
                + " cards. You do not have enough free space to move that many." : "That move is not allowed.", null);
    }

    private void moveAnimated(int from, int index, int to) {
        List<Card> moving = new ArrayList<>(model.pile(from).subList(index, model.pile(from).size()));
        PointF[] start = new PointF[moving.size()];
        for (int i = 0; i < start.length; i++) {
            start[i] = positionOf(from, index + i);
        }
        int destStart = model.pile(to).size();
        if (!model.move(from, index, to)) {
            return;
        }
        for (int i = 0; i < moving.size(); i++) {
            PointF end = positionOf(to, destStart + i);
            fly(moving.get(i), start[i].x, start[i].y, end.x, end.y, 0, 130);
        }
        afterMove(to);
    }

    private void afterMove(int to) {
        PointF t = base[to];
        kingLooksLeft = t.x + cw / 2f < king.centerX();
        long delay = 130;
        int[] auto;
        while ((auto = model.autoMove()) != null) {
            Card card = model.top(auto[0]);
            PointF s = positionOf(auto[0], model.pile(auto[0]).size() - 1);
            model.move(auto[0], model.pile(auto[0]).size() - 1, auto[1]);
            PointF end = base[auto[1]];
            fly(card, s.x, s.y, end.x, end.y, delay, 120);
            delay += 70;
            kingLooksLeft = false;
        }
        save();
        if (model.won()) {
            counted = true;
            record(true);
            sound(Sfx.WIN);
            postDelayed(() -> confirm("Game Over", "Congratulations, you win!\n\nDo you want to play again?",
                    () -> newGame(1 + rng.nextInt(32000)), null), delay + 200);
        } else if (model.stuck()) {
            sound(Sfx.LOSE);
            postDelayed(() -> confirm("Game Over", "Sorry, you lose.\n\nDo you want to play this game again?",
                    this::restart, () -> newGame(1 + rng.nextInt(32000))), delay + 200);
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            newGame(1 + rng.nextInt(32000));
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_F3) {
            selectGame();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_F4) {
            statistics();
            return true;
        }
        return false;
    }

    @Override
    protected String caption() {
        return "FreeCell Game #" + model.gameNumber();
    }
}
