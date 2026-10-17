package com.rlyles.solitaire;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole Solitaire window: Win95 title bar, menu bar, the green table, status bar, dialogs
 * and the bouncing-cards win animation, all drawn on one Canvas.
 */
@SuppressLint("ViewConstructor")
public final class SolitaireView extends View {

    /** Things the view needs from its Activity. */
    interface Host {
        void exitApp();

        void minimize();

        void pickCardsDll();

        void useBuiltInCards();
    }

    private static final int FELT = 0xFF008000;
    private static final int EMPTY_OUTLINE = 0xFF004000;
    private static final long DOUBLE_TAP_MS = 350;

    private final Host host;
    private final Prefs prefs;
    final Game game = new Game();
    final CardArt art = new CardArt();
    private final Win95 ui;
    private final Random rng = new Random();
    private final float touchSlop;

    // ---------------------------------------------------------------- geometry
    private int viewW, viewH;
    private final RectF titleBar = new RectF();
    private final RectF minBox = new RectF(), maxBox = new RectF(), closeBox = new RectF();
    private final RectF menuBar = new RectF();
    private final RectF statusBar = new RectF();
    private final RectF[] menuTitles = {new RectF(), new RectF()};
    private float playTop, playBottom;
    private int cw, ch;
    private float gap, fanDown, fanUp, wasteStep;
    private final float[] colX = new float[7];
    private float rowY, tabY;

    // ---------------------------------------------------------------- interaction state
    private enum Owner { NONE, MENU, TITLE, DIALOG, GAME }

    private Owner owner = Owner.NONE;
    private int titlePressed = -1;
    private float downX, downY;
    private int downPile = -1, downIndex = -1;
    private boolean dragging;
    private int dragPile, dragIndex;
    private final List<Card> dragCards = new ArrayList<>();
    private float[] dragOffsets = new float[0];
    private float grabDX, grabDY, dragX, dragY;
    private long lastTapTime;
    private int lastTapPile = -2, lastTapIndex = -2;
    private final boolean[] hidden = new boolean[52];

    private final List<Flight> flights = new ArrayList<>();

    // menus
    private int openMenu = -1;
    private int hotItem = -1;
    private final RectF dropdown = new RectF();
    private List<MenuItem> menuItems = new ArrayList<>();
    private final List<RectF> itemRects = new ArrayList<>();

    private Win95.Dialog dialog;

