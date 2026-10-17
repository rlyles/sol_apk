package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
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
 * Cruel: the aces start on the foundations and the rest are dealt in twelve piles of four. Build
 * each suit up on its foundation; on the piles, build down in the same suit. When you are stuck,
 * Deal gathers the piles and deals them out again in fours, without shuffling.
 */
@SuppressLint("ViewConstructor")
public final class Cruel extends CardGame {
    static final int PILES = 12, FOUND = 12, TOTAL = 16;

    private final Card[] deck = Deck.ordered();
    final List<List<Card>> piles = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private final Random rng = new Random();
    boolean movedSinceDeal;
    private boolean messages, over;
    private int selected = -1;
    private long lastTap;
    private int lastTapPile = -1;

    private final RectF[] areas = new RectF[TOTAL];
    private final RectF dealButton = new RectF();
    private boolean dealPressed;
    private float pileStep;

    public Cruel(Context context, Host host) {
        super(context, host, "cruel");
        messages = prefs.getBoolean("messages", true);
        for (int i = 0; i < TOTAL; i++) {
            areas[i] = new RectF();
        }
        List<List<Card>> saved = Deck.load(prefs.getString("game", null), deck, TOTAL);
        if (saved != null) {
            piles.addAll(saved);
            movedSinceDeal = prefs.getBoolean("moved", false);
        } else {
            newGame();
        }
    }

    @Override
    public String title() {
        return "Cruel";
    }

