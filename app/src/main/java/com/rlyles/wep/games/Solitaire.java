package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.rlyles.wep.cards.Card;
import com.rlyles.wep.cards.CardGame;
import com.rlyles.wep.win.Win95;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Klondike, as in the classic Windows Solitaire, with the bouncing-cards win. */
@SuppressLint("ViewConstructor")
public final class Solitaire extends CardGame {
    private static final long DOUBLE_TAP_MS = 350;

    public final Klondike game = new Klondike();
    private final Random rng = new Random();
    private final float touchSlop;

    // options
    private boolean drawThree, timed, statusBar, outline, cumulative, tapToMove;
    private int scoring;

    // geometry
    private float gap, fanDown, fanUp, wasteStep;
    private final float[] colX = new float[7];
    private float rowY, tabY, playTop, playBottom;

    // interaction
    private float downX, downY;
    private int downPile = -1, downIndex = -1;
    private boolean dragging;
    private int dragPile, dragIndex;
    private final List<Card> dragCards = new ArrayList<>();
    private float[] dragOffsets = new float[0];
    private float grabDX, grabDY, dragX, dragY;
    private long lastTapTime;
    private int lastTapPile = -2, lastTapIndex = -2;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // win animation
    private boolean celebrated;
    private Bitmap winBmp;
    private Canvas winCanvas;
    private int winLaunched;
    private Bouncer bouncer;

    public Solitaire(Context context, Host host) {
        super(context, host, "sol");
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        drawThree = prefs.getBoolean("drawThree", true);
        scoring = prefs.getInt("scoring", Klondike.SCORE_STANDARD);
        timed = prefs.getBoolean("timed", true);
        statusBar = prefs.getBoolean("statusBar", true);
        outline = prefs.getBoolean("outline", false);
        cumulative = prefs.getBoolean("cumulative", false);
        tapToMove = prefs.getBoolean("tapToMove", true);
        if (!game.load(prefs.getString("game", null))) {
            newDeal(false);
        }
        celebrated = game.won();
    }

    @Override
    public String title() {
        return "Solitaire";
    }

    @Override
    public void save() {
        prefs.edit()
                .putBoolean("drawThree", drawThree).putInt("scoring", scoring).putBoolean("timed", timed)
                .putBoolean("statusBar", statusBar).putBoolean("outline", outline)
                .putBoolean("cumulative", cumulative).putBoolean("tapToMove", tapToMove)
                .putString("game", game.save()).apply();
    }