    // win animation
    private boolean celebrated;
    private Bitmap winBmp;
    private Canvas winCanvas;
    private int winLaunched;
    private Bouncer bouncer;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r1 = new RectF();
    private boolean running;

    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            if (game.timed() && game.started() && !game.won()) {
                game.tick();
                invalidate();
            }
            postDelayed(this, 1000);
        }
    };

    public SolitaireView(Context context, Host host, Prefs prefs) {
        super(context);
        this.host = host;
        this.prefs = prefs;
        ui = new Win95(getResources().getDisplayMetrics().density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setFocusableInTouchMode(true);
        if (!game.load(prefs.game)) {
            newDeal(false);
        }
        celebrated = game.won();
    }

    // ---------------------------------------------------------------- lifecycle

    void onResumeGame() {
        running = true;
        removeCallbacks(clock);
        postDelayed(clock, 1000);
    }

    void onPauseGame() {
        running = false;
        removeCallbacks(clock);
        saveState();
    }

    /** Back button: closes whatever is open. Returns false when there is nothing to close. */
    boolean onBack() {
        if (winBmp != null) {
            stopWinAnimation();
            return true;
        }
        if (dialog != null) {
            if (dialog.onCancel != null) {
                dialog.onCancel.run();
            } else {
                dialog = null;
            }
            invalidate();
            return true;
        }
        if (openMenu >= 0) {
            closeMenu();
            return true;
        }
        return false;
    }

    void setDll(CardsDll dll) {
        art.setDll(dll);
        prefs.back = art.clampBack(prefs.back);
        if (dialog != null && "Select Card Back".equals(dialog.title)) {
            showDeckDialog();
        }
        invalidate();
    }

    void showMessage(String message) {
        Win95.Dialog d = new Win95.Dialog("Solitaire", 300, 150);
        d.add(new Win95.Text(message, false)).at(12, 12, 276, 80);
        d.add(new Win95.Button("OK", true, this::closeDialog)).at(102, 104, 96, 36);
        d.onCancel = this::closeDialog;
        showDialog(d);
    }

    private void saveState() {
        prefs.game = game.save();
        prefs.save();
    }

    // ---------------------------------------------------------------- dealing

    private void newDeal(boolean keepVegasTotal) {
        int carry = keepVegasTotal && prefs.cumulative && game.scoring() == Game.SCORE_VEGAS ? game.score() : 0;
        if (prefs.scoring != Game.SCORE_VEGAS) {
            carry = 0;
        }
        game.deal(rng.nextLong(), prefs.drawThree, prefs.scoring, prefs.timed, carry);
        celebrated = false;
        flights.clear();
        clearHidden();
        cancelDrag();
        saveState();
        invalidate();
    }

    void deal() {
        stopWinAnimationSilently();
        newDeal(true);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        viewW = w;
        viewH = h;
        closeMenu();
        relayout();
        if (winBmp != null) {
            stopWinAnimationSilently();
        }
    }

    private void relayout() {
        float d = ui.dp;
        titleBar.set(0, 0, viewW, 30 * d);
        float bs = titleBar.height() - 8 * d;
        closeBox.set(viewW - 4 * d - bs * 1.15f, 4 * d, viewW - 4 * d, 4 * d + bs);
        maxBox.set(closeBox.left - 3 * d - bs * 1.15f, closeBox.top, closeBox.left - 3 * d, closeBox.bottom);
        minBox.set(maxBox.left - bs * 1.15f, closeBox.top, maxBox.left, closeBox.bottom);
        menuBar.set(0, titleBar.bottom, viewW, titleBar.bottom + 34 * d);
        float x = 4 * d;
        String[] titles = {"&Game", "&Help"};
        for (int i = 0; i < 2; i++) {
            float w = ui.textWidth(titles[i]) + 20 * d;
            menuTitles[i].set(x, menuBar.top + 3 * d, x + w, menuBar.bottom - 3 * d);
            x += w;
        }
        float statusH = prefs.statusBar ? 30 * d : 0;
        statusBar.set(0, viewH - statusH, viewW, viewH);
        playTop = menuBar.bottom;
        playBottom = viewH - statusH;

        float byWidth = viewW / (7f + 8f * 11f / 71f);
        float byHeight = (playBottom - playTop) / 3.1f * 71f / 96f;
        cw = Math.max(8, (int) Math.min(byWidth, byHeight));
        ch = Math.round(cw * 96f / 71f);
        gap = cw * 11f / 71f;
        float left = (viewW - (7 * cw + 6 * gap)) / 2f;
        for (int i = 0; i < 7; i++) {
            colX[i] = Math.round(left + i * (cw + gap));
        }
        rowY = Math.round(playTop + gap * 0.7f);
        tabY = Math.round(rowY + ch + gap);
        fanDown = ch * 0.1f;
        fanUp = ch * 0.3f;
        wasteStep = cw * 0.22f;
        art.setSize(cw, ch);
        if (dialog != null) {
            dialog.layout(ui, viewW, viewH);
        }
    }

    /** Y offsets of each card in a tableau column, squeezed to fit above the status bar. */
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
        if (pile == Game.STOCK) {
            return new PointF(colX[0], rowY);
        } else if (pile == Game.WASTE) {
            return new PointF(colX[1], rowY);
        } else if (Game.isFoundation(pile)) {
            return new PointF(colX[3 + pile - Game.FOUNDATION], rowY);
        }
        return new PointF(colX[pile - Game.TABLEAU], tabY);
    }

    /** Where card {@code index} of {@code pile} is drawn. */
    PointF positionOf(int pile, int index) {
        PointF p = basePosition(pile);
        if (pile == Game.WASTE) {
            int n = game.pile(pile).size();
            int first = n - game.wasteFan();
            if (index >= first) {
                p.x += (index - first) * wasteStep;
            }
        } else if (Game.isTableau(pile) && index > 0) {
            float[] off = columnOffsets(pile);
            if (index < off.length) {
                p.y += off[index];
            }
        }
        return p;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        if (winBmp != null) {
            stepWinAnimation();
            if (winBmp != null) {
                c.drawBitmap(winBmp, 0, 0, null);
                drawChrome(c);
                postInvalidateOnAnimation();
                return;
            }
        }
        drawTable(c);
        boolean animating = drawFlights(c);
        drawDrag(c);
        drawChrome(c);
        if (openMenu >= 0) {
            drawMenu(c);
        }
        if (dialog != null) {
            dialog.draw(c, ui);
        }
        if (animating) {
            postInvalidateOnAnimation();
        } else if (game.won() && !celebrated && !dragging && dialog == null) {
            startWinAnimation();
        }
    }

    private void drawTable(Canvas c) {
        c.save();
        c.clipRect(0, playTop, viewW, playBottom);
        c.drawColor(FELT);
        drawStock(c);
        drawWaste(c);
        for (int f = Game.FOUNDATION; f < Game.FOUNDATION + 4; f++) {
            drawFoundation(c, f);
        }
        for (int t = Game.TABLEAU; t < Game.TABLEAU + 7; t++) {
            drawColumn(c, t);
        }
        c.restore();
    }

    private void drawEmpty(Canvas c, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, cw / 50f));
        paint.setColor(EMPTY_OUTLINE);
        float in = paint.getStrokeWidth() / 2;
        float rad = cw * 0.055f;
        c.drawRoundRect(x + in, y + in, x + cw - in, y + ch - in, rad, rad, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStock(Canvas c) {
        List<Card> stock = game.pile(Game.STOCK);
        PointF p = basePosition(Game.STOCK);
        int n = stock.size();
        if (n == 0) {
            drawEmpty(c, p.x, p.y);
            r1.set(p.x, p.y, p.x + cw, p.y + ch);
            art.drawStockMarker(c, r1, game.canRecycle());
            return;
        }
        // A thicker stock shows a couple of extra card edges, as the original did.
        int layers = Math.min(3, 1 + (n - 1) / 10);
        float step = Math.max(1f, cw / 71f * 2f);
        for (int i = 0; i < layers; i++) {
            art.drawBack(c, prefs.back, p.x + i * step, p.y + i * step / 2);
        }
    }

    private void drawWaste(Canvas c) {
        List<Card> waste = game.pile(Game.WASTE);
        int n = waste.size();
        if (n == 0) {
            return;
        }
        int first = n - game.wasteFan();
        for (int i = Math.max(0, first - 1); i < n; i++) {
            Card card = waste.get(i);
            if (hidden[card.id()]) {
                continue;
            }
            PointF p = positionOf(Game.WASTE, i);
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
        if (col.isEmpty()) {
            drawEmpty(c, base.x, base.y);
            return;
        }
        float[] off = columnOffsets(pile);
        boolean any = false;
        for (int i = 0; i < col.size(); i++) {
            Card card = col.get(i);
            if (hidden[card.id()]) {
                continue;
            }
            art.drawCard(c, card, prefs.back, base.x, base.y + off[i]);
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
        if (prefs.outline) {
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

    // ---------------------------------------------------------------- chrome

    private void drawChrome(Canvas c) {
        // Title bar.
        paint.setColor(Win95.TITLE);
        c.drawRect(titleBar, paint);
        float d = ui.dp;
        drawAppIcon(c, 6 * d, titleBar.top + 5 * d, titleBar.height() - 10 * d);
        Typeface old = ui.text.getTypeface();
        ui.text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        ui.label(c, "Solitaire", 6 * d + titleBar.height(), ui.baseline(titleBar), Win95.TITLE_TEXT);
        ui.text.setTypeface(old);
        drawCaptionButton(c, minBox, 0, titlePressed == 0);
        drawCaptionButton(c, maxBox, 1, false);
        drawCaptionButton(c, closeBox, 2, titlePressed == 2);

        // Menu bar.
        paint.setColor(Win95.FACE);
        c.drawRect(menuBar, paint);
        String[] titles = {"&Game", "&Help"};
        for (int i = 0; i < 2; i++) {
            RectF r = menuTitles[i];
            boolean open = openMenu == i;
            if (open) {
                ui.shallow(c, r);
            }
            float off = open ? ui.px : 0;
            ui.label(c, titles[i], r.left + 10 * d + off, ui.baseline(r) + off, Win95.DARK);
        }
        paint.setColor(Win95.SHADOW);
        c.drawRect(0, menuBar.bottom - ui.px, viewW, menuBar.bottom, paint);

        if (prefs.statusBar) {
            drawStatusBar(c);
        }
    }

    private void drawCaptionButton(Canvas c, RectF r, int kind, boolean pressed) {
        ui.button(c, r, "", pressed, true, false);
        float off = pressed ? ui.px : 0;
        paint.setColor(kind == 1 ? Win95.SHADOW : Win95.DARK);
        float s = r.height() * 0.22f, cx = r.centerX() + off, cy = r.centerY() + off;
        float lw = Math.max(2f, r.height() * 0.1f);
        if (kind == 0) {
            c.drawRect(cx - s, cy + s - lw, cx + s * 0.6f, cy + s, paint);
        } else if (kind == 1) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, lw / 2));
            c.drawRect(cx - s, cy - s, cx + s, cy + s, paint);
            c.drawRect(cx - s, cy - s, cx + s, cy - s + lw, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            ui.drawGlyphX(c, new RectF(r.left + off, r.top + off, r.right + off, r.bottom + off), Win95.DARK);
        }
    }

    /** Two little cards, the Solitaire window icon. */
    private void drawAppIcon(Canvas c, float x, float y, float size) {
        float w = size * 0.62f, h = size * 0.84f;
        paint.setColor(0xFF000000);
        c.drawRect(x, y, x + w, y + h, paint);
        paint.setColor(0xFF0000A8);
        c.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, paint);
        float x2 = x + size * 0.36f, y2 = y + size * 0.16f;
        paint.setColor(0xFF000000);
        c.drawRect(x2, y2, x2 + w, y2 + h, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawRect(x2 + 1, y2 + 1, x2 + w - 1, y2 + h - 1, paint);
        paint.setColor(0xFFFF0000);
        float cx = x2 + w / 2, cy = y2 + h / 2, r = w * 0.28f;
        android.graphics.Path diamond = new android.graphics.Path();
        diamond.moveTo(cx, cy - r * 1.3f);
        diamond.lineTo(cx + r, cy);
        diamond.lineTo(cx, cy + r * 1.3f);
        diamond.lineTo(cx - r, cy);
        diamond.close();
        c.drawPath(diamond, paint);
    }

    private void drawStatusBar(Canvas c) {
        paint.setColor(Win95.FACE);
        c.drawRect(statusBar, paint);
        paint.setColor(Win95.HILITE);
        c.drawRect(0, statusBar.top, viewW, statusBar.top + ui.px, paint);
        float d = ui.dp;
        float top = statusBar.top + 3 * d, bottom = statusBar.bottom - 3 * d;

        String right = "";
        if (winBmp != null) {
            right = "";
        } else if (game.scoring() == Game.SCORE_STANDARD) {
            right = "Score: " + game.score();
        } else if (game.scoring() == Game.SCORE_VEGAS) {
            right = "Score: $" + game.score();
        }
        String time = game.timed() && winBmp == null ? "Time: " + game.seconds() : "";
        if (game.won() && game.bonus() > 0 && winBmp == null) {
            time = "Bonus: " + game.bonus() + "   " + time;
        }

        float x = viewW - 3 * d;
        if (!time.isEmpty()) {
            float w = Math.max(ui.textWidth(time) + 16 * d, 96 * d);
            r1.set(x - w, top, x, bottom);
            ui.shallow(c, r1);
            ui.label(c, time, r1.left + 8 * d, ui.baseline(r1), Win95.DARK);
            x = r1.left - 3 * d;
        }
        if (!right.isEmpty()) {
            float w = Math.max(ui.textWidth(right) + 16 * d, 110 * d);
            r1.set(x - w, top, x, bottom);
            ui.shallow(c, r1);
            ui.label(c, right, r1.left + 8 * d, ui.baseline(r1), Win95.DARK);
            x = r1.left - 3 * d;
        }
        r1.set(3 * d, top, x, bottom);
        ui.shallow(c, r1);
        String help = "";
        if (winBmp != null) {
            help = "Tap the screen to stop...";
        } else if (openMenu >= 0 && hotItem >= 0 && hotItem < menuItems.size()) {
            help = menuItems.get(hotItem).help;
        }
        if (!help.isEmpty()) {
            c.save();
            c.clipRect(r1);
            ui.label(c, help, r1.left + 6 * d, ui.baseline(r1), Win95.DARK);
            c.restore();
        }
    }

    // ---------------------------------------------------------------- menus

    private static final class MenuItem {
        final String label, accel, help;
        final boolean enabled, separator, checked;
        final Runnable action;

        MenuItem(String label, String accel, String help, boolean enabled, Runnable action) {
            this(label, accel, help, enabled, false, action);
        }

        MenuItem(String label, String accel, String help, boolean enabled, boolean checked, Runnable action) {
            this.label = label;
            this.accel = accel;
            this.help = help;
            this.enabled = enabled;
            this.checked = checked;
            this.action = action;
            this.separator = false;
        }

        private MenuItem() {
            label = accel = help = "";
            enabled = checked = false;
            separator = true;
            action = null;
        }

        static MenuItem separator() {
            return new MenuItem();
        }
    }

    private List<MenuItem> buildMenu(int which) {
        List<MenuItem> items = new ArrayList<>();
        if (which == 0) {
            items.add(new MenuItem("&Deal", "F2", "Deal a new game", true, this::deal));
            items.add(MenuItem.separator());
            items.add(new MenuItem("&Undo", "", "Undo last action", game.canUndo(), this::undo));
            items.add(new MenuItem("De&ck...", "", "Choose new deck back", true, this::showDeckDialog));
            items.add(new MenuItem("&Options...", "", "Change Solitaire options", true, this::showOptionsDialog));
            items.add(MenuItem.separator());
            items.add(new MenuItem("E&xit", "", "Exit Solitaire", true, host::exitApp));
        } else {
            items.add(new MenuItem("&Contents", "F1", "How to play Solitaire", true, this::showHelpDialog));
            items.add(MenuItem.separator());
            items.add(new MenuItem("&About Solitaire", "", "About Solitaire", true, this::showAboutDialog));
        }
        return items;
    }

    private void openMenu(int which) {
        openMenu = which;
        hotItem = -1;
        menuItems = buildMenu(which);
        float d = ui.dp;
        float itemH = 40 * d, sepH = 10 * d;
        float labelW = 0, accelW = 0;
        for (MenuItem m : menuItems) {
            labelW = Math.max(labelW, ui.textWidth(m.label));
            accelW = Math.max(accelW, ui.textWidth(m.accel));
        }
        float w = Math.max(190 * d, labelW + accelW + 76 * d);
        float h = 6 * ui.px;
        for (MenuItem m : menuItems) {
            h += m.separator ? sepH : itemH;
        }
        RectF t = menuTitles[which];
        dropdown.set(t.left, menuBar.bottom - ui.px, t.left + w, menuBar.bottom - ui.px + h);
        itemRects.clear();
        float y = dropdown.top + 3 * ui.px;
        for (MenuItem m : menuItems) {
            float ih = m.separator ? sepH : itemH;
            itemRects.add(new RectF(dropdown.left + 3 * ui.px, y, dropdown.right - 3 * ui.px, y + ih));
            y += ih;
        }
        invalidate();
    }

    private void closeMenu() {
        openMenu = -1;
        hotItem = -1;
        invalidate();
    }

    private void drawMenu(Canvas c) {
        ui.raised(c, dropdown);
        float d = ui.dp;
        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem m = menuItems.get(i);
            RectF r = itemRects.get(i);
            if (m.separator) {
                float y = r.centerY();
                paint.setColor(Win95.SHADOW);
                c.drawRect(r.left + ui.px, y - ui.px, r.right - ui.px, y, paint);
                paint.setColor(Win95.HILITE);
                c.drawRect(r.left + ui.px, y, r.right - ui.px, y + ui.px, paint);
                continue;
            }
            boolean hot = i == hotItem;
            if (hot) {
                paint.setColor(Win95.SELECT);
                c.drawRect(r, paint);
            }
            float base = ui.baseline(r);
            float x = r.left + 24 * d;
            if (!m.enabled) {
                ui.disabledLabel(c, m.label, x, base);
            } else {
                ui.label(c, m.label, x, base, hot ? Win95.HILITE : Win95.DARK);
            }
            if (!m.accel.isEmpty()) {
                float ax = r.right - 16 * d - ui.textWidth(m.accel);
                if (m.enabled) {
                    ui.label(c, m.accel, ax, base, hot ? Win95.HILITE : Win95.DARK);
                } else {
                    ui.disabledLabel(c, m.accel, ax, base);
                }
            }
        }
    }

    private int menuItemAt(float x, float y) {
        for (int i = 0; i < itemRects.size(); i++) {
            if (itemRects.get(i).contains(x, y)) {
                MenuItem m = menuItems.get(i);
                return m.separator || !m.enabled ? -1 : i;
            }
        }
        return -1;
    }

    private int menuTitleAt(float x, float y) {
        for (int i = 0; i < menuTitles.length; i++) {
            if (y >= menuBar.top && y < menuBar.bottom && x >= menuTitles[i].left && x < menuTitles[i].right) {
                return i;
            }
        }
        return -1;
    }

    private void onMenuTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int title = menuTitleAt(x, y);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (title >= 0) {
                    if (title == openMenu) {
                        closeMenu();
                        owner = Owner.NONE;
                    } else {
                        openMenu(title);
                    }
                } else if (openMenu >= 0 && dropdown.contains(x, y)) {
                    hotItem = menuItemAt(x, y);
                } else {
                    closeMenu();
                    owner = Owner.NONE;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (openMenu < 0) {
                    break;
                }
                if (title >= 0 && title != openMenu) {
                    openMenu(title);
                } else if (dropdown.contains(x, y)) {
                    hotItem = menuItemAt(x, y);
                } else {
                    hotItem = -1;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (openMenu >= 0 && dropdown.contains(x, y)) {
                    int item = menuItemAt(x, y);
                    if (item >= 0) {
                        Runnable action = menuItems.get(item).action;
                        closeMenu();
                        action.run();
                    }
                }
                owner = Owner.NONE;
                break;
            case MotionEvent.ACTION_CANCEL:
                owner = Owner.NONE;
                break;
            default:
                break;
        }
        invalidate();
    }

    // ---------------------------------------------------------------- touch

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (winBmp != null) {
                stopWinAnimation();
                owner = Owner.NONE;
                return true;
            }
            if (dialog != null) {
                owner = Owner.DIALOG;
            } else if (openMenu >= 0 || menuTitleAt(x, y) >= 0) {
                owner = Owner.MENU;
            } else if (titleBar.contains(x, y)) {
                owner = Owner.TITLE;
            } else if (y >= playTop && y < playBottom) {
                owner = Owner.GAME;
            } else {
                owner = Owner.NONE;
            }
        }
        switch (owner) {
            case DIALOG:
                if (dialog != null) {
                    dialog.onTouch(e);
                }
                invalidate();
                break;
            case MENU:
                onMenuTouch(e);
                break;
            case TITLE:
                onTitleTouch(e);
                break;
            case GAME:
                onGameTouch(e);
                break;
            default:
                break;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            owner = Owner.NONE;
        }
        return true;
    }

    private void onTitleTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int hit = minBox.contains(x, y) ? 0 : closeBox.contains(x, y) ? 2 : -1;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                titlePressed = hit;
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                int pressed = titlePressed;
                titlePressed = -1;
                if (pressed >= 0 && pressed == hit) {
                    if (hit == 0) {
                        host.minimize();
                    } else {
                        host.exitApp();
                    }
                }
                break;
            default:
                titlePressed = -1;
                break;
        }
        invalidate();
    }

    private void onGameTouch(MotionEvent e) {
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
                if (!dragging && downPile >= 0 && game.canPick(downPile, downIndex)
                        && Math.hypot(x - downX, y - downY) > touchSlop) {
                    startDrag(downPile, downIndex, downX, downY);
                }
                if (dragging) {
                    dragX = x - grabDX;
                    dragY = y - grabDY;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    drop(x, y);
                } else {
                    tap(downPile, downIndex, x, y);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                invalidate();
                break;
            default:
                break;
        }
    }

    /** Returns {pile, index}; pile -1 is bare felt, index -1 an empty pile, -2 a card that can't be picked. */
    private int[] hitTest(float x, float y) {
        PointF p = basePosition(Game.STOCK);
        if (inCard(x, y, p.x, p.y)) {
            return new int[] {Game.STOCK, game.pile(Game.STOCK).size() - 1};
        }
        List<Card> waste = game.pile(Game.WASTE);
        for (int i = waste.size() - 1; i >= Math.max(0, waste.size() - game.wasteFan() - 1); i--) {
            p = positionOf(Game.WASTE, i);
            if (inCard(x, y, p.x, p.y)) {
                return new int[] {Game.WASTE, i == waste.size() - 1 ? i : -2};
            }
        }
        for (int f = Game.FOUNDATION; f < Game.FOUNDATION + 4; f++) {
            p = basePosition(f);
            if (inCard(x, y, p.x, p.y)) {
                return new int[] {f, game.pile(f).size() - 1};
            }
        }
        for (int t = Game.TABLEAU; t < Game.TABLEAU + 7; t++) {
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

    private boolean inCard(float x, float y, float cx, float cy) {
        return x >= cx && x < cx + cw && y >= cy && y < cy + ch;
    }

    private void startDrag(int pile, int index, float fingerX, float fingerY) {
        List<Card> src = game.pile(pile);
        dragging = true;
        dragPile = pile;
        dragIndex = index;
        dragCards.clear();
        dragCards.addAll(src.subList(index, src.size()));
        dragOffsets = new float[dragCards.size()];
        if (Game.isTableau(pile)) {
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
        if (!prefs.outline) {
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
        for (int p = Game.FOUNDATION; p < Game.PILE_COUNT; p++) {
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
            // Be forgiving: dropping anywhere over a column counts as dropping on it.
            for (int t = Game.TABLEAU; t < Game.TABLEAU + 7; t++) {
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
        boolean outline = prefs.outline;
        cancelDrag();
        if (best >= 0) {
            int destStart = game.pile(best).size();
            game.move(dragPile, dragIndex, best);
            if (!outline) {
                for (int i = 0; i < moving.size(); i++) {
                    PointF to = positionOf(best, destStart + i);
                    addFlight(moving.get(i), fromX, fromTop + fromY[i], to.x, to.y, 0, 90);
                }
            }
            afterAction();
        } else if (!outline) {
            for (int i = 0; i < moving.size(); i++) {
                PointF to = positionOf(dragPile, dragIndex + i);
                addFlight(moving.get(i), fromX, fromTop + fromY[i], to.x, to.y, 0, 140);
            }
        }
        invalidate();
    }

    private void tap(int pile, int index, float x, float y) {
        long now = SystemClock.uptimeMillis();
        boolean doubleTap = now - lastTapTime < DOUBLE_TAP_MS && pile == lastTapPile && index == lastTapIndex;
        lastTapTime = doubleTap ? 0 : now;
        lastTapPile = pile;
        lastTapIndex = index;

        if (pile == Game.STOCK) {
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
        if (Game.isTableau(pile) && !card.faceUp) {
            if (index == game.pile(pile).size() - 1 && game.flip(pile)) {
                afterAction();
            }
            return;
        }
        if (!game.canPick(pile, index) || Game.isFoundation(pile)) {
            return;
        }
        if (prefs.tapToMove) {
            autoMove(pile, index);
        } else if (doubleTap && index == game.pile(pile).size() - 1 && !Game.isFoundation(pile)) {
            int f = game.foundationFor(pile);
            if (f >= 0) {
                moveAnimated(pile, index, f);
            }
        }
    }

    private void clickStock() {
        List<Card> stock = game.pile(Game.STOCK);
        int before = game.pile(Game.WASTE).size();
        boolean drawing = !stock.isEmpty();
        PointF from = basePosition(Game.STOCK);
        if (!game.drawOrRecycle()) {
            return;
        }
        if (drawing) {
            List<Card> waste = game.pile(Game.WASTE);
            for (int i = before; i < waste.size(); i++) {
                PointF to = positionOf(Game.WASTE, i);
                addFlight(waste.get(i), from.x, from.y, to.x, to.y, (i - before) * 40L, 110);
            }
        }
        afterAction();
    }

    /** Tap-to-move: foundation first, then a column with cards, then an empty column. */
    private void autoMove(int pile, int index) {
        int size = game.pile(pile).size();
        if (index == size - 1 && !Game.isFoundation(pile)) {
            int f = game.foundationFor(pile);
            if (f >= 0) {
                moveAnimated(pile, index, f);
                return;
            }
        }
        int emptyTarget = -1;
        for (int k = 1; k <= 7; k++) {
            int start = Game.isTableau(pile) ? pile - Game.TABLEAU : -1;
            int t = Game.TABLEAU + (start + k + 7) % 7;
            if (t == pile || !game.canDrop(pile, index, t)) {
                continue;
            }
            if (game.pile(t).isEmpty()) {
                // Moving a king that already heads a column to another empty column is pointless.
                if (emptyTarget < 0 && !(Game.isTableau(pile) && index == 0)) {
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
            addFlight(moving.get(i), start[i].x, start[i].y, end.x, end.y, 0, 130);
        }
        afterAction();
    }

    /** The right-click shortcut: everything that can go up, goes up. */
    private void playAllToFoundations() {
        // Record where every top card is before the sweep so each can fly from its old spot.
        float[][] startPos = new float[52][];
        for (int p = Game.WASTE; p < Game.PILE_COUNT; p++) {
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
            Card card = cardForMove(moves, i);
            PointF end = basePosition(moves.get(i)[1]);
            float[] s = startPos[card.id()];
            addFlight(card, s[0], s[1], end.x, end.y, i * 70L, 140);
        }
        afterAction();
    }

    /** Replays the sweep's foundation sizes to work out which card the {@code i}-th move carried. */
    private Card cardForMove(List<int[]> moves, int i) {
        int to = moves.get(i)[1];
        int later = 0;
        for (int j = i + 1; j < moves.size(); j++) {
            if (moves.get(j)[1] == to) {
                later++;
            }
        }
        List<Card> f = game.pile(to);
        return f.get(f.size() - 1 - later);
    }

    void undo() {
        flights.clear();
        clearHidden();
        cancelDrag();
        if (game.undo()) {
            afterAction();
        }
        invalidate();
    }

    private void afterAction() {
        saveState();
        invalidate();
    }

    // ---------------------------------------------------------------- flights

    private static final class Flight {
        Card card;
        float sx, sy, ex, ey;
        long start, duration;
    }

    private void addFlight(Card card, float sx, float sy, float ex, float ey, long delay, long duration) {
        Flight f = new Flight();
        f.card = card;
        f.sx = sx;
        f.sy = sy;
        f.ex = ex;
        f.ey = ey;
        f.start = SystemClock.uptimeMillis() + delay;
        f.duration = duration;
        flights.add(f);
        hidden[card.id()] = true;
        invalidate();
    }

    /** Draws cards in flight; returns true while any are still moving. */
    private boolean drawFlights(Canvas c) {
        if (flights.isEmpty()) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        List<Flight> done = new ArrayList<>();
        for (Flight f : flights) {
            float t = (now - f.start) / (float) f.duration;
            if (t >= 1f) {
                done.add(f);
                continue;
            }
            t = Math.max(0f, t);
            float e = 1f - (1f - t) * (1f - t);
            float x = f.sx + (f.ex - f.sx) * e, y = f.sy + (f.ey - f.sy) * e;
            art.drawCard(c, f.card, prefs.back, x, y);
        }
        for (Flight f : done) {
            flights.remove(f);
            hidden[f.card.id()] = false;
            art.drawCard(c, f.card, prefs.back, f.ex, f.ey);
        }
        return true;
    }

    private void finishFlights() {
        for (Flight f : flights) {
            hidden[f.card.id()] = false;
        }
        flights.clear();
    }

    private void clearHidden() {
        java.util.Arrays.fill(hidden, false);
    }

    // ---------------------------------------------------------------- win animation

    private static final class Bouncer {
        Card card;
        float x, y, vx, vy;
    }

    private void startWinAnimation() {
        celebrated = true;
        if (viewW <= 0 || viewH <= 0) {
            return;
        }
        winBmp = Bitmap.createBitmap(viewW, viewH, Bitmap.Config.ARGB_8888);
        winCanvas = new Canvas(winBmp);
        drawTable(winCanvas);
        winLaunched = 0;
        bouncer = null;
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
                List<Card> pile = game.pile(Game.FOUNDATION + f);
                PointF p = basePosition(Game.FOUNDATION + f);
                bouncer = new Bouncer();
                bouncer.card = pile.get(pile.size() - 1 - fromTop);
                bouncer.x = p.x;
                bouncer.y = p.y;
                float speed = (2.5f + rng.nextFloat() * 4.5f) * s;
                bouncer.vx = rng.nextBoolean() ? speed : -speed;
                bouncer.vy = -rng.nextFloat() * 9f * s;
                // Uncover the next card down on the foundation, as the original did.
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
            winCanvas.save();
            winCanvas.clipRect(0, playTop, viewW, playBottom);
            art.drawFace(winCanvas, b.card, b.x, b.y);
            winCanvas.restore();
            if (b.x + cw < 0 || b.x > viewW) {
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
        Win95.Dialog d = new Win95.Dialog("Solitaire", 260, 120);
        d.add(new Win95.Text("Deal Again?", false)).at(16, 14, 228, 30);
        d.add(new Win95.Button("&Yes", true, () -> {
            closeDialog();
            deal();
        })).at(24, 70, 96, 36);
        d.add(new Win95.Button("&No", false, this::closeDialog)).at(140, 70, 96, 36);
        d.onCancel = this::closeDialog;
        showDialog(d);
    }

    // ---------------------------------------------------------------- dialogs

    private void showDialog(Win95.Dialog d) {
        cancelDrag();
        closeMenu();
        dialog = d;
        d.layout(ui, viewW, viewH);
        invalidate();
    }

    private void closeDialog() {
        dialog = null;
        invalidate();
    }

    void showOptionsDialog() {
        Win95.Dialog d = new Win95.Dialog("Options", 336, 290);
        d.add(new Win95.Group("Draw")).at(8, 4, 152, 100);
        Win95.Radio drawOne = d.add(new Win95.Radio("Draw &One", 0, !prefs.drawThree));
        drawOne.at(20, 32, 136, 30);
        Win95.Radio drawThree = d.add(new Win95.Radio("Draw &Three", 0, prefs.drawThree));
        drawThree.at(20, 64, 136, 30);
        d.add(new Win95.Group("Scoring")).at(172, 4, 156, 130);
        Win95.Radio standard = d.add(new Win95.Radio("St&andard", 1, prefs.scoring == Game.SCORE_STANDARD));
        standard.at(184, 32, 136, 30);
        Win95.Radio vegas = d.add(new Win95.Radio("&Vegas", 1, prefs.scoring == Game.SCORE_VEGAS));
        vegas.at(184, 64, 136, 30);
        Win95.Radio none = d.add(new Win95.Radio("&None", 1, prefs.scoring == Game.SCORE_NONE));
        none.at(184, 96, 136, 30);
        Win95.Check timed = d.add(new Win95.Check("T&imed game", prefs.timed));
        timed.at(12, 112, 150, 30);
        Win95.Check status = d.add(new Win95.Check("Status &bar", prefs.statusBar));
        status.at(12, 144, 150, 30);
        Win95.Check outline = d.add(new Win95.Check("Out&line dragging", prefs.outline));
        outline.at(12, 176, 160, 30);
        Win95.Check tap = d.add(new Win95.Check("&Tap to move", prefs.tapToMove));
        tap.at(12, 208, 150, 30);
        Win95.Check cumulative = d.add(new Win95.Check("&Cumulative Score", prefs.cumulative));
        cumulative.at(176, 144, 156, 30);
        Runnable sync = () -> cumulative.enabled = vegas.checked;
        sync.run();
        standard.onChange = sync;
        vegas.onChange = sync;
        none.onChange = sync;

        d.add(new Win95.Button("OK", true, () -> {
            boolean redeal = drawThree.checked != prefs.drawThree
                    || (vegas.checked ? Game.SCORE_VEGAS : none.checked ? Game.SCORE_NONE : Game.SCORE_STANDARD)
                    != prefs.scoring;
            prefs.drawThree = drawThree.checked;
            prefs.scoring = vegas.checked ? Game.SCORE_VEGAS : none.checked ? Game.SCORE_NONE : Game.SCORE_STANDARD;
            prefs.timed = timed.checked;
            prefs.statusBar = status.checked;
            prefs.outline = outline.checked;
            prefs.tapToMove = tap.checked;
            prefs.cumulative = cumulative.checked;
            game.setTimed(prefs.timed);
            closeDialog();
            relayout();
            if (redeal) {
                deal();
            } else {
                saveState();
            }
        })).at(60, 246, 100, 36);
        d.add(new Win95.Button("Cancel", false, this::closeDialog)).at(176, 246, 100, 36);
        d.onCancel = this::closeDialog;
        showDialog(d);
    }

    /** The card-back picker, plus the extra buttons for loading cards.dll. */
    void showDeckDialog() {
        int n = art.backCount();
        int cols = 4;
        int rows = (n + cols - 1) / cols;
        float cellW = 78, cellH = 104;
        float gridH = rows * cellH;
        float width = cols * cellW + 16;
        Win95.Dialog d = new Win95.Dialog("Select Card Back", width, gridH + 116);
        DeckGrid grid = d.add(new DeckGrid(n, cols, art.clampBack(prefs.back)));
        grid.at(8, 8, cols * cellW, gridH);
        float y = gridH + 16;
        d.add(new Win95.Button("Load cards.dll...", false, () -> {
            closeDialog();
            host.pickCardsDll();
        })).at(8, y, 168, 36);
        Win95.Button builtIn = d.add(new Win95.Button("Built-in cards", false, () -> {
            closeDialog();
            host.useBuiltInCards();
        }));
        builtIn.at(width - 8 - 140, y, 140, 36);
        builtIn.enabled = art.usingDll();
        Runnable ok = () -> {
            prefs.back = grid.selected;
            saveState();
            closeDialog();
        };
        grid.onDoubleTap = ok;
        float by = y + 48;
        d.add(new Win95.Button("OK", true, ok)).at(width / 2 - 108, by, 100, 36);
        d.add(new Win95.Button("Cancel", false, this::closeDialog)).at(width / 2 + 8, by, 100, 36);
        d.onCancel = this::closeDialog;
        showDialog(d);
    }

    private final class DeckGrid extends Win95.Widget {
        final int count, cols;
        int selected;
        Runnable onDoubleTap;
        private long lastTap;
        private int touched = -1;

        DeckGrid(int count, int cols, int selected) {
            this.count = count;
            this.cols = cols;
            this.selected = selected;
        }

        private RectF cell(int i) {
            float cwid = r.width() / cols;
            float chgt = cwid * 104f / 78f;
            float x = r.left + (i % cols) * cwid, y = r.top + (i / cols) * chgt;
            return new RectF(x, y, x + cwid, y + chgt);
        }

        @Override
        void draw(Canvas c, Win95 u) {
            float d = u.dp;
            for (int i = 0; i < count; i++) {
                RectF cell = cell(i);
                RectF card = new RectF(cell.centerX() - 32 * d, cell.centerY() - 44 * d,
                        cell.centerX() + 32 * d, cell.centerY() + 44 * d);
                if (i == selected) {
                    RectF frame = new RectF(cell.left + 2 * d, cell.top + 2 * d, cell.right - 2 * d, cell.bottom - 2 * d);
                    u.sunken(c, frame, Win95.FACE);
                }
                art.drawBackIn(c, i, card);
            }
        }

        @Override
        boolean tappable() {
            return true;
        }

        @Override
        void tap(Win95.Dialog dlg) {
            if (touched < 0) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            boolean twice = touched == selected && now - lastTap < DOUBLE_TAP_MS;
            selected = touched;
            lastTap = now;
            if (twice && onDoubleTap != null) {
                onDoubleTap.run();
            }
        }

        void touchAt(float x, float y) {
            touched = -1;
            for (int i = 0; i < count; i++) {
                if (cell(i).contains(x, y)) {
                    touched = i;
                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        // The deck grid needs to know which thumbnail was touched before the dialog dispatches the tap.
        if (dialog != null && e.getActionMasked() == MotionEvent.ACTION_UP) {
            for (Win95.Widget w : dialog.widgets) {
                if (w instanceof DeckGrid) {
                    ((DeckGrid) w).touchAt(e.getX(), e.getY());
                }
            }
        }
        return super.dispatchTouchEvent(e);
    }

    void showAboutDialog() {
        Win95.Dialog d = new Win95.Dialog("About Solitaire", 312, 236);
        d.add(new Win95.Text("Solitaire", true)).at(16, 12, 280, 24);
        d.add(new Win95.Text("A remake of the classic Windows Solitaire (Klondike) for Android.\n\n"
                + "Cards are drawn by the app. Load your own cards.dll from Game > Deck to play with "
                + "the original card artwork.", false)).at(16, 42, 280, 140);
        d.add(new Win95.Button("OK", true, this::closeDialog)).at(106, 190, 100, 36);
        d.onCancel = this::closeDialog;
        showDialog(d);
    }

    void showHelpDialog() {
        Win95.Dialog d = new Win95.Dialog("Solitaire Help", 330, 440);
        d.add(new Win95.Text(
                "Build each suit up from Ace to King on the four foundations at the top right.\n\n"
                        + "In the seven columns, stack cards down in alternating colours. Only a King can "
                        + "go in an empty column. Tap a face-down card to turn it over.\n\n"
                        + "Tap the deck to deal cards. When it runs out, tap the empty spot to turn the "
                        + "pile over again.\n\n"
                        + "Drag cards to move them. With Tap to move on, tapping a card sends it to the "
                        + "best spot. Double-tap a card to send it to a foundation, or double-tap the "
                        + "green table to send up everything that can go.\n\n"
                        + "Standard scoring: +10 to a foundation, +5 deck to column, +5 turning a card, "
                        + "-15 off a foundation. Vegas: $52 a game, $5 back for every card you play up.",
                false)).at(14, 10, 302, 370);
        d.add(new Win95.Button("OK", true, this::closeDialog)).at(115, 394, 100, 36);
        d.onCancel = this::closeDialog;
        showDialog(d);
    }

    // ---------------------------------------------------------------- keyboard

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            deal();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_F1) {
            showHelpDialog();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_Z && event.isCtrlPressed()) {
            undo();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return onBack();
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- test hooks

    int cardWidth() {
        return cw;
    }

    boolean winAnimationRunning() {
        return winBmp != null;
    }

    Win95.Dialog currentDialog() {
        return dialog;
    }

    void openMenuForTest(int which) {
        openMenu(which);
        hotItem = 2;
    }

    void stepWinAnimationForTest(int frames) {
        for (int i = 0; i < frames && winBmp != null; i++) {
            stepWinAnimation();
        }
    }
}
