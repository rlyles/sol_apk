package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
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
 * Golf: clear the seven columns onto the foundation by playing cards one higher or one lower
 * than its top card. Like a round of golf, the lower your score the better.
 */
@SuppressLint("ViewConstructor")
public final class Golf extends CardGame {
    static final int STOCK = 7, FOUNDATION = 8, PILES = 9;

    private final Card[] deck = Deck.ordered();
    final List<List<Card>> piles = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private final Random rng = new Random();
    private boolean messages, over;

    private final PointF[] base = new PointF[PILES];
    private float fan;

    public Golf(Context context, Host host) {
        super(context, host, "golf");
        messages = prefs.getBoolean("messages", true);
        for (int i = 0; i < PILES; i++) {
            base[i] = new PointF();
        }
        List<List<Card>> saved = Deck.load(prefs.getString("game", null), deck, PILES);
        if (saved != null) {
            piles.addAll(saved);
            over = !anyMove() && piles.get(STOCK).isEmpty();
        } else {
            deal();
        }
    }

    @Override
    public String title() {
        return "Golf";
    }

    @Override
    public void save() {
        prefs.edit().putString("game", Deck.save(piles)).putBoolean("messages", messages).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New", "F2", "Deal a new game", true, false, this::newGame));
            items.add(new Item("&Backup", "BkSp", "Take back the last move", !history.isEmpty(), false, this::backup));
            items.add(new Item("&Record...", "", "Show your scores", true, false, this::record));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Golf", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Deck...", "", "Choose the card back", true, false, this::showDeckDialog));
            items.add(new Item("&Messages", "", "Explain moves that are not allowed", true, messages,
                    () -> messages = !messages));
        }));
        addHelpMenu(menus, "The object is to move all the cards from the seven columns onto the foundation "
                + "at the bottom.\n\nYou can play the bottom card of any column if it is one higher or one lower "
                + "than the top card of the foundation, regardless of suit. Aces are low and kings are high, so "
                + "nothing can be played on a King.\n\nWhen you are stuck, tap the deck to turn up a new "
                + "foundation card.\n\nYour score is the number of cards left in the columns. If you clear "
                + "them all, the cards left in the deck count against par: the lower the better.",
                "Golf for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Deck: " + piles.get(STOCK).size());
        panels.add("Score: " + score());
    }

    int score() {
        int left = 0;
        for (int i = 0; i < 7; i++) {
            left += piles.get(i).size();
        }
        return left > 0 ? left : -piles.get(STOCK).size();
    }

    // ---------------------------------------------------------------- game

    void deal() {
        piles.clear();
        List<Card> cards = Deck.shuffled(rng);
        for (int i = 0; i < PILES; i++) {
            piles.add(new ArrayList<>());
        }
        int n = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 7; col++) {
                Card c = cards.get(n++);
                c.faceUp = true;
                piles.get(col).add(c);
            }
        }
        while (n < 51) {
            Card c = cards.get(n++);
            c.faceUp = false;
            piles.get(STOCK).add(c);
        }
        Card first = cards.get(51);
        first.faceUp = true;
        piles.get(FOUNDATION).add(first);
        history.clear();
        over = false;
        finishFlights();
        save();
        invalidate();
    }

    private void newGame() {
        if (!over && history.size() > 0) {
            recordScore(score());
        }
        deal();
    }

    private Card top(int pile) {
        List<Card> p = piles.get(pile);
        return p.isEmpty() ? null : p.get(p.size() - 1);
    }

    boolean playable(int col) {
        Card c = top(col), f = top(FOUNDATION);
        return c != null && f != null && f.rank != 13 && Math.abs(c.rank - f.rank) == 1;
    }

    boolean anyMove() {
        for (int i = 0; i < 7; i++) {
            if (playable(i)) {
                return true;
            }
        }
        return false;
    }

    private void play(int col) {
        if (!playable(col)) {
            if (messages && top(col) != null) {
                sound(Sfx.DROP);
                messageBox("Golf", top(FOUNDATION).rank == 13 ? "Nothing can be played on a King."
                        : "That card is not one higher or one lower than the foundation card.", null);
            }
            return;
        }
        history.add(Deck.save(piles));
        Card c = top(col);
        PointF from = positionOf(col, piles.get(col).size() - 1);
        piles.get(col).remove(piles.get(col).size() - 1);
        piles.get(FOUNDATION).add(c);
        fly(c, from.x, from.y, base[FOUNDATION].x, base[FOUNDATION].y, 0, 130);
        sound(Sfx.CLICK);
        afterMove();
    }

    private void turnStock() {
        List<Card> stock = piles.get(STOCK);
        if (stock.isEmpty()) {
            return;
        }
        history.add(Deck.save(piles));
        Card c = stock.remove(stock.size() - 1);
        c.faceUp = true;
        piles.get(FOUNDATION).add(c);
        fly(c, base[STOCK].x, base[STOCK].y, base[FOUNDATION].x, base[FOUNDATION].y, 0, 130);
        afterMove();
    }

    private void afterMove() {
        save();
        invalidate();
        boolean cleared = score() <= 0;
        if (cleared || (!anyMove() && piles.get(STOCK).isEmpty())) {
            over = true;
            int s = score();
            recordScore(s);
            sound(cleared ? Sfx.WIN : Sfx.LOSE);
            postDelayed(() -> confirm("Golf", (cleared ? "Congratulations! You cleared the course.\n\n"
                    : "No more moves.\n\n") + "Your score is " + s + ".\n\nDeal again?", this::deal, null), 400);
        }
    }

    private void backup() {
        if (history.isEmpty()) {
            return;
        }
        List<List<Card>> prev = Deck.load(history.remove(history.size() - 1), deck, PILES);
        if (prev != null) {
            finishFlights();
            piles.clear();
            piles.addAll(prev);
            over = false;
            save();
            invalidate();
        }
    }

    private void recordScore(int s) {
        int games = prefs.getInt("games", 0) + 1;
        prefs.edit().putInt("games", games).putInt("total", prefs.getInt("total", 0) + s)
                .putInt("best", games == 1 ? s : Math.min(prefs.getInt("best", s), s)).apply();
    }

    private void record() {
        int games = prefs.getInt("games", 0);
        String body = games == 0 ? "You have not finished any games yet."
                : "Games played: " + games + "\nAverage score: "
                + String.format(java.util.Locale.US, "%.1f", prefs.getInt("total", 0) / (float) games)
                + "\nBest score: " + prefs.getInt("best", 0);
        Win95.Dialog d = new Win95.Dialog("Golf Record", 260, 150);
        d.add(new Win95.Text(body, false)).at(14, 10, 236, 90);
        d.add(new Win95.Button("OK", true, () -> closeDialog(d))).at(20, 106, 100, 36);
        d.add(new Win95.Button("&Clear", false, () -> {
            prefs.edit().remove("games").remove("total").remove("best").apply();
            closeDialog(d);
        })).at(140, 106, 100, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    // ---------------------------------------------------------------- layout and drawing

    @Override
    protected void layoutClient(RectF r) {
        float gapK = 0.18f;
        float byWidth = r.width() / (7 + 8 * gapK);
        float byHeight = r.height() / ((2f + 4 * 0.24f) * 96f / 71f + 4 * gapK);
        setCardWidth(Math.min(byWidth, byHeight));
        float gap = cw * gapK;
        fan = ch * 0.24f;
        float left = r.left + (r.width() - (7 * cw + 6 * gap)) / 2;
        float top = r.top + gap;
        for (int i = 0; i < 7; i++) {
            base[i].set(Math.round(left + i * (cw + gap)), top);
        }
        float y = Math.max(top + 4 * fan + ch + gap * 2, r.bottom - ch - gap);
        base[STOCK].set(left + 2 * (cw + gap), y);
        base[FOUNDATION].set(left + 4 * (cw + gap), y);
    }

    PointF positionOf(int pile, int index) {
        return pile < 7 ? new PointF(base[pile].x, base[pile].y + index * fan) : new PointF(base[pile].x, base[pile].y);
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(FELT);
        for (int col = 0; col < 7; col++) {
            List<Card> p = piles.get(col);
            if (p.isEmpty()) {
                drawEmpty(c, base[col].x, base[col].y);
            }
            for (int i = 0; i < p.size(); i++) {
                if (!hidden[p.get(i).id()]) {
                    PointF pos = positionOf(col, i);
                    art.drawFace(c, p.get(i), pos.x, pos.y);
                }
            }
        }
        List<Card> stock = piles.get(STOCK);
        if (stock.isEmpty()) {
            drawEmpty(c, base[STOCK].x, base[STOCK].y);
        } else {
            int layers = Math.min(3, 1 + stock.size() / 6);
            for (int i = 0; i < layers; i++) {
                art.drawBack(c, back(), base[STOCK].x + i * 2, base[STOCK].y - i * 2);
            }
        }
        List<Card> f = piles.get(FOUNDATION);
        boolean drawn = false;
        for (int i = f.size() - 1; i >= 0 && !drawn; i--) {
            if (!hidden[f.get(i).id()]) {
                art.drawFace(c, f.get(i), base[FOUNDATION].x, base[FOUNDATION].y);
                drawn = true;
            }
        }
        if (!drawn) {
            drawEmpty(c, base[FOUNDATION].x, base[FOUNDATION].y);
        }
        drawFlights(c);
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) {
            return;
        }
        finishFlights();
        float x = e.getX(), y = e.getY();
        if (inCard(x, y, base[STOCK].x, base[STOCK].y)) {
            turnStock();
            return;
        }
        for (int col = 0; col < 7; col++) {
            List<Card> p = piles.get(col);
            if (p.isEmpty()) {
                continue;
            }
            PointF pos = positionOf(col, p.size() - 1);
            if (x >= pos.x && x < pos.x + cw && y >= base[col].y && y < pos.y + ch) {
                play(col);
                return;
            }
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
