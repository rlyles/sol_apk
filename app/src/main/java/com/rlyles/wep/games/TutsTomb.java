package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.cards.Card;
import com.rlyles.wep.cards.CardGame;
import com.rlyles.wep.cards.Deck;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tut's Tomb: the pyramid game. Remove pairs of cards that add up to 13 (Jack 11, Queen 12; a King
 * goes alone) until the tomb is empty.
 */
@SuppressLint("ViewConstructor")
public final class TutsTomb extends CardGame {
    static final int STANDARD = 0, CASINO = 1, NONE = 2;
    static final int WASTE = 100;

    private final Card[] deck = Deck.ordered();
    final Card[] pyramid = new Card[28];
    final List<Card> stock = new ArrayList<>(), waste = new ArrayList<>();
    private final Random rng = new Random();
    private int scoring, drawCount, passes, seconds, score, session, wasteFan;
    private boolean timed, beep, paused, over, started;
    private int selected = -1;

    private float left, top, rowStep;
    private final RectF stockRect = new RectF(), wasteRect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TutsTomb(Context context, Host host) {
        super(context, host, "tutstomb");
        scoring = prefs.getInt("scoring", STANDARD);
        drawCount = prefs.getInt("draw", 1);
        timed = prefs.getBoolean("timed", true);
        beep = prefs.getBoolean("beep", true);
        session = prefs.getInt("session", 0);
        deal();
    }

    @Override
    public String title() {
        return "Tut's Tomb";
    }