    // ---------------------------------------------------------------- menus and status

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&Deal", "F2", "Deal a new game", true, false, this::deal));
            items.add(Item.sep());
            items.add(new Item("&Undo", "", "Undo last action", game.canUndo(), false, this::undo));
            items.add(new Item("De&ck...", "", "Choose new deck back", true, false, this::showDeckDialog));
            items.add(new Item("&Options...", "", "Change Solitaire options", true, false, this::showOptionsDialog));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Solitaire", true, false, () -> host.closeApp(this)));
        }));
        addHelpMenu(menus, HELP, "A remake of the classic Windows Solitaire (Klondike).\n\nLoad your own cards.dll "
                + "from Game > Deck to play with the original card artwork.");
    }

    private static final String HELP = "Build each suit up from Ace to King on the four foundations at the top right.\n\n"
            + "In the seven columns, stack cards down in alternating colours. Only a King can go in an empty "
            + "column. Tap a face-down card to turn it over.\n\n"
            + "Tap the deck to deal cards. When it runs out, tap the empty spot to turn the pile over again.\n\n"
            + "Drag cards to move them. With Tap to move on, tapping a card sends it to the best spot. "
            + "Double-tap a card to send it to a foundation, or double-tap the green table to send up "
            + "everything that can go.\n\n"
            + "Standard scoring: +10 to a foundation, +5 deck to column, +5 turning a card, -15 off a "
            + "foundation; turning the deck over costs 100 (draw one) or 20 after three passes (draw three). "
            + "A timed game loses 2 points every 10 seconds and earns a bonus when you win.\n\n"
            + "Vegas: you pay $52 to play and win $5 for every card you play up. You get one pass through "
            + "the deck when drawing one, three when drawing three.";

    @Override
    protected boolean hasStatusBar() {
        return statusBar;
    }

    @Override
    protected String statusText() {
        return winBmp != null ? "Tap the screen to stop..." : "";
    }

    @Override
    protected void statusPanels(List<String> panels) {
        if (winBmp != null) {
            return;
        }
        if (game.won() && game.bonus() > 0) {
            panels.add("Bonus: " + game.bonus());
        }
        if (game.scoring() == Klondike.SCORE_STANDARD) {
            panels.add("Score: " + game.score());
        } else if (game.scoring() == Klondike.SCORE_VEGAS) {
            panels.add("Score: $" + game.score());
        }
        if (game.timed()) {
            panels.add("Time: " + game.seconds());
        }
    }

    @Override
    protected void tick() {
        if (game.timed() && game.started() && !game.won()) {
            game.tick();
            invalidate();
        }
    }

    // ---------------------------------------------------------------- dealing

    private void newDeal(boolean keepVegasTotal) {
        int carry = keepVegasTotal && cumulative && game.scoring() == Klondike.SCORE_VEGAS
                && scoring == Klondike.SCORE_VEGAS ? game.score() : 0;
        game.deal(rng.nextLong(), drawThree, scoring, timed, carry);
        celebrated = false;
        finishFlights();
        java.util.Arrays.fill(hidden, false);
        cancelDrag();
        save();
        invalidate();
    }

    public void deal() {
        stopWinAnimationSilently();
        newDeal(true);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void layoutClient(RectF r) {
        stopWinAnimationSilently();
        playTop = r.top;
        playBottom = r.bottom;
        float byWidth = r.width() / (7f + 8f * 11f / 71f);
        float byHeight = r.height() / 3.1f * 71f / 96f;
        setCardWidth(Math.min(byWidth, byHeight));
        gap = cw * 11f / 71f;
        float left = r.left + (r.width() - (7 * cw + 6 * gap)) / 2f;
        for (int i = 0; i < 7; i++) {
            colX[i] = Math.round(left + i * (cw + gap));
        }
        rowY = Math.round(playTop + gap * 0.7f);
        tabY = Math.round(rowY + ch + gap);
        fanDown = ch * 0.1f;
        fanUp = ch * 0.3f;
        wasteStep = cw * 0.22f;
    }

    private float[] columnOffsets(int pile) {
        List<Card> p = game.pile(pile);
        int n = p.size();
        float[] y = new float[n];
        float need = 0;
        for (int i = 0; i < n - 1; i++) {
            need += p.get(i).faceUp ? fanUp : fanDown;
        }
        float avail = playBottom - tabY - ch - gap * 0.5f;
        float k = need > avail && need > 0 ? Math.max(0f, avail) / need : 1f;
        float acc = 0;
        for (int i = 0; i < n; i++) {
            y[i] = acc;
            if (i < n - 1) {
                acc += (p.get(i).faceUp ? fanUp : fanDown) * k;
            }
        }
        return y;
    }

    private PointF basePosition(int pile) {
        if (pile == Klondike.STOCK) {
            return new PointF(colX[0], rowY);
        } else if (pile == Klondike.WASTE) {
            return new PointF(colX[1], rowY);
        } else if (Klondike.isFoundation(pile)) {
            return new PointF(colX[3 + pile - Klondike.FOUNDATION], rowY);
        }
        return new PointF(colX[pile - Klondike.TABLEAU], tabY);
    }

    public PointF positionOf(int pile, int index) {
        PointF p = basePosition(pile);
        if (pile == Klondike.WASTE) {
            int n = game.pile(pile).size();
            int first = n - game.wasteFan();
            if (index >= first) {
                p.x += (index - first) * wasteStep;
            }
        } else if (Klondike.isTableau(pile) && index > 0) {
            float[] off = columnOffsets(pile);
            if (index < off.length) {
                p.y += off[index];
            }
        }
        return p;
    }

    public int cardWidth() {
        return cw;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawClient(Canvas c) {
        if (winBmp != null) {
            stepWinAnimation();
            if (winBmp != null) {
                c.drawBitmap(winBmp, 0, 0, null);
                postInvalidateOnAnimation();
                return;
            }
        }
        drawTable(c);
        boolean animating = flying();
        drawFlights(c);
        drawDrag(c);
        if (!animating && game.won() && !celebrated && !dragging && !hasDialog()) {
            startWinAnimation();
        }
    }

    @Override
    protected void onFlightsDone() {
        postInvalidateOnAnimation();
    }

    private void drawTable(Canvas c) {
        c.drawColor(FELT);
        drawStock(c);
        drawWaste(c);
        for (int f = Klondike.FOUNDATION; f < Klondike.FOUNDATION + 4; f++) {
            drawFoundation(c, f);
        }
        for (int t = Klondike.TABLEAU; t < Klondike.TABLEAU + 7; t++) {
            drawColumn(c, t);
        }
    }

    private void drawStock(Canvas c) {
        List<Card> stock = game.pile(Klondike.STOCK);
        PointF p = basePosition(Klondike.STOCK);
        int n = stock.size();
        if (n == 0) {
            drawEmpty(c, p.x, p.y);
            art.drawStockMarker(c, new RectF(p.x, p.y, p.x + cw, p.y + ch), game.canRecycle());
            return;
        }
        int layers = Math.min(3, 1 + (n - 1) / 10);
        float step = Math.max(1f, cw / 71f * 2f);
        for (int i = 0; i < layers; i++) {
            art.drawBack(c, back(), p.x + i * step, p.y + i * step / 2);
        }
    }

    private void drawWaste(Canvas c) {
        List<Card> waste = game.pile(Klondike.WASTE);
        int n = waste.size();
        int first = n - game.wasteFan();
        for (int i = Math.max(0, first - 1); i < n; i++) {
            Card card = waste.get(i);
            if (hidden[card.id()]) {
                continue;
            }
            PointF p = positionOf(Klondike.WASTE, i);
            art.drawFace(c, card, p.x, p.y);
        }
    }

    private void drawFoundation(Canvas c, int pile) {
        List<Card> f = game.pile(pile);
        PointF p = basePosition(pile);
        for (int i = f.size() - 1; i >= 0; i--) {
            if (!hidden[f.get(i).id()]) {
                art.drawFace(c, f.get(i), p.x, p.y);
                return;
            }
        }
        drawEmpty(c, p.x, p.y);
    }

    private void drawColumn(Canvas c, int pile) {
        List<Card> col = game.pile(pile);
        PointF base = basePosition(pile);
        float[] off = columnOffsets(pile);
        boolean any = false;
        for (int i = 0; i < col.size(); i++) {
            Card card = col.get(i);
            if (hidden[card.id()]) {
                continue;
            }
            drawCard(c, card, base.x, base.y + off[i]);
            any = true;
        }
        if (!any) {
            drawEmpty(c, base.x, base.y);
        }
    }

    private void drawDrag(Canvas c) {
        if (!dragging) {
            return;
        }
        if (outline) {
            float bottom = dragY + dragOffsets[dragOffsets.length - 1] + ch;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, cw / 35f));
            paint.setColor(0xFF000000);
            c.drawRect(dragX, dragY, dragX + cw, bottom, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        for (int i = 0; i < dragCards.size(); i++) {
            art.drawFace(c, dragCards.get(i), dragX, dragY + dragOffsets[i]);
        }
    }

    // ---------------------------------------------------------------- touch

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (winBmp != null) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                stopWinAnimation();
            }
            return;
        }
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
                if (!dragging && downPile >= 0 && game.canPick(downPile, downIndex)
                        && Math.hypot(x - downX, y - downY) > touchSlop) {
                    startDrag(downPile, downIndex, downX, downY);
                }
                if (dragging) {
                    dragX = x - grabDX;
                    dragY = y - grabDY;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    drop(x, y);
                } else {
                    tap(downPile, downIndex);
                }
                break;
            default:
                cancelDrag();
                break;
        }
        invalidate();
    }

    private int[] hitTest(float x, float y) {
        PointF p = basePosition(Klondike.STOCK);
        if (inCard(x, y, p.x, p.y)) {
            return new int[] {Klondike.STOCK, game.pile(Klondike.STOCK).size() - 1};
        }
        List<Card> waste = game.pile(Klondike.WASTE);
        for (int i = waste.size() - 1; i >= Math.max(0, waste.size() - game.wasteFan() - 1); i--) {
            p = positionOf(Klondike.WASTE, i);
            if (inCard(x, y, p.x, p.y)) {
                return new int[] {Klondike.WASTE, i == waste.size() - 1 ? i : -2};
            }
        }
        for (int f = Klondike.FOUNDATION; f < Klondike.FOUNDATION + 4; f++) {
            p = basePosition(f);
            if (inCard(x, y, p.x, p.y)) {
                return new int[] {f, game.pile(f).size() - 1};
            }
        }
        for (int t = Klondike.TABLEAU; t < Klondike.TABLEAU + 7; t++) {
            List<Card> col = game.pile(t);
            PointF base = basePosition(t);
            if (x < base.x || x >= base.x + cw) {
                continue;
            }
            if (col.isEmpty()) {
                return inCard(x, y, base.x, base.y) ? new int[] {t, -1} : new int[] {-1, -1};
            }
            float[] off = columnOffsets(t);
            for (int i = col.size() - 1; i >= 0; i--) {
                float top = base.y + off[i];
                float bottom = i == col.size() - 1 ? top + ch : base.y + off[i + 1];
                if (y >= top && y < bottom) {
                    return new int[] {t, i};
                }
            }
        }
        return new int[] {-1, -1};
    }

    private void startDrag(int pile, int index, float fingerX, float fingerY) {
        List<Card> src = game.pile(pile);
        dragging = true;
        dragPile = pile;
        dragIndex = index;
        dragCards.clear();
        dragCards.addAll(src.subList(index, src.size()));
        dragOffsets = new float[dragCards.size()];
        if (Klondike.isTableau(pile)) {
            float[] off = columnOffsets(pile);
            for (int i = 0; i < dragOffsets.length; i++) {
                dragOffsets[i] = off[index + i] - off[index];
            }
        }
        PointF p = positionOf(pile, index);
        grabDX = fingerX - p.x;
        grabDY = fingerY - p.y;
        dragX = p.x;
        dragY = p.y;
        if (!outline) {
            for (Card c : dragCards) {
                hidden[c.id()] = true;
            }
        }
    }

    private void cancelDrag() {
        for (Card c : dragCards) {
            hidden[c.id()] = false;
        }
        dragCards.clear();
        dragging = false;
    }

    private void drop(float fingerX, float fingerY) {
        RectF dr = new RectF(dragX, dragY, dragX + cw, dragY + ch);
        int best = -1;
        float bestArea = 0;
        for (int p = Klondike.FOUNDATION; p < Klondike.PILE_COUNT; p++) {
            if (p == dragPile || !game.canDrop(dragPile, dragIndex, p)) {
                continue;
            }
            int n = game.pile(p).size();
            PointF t = n == 0 ? basePosition(p) : positionOf(p, n - 1);
            RectF tr = new RectF(t.x, t.y, t.x + cw, t.y + ch);
            if (tr.intersect(dr)) {
                float area = tr.width() * tr.height();
                if (area > bestArea) {
                    bestArea = area;
                    best = p;
                }
            }
        }
        if (best < 0) {
            for (int t = Klondike.TABLEAU; t < Klondike.TABLEAU + 7; t++) {
                PointF b = basePosition(t);
                if (fingerX >= b.x && fingerX < b.x + cw && fingerY >= b.y && t != dragPile
                        && game.canDrop(dragPile, dragIndex, t)) {
                    best = t;
                }
            }
        }
        List<Card> moving = new ArrayList<>(dragCards);
        float[] fromY = dragOffsets.clone();
        float fromX = dragX, fromTop = dragY;
        cancelDrag();
        if (best >= 0) {
            int destStart = game.pile(best).size();
            game.move(dragPile, dragIndex, best);
            if (!outline) {
                for (int i = 0; i < moving.size(); i++) {
                    PointF to = positionOf(best, destStart + i);
                    fly(moving.get(i), fromX, fromTop + fromY[i], to.x, to.y, 0, 90);
                }
            }
            afterAction();
        } else if (!outline) {
            for (int i = 0; i < moving.size(); i++) {
                PointF to = positionOf(dragPile, dragIndex + i);
                fly(moving.get(i), fromX, fromTop + fromY[i], to.x, to.y, 0, 140);
            }
        }
    }

    private void tap(int pile, int index) {
        long now = SystemClock.uptimeMillis();
        boolean doubleTap = now - lastTapTime < DOUBLE_TAP_MS && pile == lastTapPile && index == lastTapIndex;
        lastTapTime = doubleTap ? 0 : now;
        lastTapPile = pile;
        lastTapIndex = index;

        if (pile == Klondike.STOCK) {
            clickStock();
            return;
        }
        if (pile < 0) {
            if (doubleTap) {
                playAllToFoundations();
            }
            return;
        }
        if (index < 0) {
            return;
        }
        Card card = game.pile(pile).get(index);
        if (Klondike.isTableau(pile) && !card.faceUp) {
            if (index == game.pile(pile).size() - 1 && game.flip(pile)) {
                afterAction();
            }
            return;
        }
        if (!game.canPick(pile, index) || Klondike.isFoundation(pile)) {
            return;
        }
        if (tapToMove) {
            autoMove(pile, index);
        } else if (doubleTap && index == game.pile(pile).size() - 1) {
            int f = game.foundationFor(pile);
            if (f >= 0) {
                moveAnimated(pile, index, f);
            }
        }
    }

    private void clickStock() {
        List<Card> stock = game.pile(Klondike.STOCK);
        int before = game.pile(Klondike.WASTE).size();
        boolean drawing = !stock.isEmpty();
        PointF from = basePosition(Klondike.STOCK);
        if (!game.drawOrRecycle()) {
            return;
        }
        if (drawing) {
            List<Card> waste = game.pile(Klondike.WASTE);
            for (int i = before; i < waste.size(); i++) {
                PointF to = positionOf(Klondike.WASTE, i);
                fly(waste.get(i), from.x, from.y, to.x, to.y, (i - before) * 40L, 110);
            }
        }
        afterAction();
    }

    private void autoMove(int pile, int index) {
        int size = game.pile(pile).size();
        if (index == size - 1) {
            int f = game.foundationFor(pile);
            if (f >= 0) {
                moveAnimated(pile, index, f);
                return;
            }
        }
        int emptyTarget = -1;
        for (int k = 1; k <= 7; k++) {
            int start = Klondike.isTableau(pile) ? pile - Klondike.TABLEAU : -1;
            int t = Klondike.TABLEAU + (start + k + 7) % 7;
            if (t == pile || !game.canDrop(pile, index, t)) {
                continue;
            }
            if (game.pile(t).isEmpty()) {
                if (emptyTarget < 0 && !(Klondike.isTableau(pile) && index == 0)) {
                    emptyTarget = t;
                }
                continue;
            }
            moveAnimated(pile, index, t);
            return;
        }
        if (emptyTarget >= 0) {
            moveAnimated(pile, index, emptyTarget);
        }
    }

    private void moveAnimated(int from, int index, int to) {
        List<Card> moving = new ArrayList<>(game.pile(from).subList(index, game.pile(from).size()));
        PointF[] start = new PointF[moving.size()];
        for (int i = 0; i < start.length; i++) {
            start[i] = positionOf(from, index + i);
        }
        int destStart = game.pile(to).size();
        if (!game.move(from, index, to)) {
            return;
        }
        for (int i = 0; i < moving.size(); i++) {
            PointF end = positionOf(to, destStart + i);
            fly(moving.get(i), start[i].x, start[i].y, end.x, end.y, 0, 130);
        }
        afterAction();
    }

    private void playAllToFoundations() {
        float[][] startPos = new float[52][];
        for (int p = Klondike.WASTE; p < Klondike.PILE_COUNT; p++) {
            List<Card> pile = game.pile(p);
            for (int i = 0; i < pile.size(); i++) {
                PointF pt = positionOf(p, i);
                startPos[pile.get(i).id()] = new float[] {pt.x, pt.y};
            }
        }
        List<int[]> moves = new ArrayList<>();
        if (game.playAllToFoundations(moves) == 0) {
            return;
        }
        for (int i = 0; i < moves.size(); i++) {
            int to = moves.get(i)[1];
            int later = 0;
            for (int j = i + 1; j < moves.size(); j++) {
                if (moves.get(j)[1] == to) {
                    later++;
                }
            }
            List<Card> f = game.pile(to);
            Card card = f.get(f.size() - 1 - later);
            PointF end = basePosition(to);
            float[] s = startPos[card.id()];
            fly(card, s[0], s[1], end.x, end.y, i * 70L, 140);
        }
        afterAction();
    }

    public void undo() {
        finishFlights();
        cancelDrag();
        if (game.undo()) {
            afterAction();
        }
        invalidate();
    }

    private void afterAction() {
        save();
        invalidate();
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            deal();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_Z && event.isCtrlPressed()) {
            undo();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- win animation

    private static final class Bouncer {
        Card card;
        float x, y, vx, vy;
    }

    private void startWinAnimation() {
        celebrated = true;
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        winBmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        winCanvas = new Canvas(winBmp);
        winCanvas.clipRect(client);
        drawTable(winCanvas);
        winLaunched = 0;
        bouncer = null;
        sound(com.rlyles.wep.win.Sfx.WIN);
        postInvalidateOnAnimation();
    }

    private void stepWinAnimation() {
        float s = cw / 71f;
        for (int step = 0; step < 2 && winBmp != null; step++) {
            if (bouncer == null) {
                if (winLaunched >= 52) {
                    stopWinAnimation();
                    return;
                }
                int f = winLaunched % 4;
                int fromTop = winLaunched / 4;
                List<Card> pile = game.pile(Klondike.FOUNDATION + f);
                PointF p = basePosition(Klondike.FOUNDATION + f);
                bouncer = new Bouncer();
                bouncer.card = pile.get(pile.size() - 1 - fromTop);
                bouncer.x = p.x;
                bouncer.y = p.y;
                float speed = (2.5f + rng.nextFloat() * 4.5f) * s;
                bouncer.vx = rng.nextBoolean() ? speed : -speed;
                bouncer.vy = -rng.nextFloat() * 9f * s;
                winCanvas.save();
                winCanvas.clipRect(p.x, p.y, p.x + cw, p.y + ch);
                winCanvas.drawColor(FELT);
                if (fromTop < 12) {
                    art.drawFace(winCanvas, pile.get(pile.size() - 2 - fromTop), p.x, p.y);
                } else {
                    drawEmpty(winCanvas, p.x, p.y);
                }
                winCanvas.restore();
                winLaunched++;
            }
            Bouncer b = bouncer;
            b.x += b.vx;
            b.y += b.vy;
            b.vy += 0.9f * s;
            float floor = playBottom - ch;
            if (b.y > floor) {
                b.y = floor;
                b.vy = -b.vy * 0.78f;
            }
            art.drawFace(winCanvas, b.card, b.x, b.y);
            if (b.x + cw < client.left || b.x > client.right) {
                bouncer = null;
            }
        }
    }

    private void stopWinAnimationSilently() {
        if (winBmp != null) {
            winBmp.recycle();
        }
        winBmp = null;
        winCanvas = null;
        bouncer = null;
    }

    private void stopWinAnimation() {
        stopWinAnimationSilently();
        confirm("Solitaire", "Deal Again?", this::deal, null);
    }

    public boolean winAnimationRunning() {
        return winBmp != null;
    }

    public void stepWinAnimationForTest(int frames) {
        for (int i = 0; i < frames && winBmp != null; i++) {
            stepWinAnimation();
        }
    }

    @Override
    protected void onHidden() {
        if (winBmp != null) {
            stopWinAnimationSilently();
        }
    }

    // ---------------------------------------------------------------- options

    public void showOptionsDialog() {
        Win95.Dialog d = new Win95.Dialog("Options", 336, 290);
        d.add(new Win95.Group("Draw")).at(8, 4, 152, 100);
        Win95.Radio drawOne = d.add(new Win95.Radio("Draw &One", 0, !drawThree));
        drawOne.at(20, 32, 136, 30);
        Win95.Radio three = d.add(new Win95.Radio("Draw &Three", 0, drawThree));
        three.at(20, 64, 136, 30);
        d.add(new Win95.Group("Scoring")).at(172, 4, 156, 130);
        Win95.Radio standard = d.add(new Win95.Radio("St&andard", 1, scoring == Klondike.SCORE_STANDARD));
        standard.at(184, 32, 136, 30);
        Win95.Radio vegas = d.add(new Win95.Radio("&Vegas", 1, scoring == Klondike.SCORE_VEGAS));
        vegas.at(184, 64, 136, 30);
        Win95.Radio none = d.add(new Win95.Radio("&None", 1, scoring == Klondike.SCORE_NONE));
        none.at(184, 96, 136, 30);
        Win95.Check timedBox = d.add(new Win95.Check("T&imed game", timed));
        timedBox.at(12, 112, 150, 30);
        Win95.Check status = d.add(new Win95.Check("Status &bar", statusBar));
        status.at(12, 144, 150, 30);
        Win95.Check outlineBox = d.add(new Win95.Check("Out&line dragging", outline));
        outlineBox.at(12, 176, 160, 30);
        Win95.Check tap = d.add(new Win95.Check("&Tap to move", tapToMove));
        tap.at(12, 208, 150, 30);
        Win95.Check cumulativeBox = d.add(new Win95.Check("&Cumulative Score", cumulative));
        cumulativeBox.at(176, 144, 156, 30);
        Runnable sync = () -> cumulativeBox.enabled = vegas.checked;
        sync.run();
        standard.onChange = sync;
        vegas.onChange = sync;
        none.onChange = sync;

        d.add(new Win95.Button("OK", true, () -> {
            int newScoring = vegas.checked ? Klondike.SCORE_VEGAS : none.checked ? Klondike.SCORE_NONE
                    : Klondike.SCORE_STANDARD;
            boolean redeal = three.checked != drawThree || newScoring != scoring;
            drawThree = three.checked;
            scoring = newScoring;
            timed = timedBox.checked;
            statusBar = status.checked;
            outline = outlineBox.checked;
            tapToMove = tap.checked;
            cumulative = cumulativeBox.checked;
            game.setTimed(timed);
            closeDialog(d);
            relayout();
            if (redeal) {
                deal();
            } else {
                save();
            }
        })).at(60, 246, 100, 36);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(176, 246, 100, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }
}