    @Override
    public void save() {
        prefs.edit().putString("game", Deck.save(piles)).putBoolean("moved", movedSinceDeal)
                .putBoolean("messages", messages).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Start a new game", true, false, this::newGame));
            items.add(new Item("&Backup", "BkSp", "Take back the last move", !history.isEmpty(), false, this::backup));
            items.add(new Item("&Record...", "", "Show your record", true, false, this::record));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Cruel", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Deck...", "", "Choose the card back", true, false, this::showDeckDialog));
            items.add(new Item("&Messages", "", "Explain moves that are not allowed", true, messages,
                    () -> messages = !messages));
        }));
        addHelpMenu(menus, "Move every card to the four foundations at the top, building each suit up from "
                + "Ace to King.\n\nOnly the top card of each pile can move. It can go onto its foundation, or "
                + "onto another pile whose top card is the same suit and one rank higher.\n\nTap a card to send "
                + "it to its foundation if it can go there; otherwise it is selected, and tapping another pile "
                + "moves it there.\n\nWhen you are stuck, tap Deal: the piles are picked up in order and dealt out "
                + "again in fours, without shuffling. You must move at least one card between deals.",
                "Cruel for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Cards left: " + cardsLeft());
    }

    int cardsLeft() {
        int n = 52;
        for (int f = FOUND; f < TOTAL; f++) {
            n -= piles.get(f).size();
        }
        return n;
    }

    // ---------------------------------------------------------------- game

    void newGame() {
        if (!over && !history.isEmpty()) {
            recordResult(cardsLeft());
        }
        piles.clear();
        for (int i = 0; i < TOTAL; i++) {
            piles.add(new ArrayList<>());
        }
        List<Card> rest = new ArrayList<>();
        for (Card c : Deck.shuffled(rng)) {
            c.faceUp = true;
            if (c.rank == 1) {
                piles.get(FOUND + c.suit).add(c);
            } else {
                rest.add(c);
            }
        }
        for (int i = 0; i < rest.size(); i++) {
            piles.get(i / 4).add(rest.get(i));
        }
        history.clear();
        movedSinceDeal = false;
        over = false;
        selected = -1;
        finishFlights();
        save();
        invalidate();
    }

    private Card top(int p) {
        List<Card> l = piles.get(p);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    int foundationFor(int p) {
        Card c = top(p);
        if (c == null) {
            return -1;
        }
        Card f = top(FOUND + c.suit);
        return f != null && f.rank == c.rank - 1 ? FOUND + c.suit : -1;
    }

    boolean canStack(int from, int to) {
        Card c = top(from), t = top(to);
        return c != null && t != null && from != to && t.suit == c.suit && t.rank == c.rank + 1;
    }

    private void move(int from, int to) {
        history.add((movedSinceDeal ? "1" : "0") + "|" + Deck.save(piles));
        List<Card> src = piles.get(from);
        Card c = src.remove(src.size() - 1);
        float[] start = cardPos(from, src.size());
        piles.get(to).add(c);
        float[] end = cardPos(to, piles.get(to).size() - 1);
        fly(c, start[0], start[1], end[0], end[1], 0, 130);
        movedSinceDeal = true;
        sound(Sfx.CLICK);
        afterMove();
    }

    void redeal() {
        if (!movedSinceDeal) {
            if (messages) {
                messageBox("Cruel", "You haven't moved any cards since the last deal.", null);
            }
            return;
        }
        history.add((movedSinceDeal ? "1" : "0") + "|" + Deck.save(piles));
        List<Card> all = new ArrayList<>();
        for (int p = 0; p < PILES; p++) {
            all.addAll(piles.get(p));
            piles.get(p).clear();
        }
        for (int i = 0; i < all.size(); i++) {
            piles.get(i / 4).add(all.get(i));
        }
        movedSinceDeal = false;
        selected = -1;
        sound(Sfx.BLIP);
        afterMove();
    }

    boolean anyMove() {
        for (int p = 0; p < PILES; p++) {
            if (foundationFor(p) >= 0) {
                return true;
            }
            for (int q = 0; q < PILES; q++) {
                if (canStack(p, q)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void afterMove() {
        save();
        invalidate();
        int left = cardsLeft();
        if (left == 0 && !over) {
            over = true;
            recordResult(0);
            sound(Sfx.WIN);
            postDelayed(() -> confirm("Cruel", "Congratulations!\n\nPlay again?", this::newGame, null), 500);
        } else if (!anyMove() && !movedSinceDeal && !over) {
            over = true;
            recordResult(left);
            sound(Sfx.LOSE);
            postDelayed(() -> confirm("Cruel", left == 1 ? "Game Over - 1 card left.\n\nPlay again?"
                    : "Game Over - " + left + " cards left.\n\nPlay again?", this::newGame, null), 500);
        }
    }

    private void backup() {
        if (history.isEmpty()) {
            return;
        }
        String h = history.remove(history.size() - 1);
        List<List<Card>> prev = Deck.load(h.substring(2), deck, TOTAL);
        if (prev != null) {
            finishFlights();
            piles.clear();
            piles.addAll(prev);
            movedSinceDeal = h.charAt(0) == '1';
            over = false;
            selected = -1;
            save();
            invalidate();
        }
    }

    private void recordResult(int left) {
        int games = prefs.getInt("games", 0) + 1;
        prefs.edit().putInt("games", games).putInt("wins", prefs.getInt("wins", 0) + (left == 0 ? 1 : 0))
                .putInt("total", prefs.getInt("total", 0) + left)
                .putInt("high", Math.max(prefs.getInt("high", 0), left))
                .putInt("low", games == 1 ? left : Math.min(prefs.getInt("low", left), left)).apply();
    }

    private void record() {
        int games = prefs.getInt("games", 0);
        String body = "Games\tWins\tHigh\tLow\tAverage\n" + games + "\t" + prefs.getInt("wins", 0) + "\t"
                + prefs.getInt("high", 0) + "\t" + prefs.getInt("low", 0) + "\t"
                + (games == 0 ? "-" : String.format(java.util.Locale.US, "%.1f", prefs.getInt("total", 0) / (float) games))
                + "\n\n(High, Low and Average count the cards left at the end.)";
        Win95.Dialog d = new Win95.Dialog("Game Records", 320, 170);
        d.add(new Win95.Text(body.replace("\t", "     "), false)).at(14, 10, 296, 110);
        d.add(new Win95.Button("OK", true, () -> closeDialog(d))).at(40, 126, 100, 36);
        d.add(new Win95.Button("&Clear Scores", false, () -> {
            prefs.edit().remove("games").remove("wins").remove("total").remove("high").remove("low").apply();
            closeDialog(d);
        })).at(160, 126, 120, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    // ---------------------------------------------------------------- layout and drawing

    @Override
    protected void layoutClient(RectF r) {
        float colW = r.width() / 4f;
        float byWidth = colW / 1.75f;
        float byHeight = r.height() / 4.6f * 71f / 96f;
        setCardWidth(Math.min(byWidth, byHeight));
        float gapY = (r.height() - 4 * ch) / 5f;
        pileStep = cw * 0.22f;
        float fy = r.top + gapY;
        float fLeft = r.left + (r.width() - 4 * cw - 3 * cw * 0.25f) / 2;
        for (int i = 0; i < 4; i++) {
            float x = fLeft + i * cw * 1.25f;
            areas[FOUND + i].set(x, fy, x + cw, fy + ch);
        }
        dealButton.set(r.left + 8 * dp, fy, r.left + 8 * dp + Math.min(fLeft - r.left - 16 * dp, 90 * dp), fy + 40 * dp);
        if (dealButton.width() < 50 * dp) {
            dealButton.set(r.right - 70 * dp, r.bottom - 44 * dp, r.right - 8 * dp, r.bottom - 6 * dp);
        }
        for (int p = 0; p < PILES; p++) {
            int row = p / 4, col = p % 4;
            float x = r.left + col * colW + (colW - (cw + 3 * pileStep)) / 2;
            float y = fy + (row + 1) * (ch + gapY);
            areas[p].set(x, y, r.left + (col + 1) * colW - 2 * dp, y + ch);
        }
    }

    float[] cardPos(int p, int index) {
        if (p >= FOUND) {
            return new float[] {areas[p].left, areas[p].top};
        }
        int n = Math.max(1, piles.get(p).size());
        float step = n <= 1 ? 0 : Math.min(pileStep, (areas[p].width() - cw) / (n - 1));
        return new float[] {areas[p].left + index * step, areas[p].top};
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(FELT);
        for (int p = 0; p < TOTAL; p++) {
            List<Card> pile = piles.get(p);
            if (pile.isEmpty()) {
                drawEmpty(c, areas[p].left, areas[p].top);
                continue;
            }
            int from = p >= FOUND ? pile.size() - 1 : 0;
            for (int i = from; i < pile.size(); i++) {
                Card card = pile.get(i);
                if (hidden[card.id()]) {
                    if (p >= FOUND && i > 0) {
                        art.drawFace(c, pile.get(i - 1), areas[p].left, areas[p].top);
                    }
                    continue;
                }
                float[] q = cardPos(p, i);
                if (p == selected && i == pile.size() - 1) {
                    art.drawFaceInverted(c, card, q[0], q[1]);
                } else {
                    art.drawFace(c, card, q[0], q[1]);
                }
            }
        }
        ui.button(c, dealButton, "&Deal", dealPressed, movedSinceDeal, false);
        drawFlights(c);
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            dealPressed = dealButton.contains(x, y);
            return;
        }
        if (e.getActionMasked() != MotionEvent.ACTION_UP) {
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                dealPressed = false;
            }
            return;
        }
        finishFlights();
        if (dealPressed) {
            dealPressed = false;
            if (dealButton.contains(x, y)) {
                redeal();
            }
            return;
        }
        int hit = -1;
        for (int p = 0; p < PILES; p++) {
            if (areas[p].contains(x, y)) {
                hit = p;
            }
        }
        if (hit < 0) {
            selected = -1;
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        boolean twice = now - lastTap < 350 && hit == lastTapPile;
        lastTap = now;
        lastTapPile = hit;
        if (selected >= 0 && selected != hit) {
            int from = selected;
            selected = -1;
            if (canStack(from, hit)) {
                move(from, hit);
            } else if (messages) {
                Card c = top(from), t = top(hit);
                sound(Sfx.DROP);
                messageBox("Cruel", t == null ? "That move is not allowed." : t.suit != c.suit
                        ? "Must play card onto same suit." : "Card must be one lower.", null);
            }
            return;
        }
        if (selected == hit && !twice) {
            selected = -1;
            return;
        }
        int f = foundationFor(hit);
        if (f >= 0) {
            selected = -1;
            move(hit, f);
        } else if (top(hit) != null) {
            selected = hit;
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