    @Override
    public void save() {
        prefs.edit().putInt("scoring", scoring).putInt("draw", drawCount).putBoolean("timed", timed)
                .putBoolean("beep", beep).putInt("session", session).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&Deal", "F2", "Deal a new game", true, false, this::deal));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the game", true, false, this::togglePause));
            items.add(new Item("&New session", "", "Start the casino bank again", true, false, () -> {
                session = 0;
                deal();
            }));
            items.add(Item.sep());
            items.add(new Item("D&eck...", "", "Choose the card back", true, false, this::showDeckDialog));
            items.add(new Item("&Options...", "", "Scoring, drawing and timing", true, false, this::options));
            items.add(new Item("&High scores...", "", "Best scores", true, false, this::highScores));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Tut's Tomb", true, false, () -> host.closeApp(this)));
        }));
        addHelpMenu(menus, "Empty the tomb! Remove cards from the pyramid in pairs that add up to 13: Ace and "
                + "Queen, 2 and Jack, 3 and 10, and so on. A King adds up to 13 on its own, so it goes with one tap.\n\n"
                + "Only cards that nothing covers can be used, plus the top card of the discard pile. Tap one card, "
                + "then its partner.\n\nTap the deck to turn up cards (one or three at a time). You can go through "
                + "the deck three times.\n\nStandard scoring: 5 points for every card removed and 50 for clearing "
                + "the tomb; a timed game loses 2 points every 10 seconds. Casino: each deal costs $52 and every "
                + "card removed pays $5.",
                "Tut's Tomb for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        if (scoring == STANDARD) {
            panels.add("Score: " + score);
        } else if (scoring == CASINO) {
            panels.add("Game: $" + score);
            panels.add("Session: $" + session);
        }
        panels.add("Passes left: " + Math.max(0, 3 - passes));
        if (timed) {
            panels.add("Time: " + seconds);
        }
    }

    @Override
    protected String statusText() {
        return paused ? "Paused" : "";
    }

    @Override
    protected void tick() {
        if (timed && started && !over && !paused) {
            seconds++;
            if (scoring == STANDARD && seconds % 10 == 0) {
                score = Math.max(0, score - 2);
            }
            invalidate();
        }
    }

    // ---------------------------------------------------------------- game

    void deal() {
        List<Card> cards = Deck.shuffled(rng);
        for (int i = 0; i < 28; i++) {
            pyramid[i] = cards.get(i);
        }
        stock.clear();
        waste.clear();
        for (int i = 28; i < 52; i++) {
            Card c = cards.get(i);
            c.faceUp = false;
            stock.add(c);
        }
        passes = 0;
        seconds = 0;
        started = false;
        over = false;
        paused = false;
        selected = -1;
        wasteFan = 0;
        score = 0;
        if (scoring == CASINO) {
            score = -52;
            session -= 52;
        }
        finishFlights();
        save();
        invalidate();
    }

    static int row(int i) {
        int r = 0;
        while ((r + 1) * (r + 2) / 2 <= i) {
            r++;
        }
        return r;
    }

    boolean covered(int i) {
        int r = row(i), k = i - r * (r + 1) / 2;
        if (r == 6) {
            return false;
        }
        int below = (r + 1) * (r + 2) / 2 + k;
        return pyramid[below] != null || pyramid[below + 1] != null;
    }

    private Card cardAt(int sel) {
        if (sel == WASTE) {
            return waste.isEmpty() ? null : waste.get(waste.size() - 1);
        }
        return pyramid[sel];
    }

    private void remove(int sel) {
        Card c = cardAt(sel);
        float[] from = sel == WASTE ? new float[] {wasteRect.left + (wasteFan - 1) * cw * 0.2f, wasteRect.top} : pos(sel);
        if (sel == WASTE) {
            waste.remove(waste.size() - 1);
            wasteFan = Math.max(waste.isEmpty() ? 0 : 1, wasteFan - 1);
        } else {
            pyramid[sel] = null;
        }
        fly(c, from[0], from[1], getWidth() + cw, from[1], 0, 220);
        int pts = scoring == NONE ? 0 : 5;
        score += pts;
        if (scoring == CASINO) {
            session += pts;
        }
    }

    private void select(int sel) {
        started = true;
        Card c = cardAt(sel);
        if (c == null) {
            return;
        }
        if (c.rank == 13) {
            remove(sel);
            selected = -1;
            sound(Sfx.CLICK);
            afterMove();
            return;
        }
        if (selected < 0) {
            selected = sel;
            return;
        }
        if (selected == sel) {
            selected = -1;
            return;
        }
        Card other = cardAt(selected);
        if (other != null && other.rank + c.rank == 13) {
            remove(sel);
            remove(selected);
            selected = -1;
            sound(Sfx.CLICK);
            afterMove();
        } else {
            if (beep) {
                sound(Sfx.DROP);
            }
            selected = sel;
        }
    }

    private void turnStock() {
        started = true;
        selected = -1;
        if (stock.isEmpty()) {
            if (passes < 2 || scoring == NONE) {
                passes++;
                for (int i = waste.size() - 1; i >= 0; i--) {
                    Card c = waste.get(i);
                    c.faceUp = false;
                    stock.add(c);
                }
                waste.clear();
                wasteFan = 0;
            }
            afterMove();
            return;
        }
        int n = Math.min(drawCount, stock.size());
        for (int i = 0; i < n; i++) {
            Card c = stock.remove(stock.size() - 1);
            c.faceUp = true;
            waste.add(c);
        }
        wasteFan = n;
        afterMove();
    }

    private void afterMove() {
        boolean cleared = true;
        for (Card c : pyramid) {
            if (c != null) {
                cleared = false;
                break;
            }
        }
        if (cleared && !over) {
            over = true;
            int bonus = scoring == NONE ? 0 : 50;
            score += bonus;
            if (scoring == CASINO) {
                session += bonus;
            }
            recordHigh();
            sound(Sfx.WIN);
            postDelayed(() -> confirm("Tut's Tomb", "You have emptied the tomb!\n\nScore: " + score + "\n\nDeal again?",
                    this::deal, null), 500);
        } else if (!over && stock.isEmpty() && passes >= 2 && scoring != NONE && !anyPair()) {
            over = true;
            recordHigh();
            sound(Sfx.LOSE);
            postDelayed(() -> confirm("Tut's Tomb", "No more moves.\n\nScore: " + score + "\n\nDeal again?",
                    this::deal, null), 500);
        }
        save();
        invalidate();
    }

    private boolean anyPair() {
        List<Card> open = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            if (pyramid[i] != null && !covered(i)) {
                open.add(pyramid[i]);
            }
        }
        if (!waste.isEmpty()) {
            open.add(waste.get(waste.size() - 1));
        }
        for (int a = 0; a < open.size(); a++) {
            if (open.get(a).rank == 13) {
                return true;
            }
            for (int b = a + 1; b < open.size(); b++) {
                if (open.get(a).rank + open.get(b).rank == 13) {
                    return true;
                }
            }
        }
        return false;
    }

    private void togglePause() {
        paused = !paused;
        invalidate();
    }

    private void recordHigh() {
        if (scoring != STANDARD) {
            return;
        }
        String s = prefs.getString("high", "");
        List<Integer> list = new ArrayList<>();
        for (String v : s.split(",")) {
            if (!v.isEmpty()) {
                list.add(Integer.parseInt(v));
            }
        }
        list.add(score);
        java.util.Collections.sort(list, java.util.Collections.reverseOrder());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            sb.append(i > 0 ? "," : "").append(list.get(i));
        }
        prefs.edit().putString("high", sb.toString()).apply();
    }

    private void highScores() {
        String s = prefs.getString("high", "");
        StringBuilder body = new StringBuilder("Standard scoring\n\n");
        String[] v = s.isEmpty() ? new String[0] : s.split(",");
        for (int i = 0; i < 5; i++) {
            body.append(i + 1).append(".   ").append(i < v.length ? v[i] : "--").append('\n');
        }
        messageBox("High Scores", body.toString().trim(), null);
    }

    private void options() {
        Win95.Dialog d = new Win95.Dialog("Options", 300, 250);
        d.add(new Win95.Group("Scoring")).at(8, 4, 138, 130);
        d.add(new Win95.Radio("&Standard", 0, scoring == STANDARD)).at(18, 30, 124, 30);
        d.add(new Win95.Radio("&Casino", 0, scoring == CASINO)).at(18, 62, 124, 30);
        d.add(new Win95.Radio("&None", 0, scoring == NONE)).at(18, 94, 124, 30);
        d.add(new Win95.Group("Draw")).at(154, 4, 138, 98);
        d.add(new Win95.Radio("Draw &One", 1, drawCount == 1)).at(164, 30, 124, 30);
        d.add(new Win95.Radio("Draw &Three", 1, drawCount == 3)).at(164, 62, 124, 30);
        Win95.Check timedBox = d.add(new Win95.Check("Timed &Game", timed));
        timedBox.at(12, 142, 150, 30);
        Win95.Check beepBox = d.add(new Win95.Check("&Beep", beep));
        beepBox.at(12, 172, 150, 30);
        d.add(new Win95.Button("OK", true, () -> {
            int newScoring = Win95.Radio.selected(d, 0);
            int newDraw = Win95.Radio.selected(d, 1) == 0 ? 1 : 3;
            boolean redeal = newScoring != scoring || newDraw != drawCount;
            scoring = newScoring;
            drawCount = newDraw;
            timed = timedBox.checked;
            beep = beepBox.checked;
            closeDialog(d);
            if (redeal) {
                deal();
            }
            save();
        })).at(44, 208, 100, 36);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(156, 208, 100, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    // ---------------------------------------------------------------- layout and drawing

    @Override
    protected void layoutClient(RectF r) {
        float byWidth = r.width() / 7.8f;
        float byHeight = r.height() / (6 * 0.5f * 96f / 71f + 2 * 96f / 71f + 0.7f);
        setCardWidth(Math.min(byWidth, byHeight));
        rowStep = ch * 0.5f;
        left = r.left + (r.width() - 7 * cw) / 2;
        top = r.top + cw * 0.25f;
        float y = Math.max(top + 6 * rowStep + ch + cw * 0.3f, r.bottom - ch - cw * 0.25f);
        stockRect.set(r.centerX() - cw * 1.6f, y, r.centerX() - cw * 0.6f, y + ch);
        wasteRect.set(r.centerX() + cw * 0.1f, y, r.centerX() + cw * 1.1f, y + ch);
    }

    float[] pos(int i) {
        int r = row(i), k = i - r * (r + 1) / 2;
        return new float[] {left + (6 - r) * cw / 2f + k * cw, top + r * rowStep};
    }

    @Override
    protected void drawClient(Canvas c) {
        // A sandy tomb wall behind the pyramid.
        paint.setShader(new LinearGradient(0, client.top, 0, client.bottom, 0xFF0A4A6A, 0xFF5A3A10, Shader.TileMode.CLAMP));
        c.drawRect(client, paint);
        paint.setShader(null);
        Path p = new Path();
        p.moveTo(client.centerX(), top - cw * 0.15f);
        p.lineTo(left + 7 * cw + cw * 0.3f, top + 6 * rowStep + ch + cw * 0.1f);
        p.lineTo(left - cw * 0.3f, top + 6 * rowStep + ch + cw * 0.1f);
        p.close();
        paint.setColor(0xFFC8A050);
        c.drawPath(p, paint);
        if (paused) {
            ui.withFont(22, true, () -> ui.centered(c, "Paused", client, 0xFFFFFFFF));
            return;
        }
        for (int i = 0; i < 28; i++) {
            if (pyramid[i] == null || hidden[pyramid[i].id()]) {
                continue;
            }
            float[] q = pos(i);
            if (i == selected) {
                art.drawFaceInverted(c, pyramid[i], q[0], q[1]);
            } else {
                art.drawFace(c, pyramid[i], q[0], q[1]);
            }
        }
        if (stock.isEmpty()) {
            drawEmpty(c, stockRect.left, stockRect.top);
            if (passes < 2 || scoring == NONE) {
                art.drawStockMarker(c, stockRect, true);
            } else {
                art.drawStockMarker(c, stockRect, false);
            }
        } else {
            art.drawBack(c, back(), stockRect.left, stockRect.top);
        }
        int n = waste.size();
        int fan = Math.min(wasteFan, n);
        int first = n - fan;
        for (int i = Math.max(0, first - 1); i < n; i++) {
            Card card = waste.get(i);
            if (hidden[card.id()]) {
                continue;
            }
            float x = wasteRect.left + Math.max(0, i - first) * cw * 0.2f;
            if (i == n - 1 && selected == WASTE) {
                art.drawFaceInverted(c, card, x, wasteRect.top);
            } else {
                art.drawFace(c, card, x, wasteRect.top);
            }
        }
        drawFlights(c);
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP || over) {
            return;
        }
        if (paused) {
            togglePause();
            return;
        }
        finishFlights();
        float x = e.getX(), y = e.getY();
        if (stockRect.contains(x, y)) {
            turnStock();
            return;
        }
        if (!waste.isEmpty()) {
            float wx = wasteRect.left + Math.max(0, Math.min(wasteFan, waste.size()) - 1) * cw * 0.2f;
            if (inCard(x, y, wx, wasteRect.top)) {
                select(WASTE);
                invalidate();
                return;
            }
        }
        for (int i = 27; i >= 0; i--) {
            if (pyramid[i] == null) {
                continue;
            }
            float[] q = pos(i);
            if (inCard(x, y, q[0], q[1])) {
                if (!covered(i)) {
                    select(i);
                }
                invalidate();
                return;
            }
        }
        selected = -1;
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            deal();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_F3) {
            togglePause();
            return true;
        }
        return false;
    }

    @Override
    protected void onHidden() {
        paused = true;
    }
}
