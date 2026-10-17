package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.cards.Card;
import com.rlyles.wep.cards.CardGame;
import com.rlyles.wep.cards.CardsDll;
import com.rlyles.wep.cards.Deck;
import com.rlyles.wep.media.Media;
import com.rlyles.wep.win.Sfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TriPeaks: clear three peaks of cards by playing ones a rank above or below the discard pile,
 * wrapping from King to Ace. Long runs pay more, and clearing a peak pays a bonus.
 */
@SuppressLint("ViewConstructor")
public final class TriPeaks extends CardGame {
    /** Peak positions in half-card units and their rows (0 = top). */
    static final int[] X = {3, 9, 15, 2, 4, 8, 10, 14, 16, 1, 3, 5, 7, 9, 11, 13, 15, 17,
            0, 2, 4, 6, 8, 10, 12, 14, 16, 18};
    static final int[] ROW = {0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3};

    private final Card[] deck = Deck.ordered();
    final Card[] board = new Card[28];
    final List<Card> stock = new ArrayList<>(), waste = new ArrayList<>();
    private final Random rng = new Random();
    int gameScore, session, run, bestRun;
    private int peaksCleared;
    private boolean showStats, over;
    private String player;

    private float left, top, rowStep;
    private final RectF stockRect = new RectF(), wasteRect = new RectF();

    public TriPeaks(Context context, Host host) {
        super(context, host, "tripeaks");
        showStats = prefs.getBoolean("stats", true);
        player = prefs.getString("player", "Player");
        session = prefs.getInt("session", 0);
        if (!load()) {
            deal();
        }
    }

    /** TriPeaks has its own, smaller deck in TRIPEAKS.EXE: faces 1-52 and backs 60-67. */
    @Override
    protected CardsDll deckArt() {
        CardsDll own = Media.get().cached("tripeaks.deck", m -> {
            if (!m.hasArt("tripeaks.exe")) {
                return null;
            }
            Bitmap[] faces = new Bitmap[52];
            for (int i = 0; i < 52; i++) {
                faces[i] = m.bitmap("tripeaks.exe", Integer.toString(i + 1));
                if (faces[i] == null) {
                    return null;
                }
            }
            List<Bitmap> backs = new ArrayList<>();
            for (int id = 60; id <= 67; id++) {
                Bitmap b = m.bitmap("tripeaks.exe", Integer.toString(id));
                if (b != null) {
                    backs.add(b);
                }
            }
            return backs.isEmpty() ? null : CardsDll.fromBitmaps(faces, backs);
        });
        return own != null ? own : super.deckArt();
    }

    @Override
    public String title() {
        return "TriPeaks";
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void save() {
        List<List<Card>> piles = new ArrayList<>();
        List<Card> b = new ArrayList<>();
        StringBuilder mask = new StringBuilder();
        for (Card c : board) {
            mask.append(c == null ? '0' : '1');
            if (c != null) {
                b.add(c);
            }
        }
        piles.add(b);
        piles.add(stock);
        piles.add(waste);
        prefs.edit().putString("game", Deck.save(piles)).putString("mask", mask.toString())
                .putInt("gameScore", gameScore).putInt("run", run).putInt("peaks", peaksCleared)
                .putInt("session", session).putBoolean("stats", showStats).putString("player", player).apply();
    }

    private boolean load() {
        String mask = prefs.getString("mask", null);
        List<List<Card>> piles = Deck.load(prefs.getString("game", null), deck, 3);
        if (piles == null || mask == null || mask.length() != 28) {
            return false;
        }
        int k = 0;
        for (int i = 0; i < 28; i++) {
            board[i] = mask.charAt(i) == '1' ? piles.get(0).get(k++) : null;
        }
        stock.addAll(piles.get(1));
        waste.addAll(piles.get(2));
        gameScore = prefs.getInt("gameScore", 0);
        run = prefs.getInt("run", 0);
        peaksCleared = prefs.getInt("peaks", 0);
        return true;
    }

    // ---------------------------------------------------------------- menus

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&Deal", "F2", "Deal a new game ($52)", true, false, this::deal));
            items.add(new Item("&Change Player...", "", "Change who is playing", true, false, this::changePlayer));
            items.add(new Item("&Reset", "", "Start a new session at $0", true, false, () -> {
                session = 0;
                deal();
            }));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit TriPeaks", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Deck...", "", "Choose the card back", true, false, this::showDeckDialog));
            items.add(new Item("&Show Statistics", "", "Show game and session totals", true, showStats, () -> {
                showStats = !showStats;
                relayout();
            }));
            items.add(new Item("&Hall of Fame...", "", "Best sessions", true, false, this::hallOfFame));
        }));
        addHelpMenu(menus, "Clear the three peaks. Tap any face-up card that is one higher or one lower than "
                + "the card on the discard pile, regardless of suit; Ace and King are next to each other. "
                + "Cards turn over when nothing covers them.\n\nWhen you are stuck, tap the deck to turn up a new "
                + "discard card.\n\nScoring: every deal costs $52. Each card you play earns as many dollars as its "
                + "place in your current run (the first card $1, the next $2, and so on); turning a card from the "
                + "deck ends the run. Clearing a peak earns $15, and clearing all three earns $30 more.",
                "TriPeaks for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return showStats;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Run: " + run);
        panels.add("Game: " + money(gameScore));
        panels.add("Session: " + money(session));
    }

    @Override
    protected String statusText() {
        return player;
    }

    private static String money(int v) {
        return (v < 0 ? "-$" : "$") + Math.abs(v);
    }

    private void changePlayer() {
        prompt("Change Player", "Type your name:", player, false, 20, name -> {
            if (!name.isEmpty()) {
                player = name;
                session = 0;
                deal();
            }
        });
    }

    private void hallOfFame() {
        String body = "Best session:  " + money(prefs.getInt("bestSession", 0)) + "  "
                + prefs.getString("bestSessionName", "") + "\n"
                + "Best game:  " + money(prefs.getInt("bestGame", 0)) + "  " + prefs.getString("bestGameName", "") + "\n"
                + "Longest run:  " + prefs.getInt("bestRun", 0) + " cards";
        messageBox("Hall of Fame", body, null);
    }

    // ---------------------------------------------------------------- game

    void deal() {
        List<Card> cards = Deck.shuffled(rng);
        for (int i = 0; i < 28; i++) {
            board[i] = cards.get(i);
            board[i].faceUp = ROW[i] == 3;
        }
        stock.clear();
        waste.clear();
        for (int i = 28; i < 51; i++) {
            cards.get(i).faceUp = false;
            stock.add(cards.get(i));
        }
        waste.add(cards.get(51));
        cards.get(51).faceUp = true;
        gameScore = -52;
        session -= 52;
        run = 0;
        peaksCleared = 0;
        over = false;
        finishFlights();
        save();
        invalidate();
    }

    boolean covered(int i) {
        for (int j = 0; j < 28; j++) {
            if (board[j] != null && ROW[j] == ROW[i] + 1 && Math.abs(X[j] - X[i]) == 1) {
                return true;
            }
        }
        return false;
    }

    boolean playable(int i) {
        if (board[i] == null || covered(i) || waste.isEmpty()) {
            return false;
        }
        int a = board[i].rank, b = waste.get(waste.size() - 1).rank;
        int diff = Math.abs(a - b);
        return diff == 1 || diff == 12;
    }

    private void play(int i) {
        Card c = board[i];
        float[] p = cardPos(i);
        board[i] = null;
        waste.add(c);
        run++;
        bestRun = Math.max(bestRun, run);
        gameScore += run;
        session += run;
        fly(c, p[0], p[1], wasteRect.left, wasteRect.top, 0, 140);
        sound(Sfx.CLICK);
        for (int j = 0; j < 28; j++) {
            if (board[j] != null && !board[j].faceUp && !covered(j)) {
                board[j].faceUp = true;
            }
        }
        int peaks = 0;
        for (int peak = 0; peak < 3; peak++) {
            if (board[peak] == null) {
                peaks++;
            }
        }
        if (peaks > peaksCleared) {
            int bonus = peaks == 3 ? 30 : 15;
            gameScore += bonus;
            session += bonus;
            peaksCleared = peaks;
            sound(Sfx.CHIME);
        }
        checkEnd();
    }

    private void turnStock() {
        if (stock.isEmpty()) {
            return;
        }
        Card c = stock.remove(stock.size() - 1);
        c.faceUp = true;
        waste.add(c);
        run = 0;
        fly(c, stockRect.left, stockRect.top, wasteRect.left, wasteRect.top, 0, 140);
        checkEnd();
    }

    private void checkEnd() {
        save();
        boolean cleared = peaksCleared == 3;
        boolean stuck = stock.isEmpty();
        for (int i = 0; i < 28 && stuck; i++) {
            if (playable(i)) {
                stuck = false;
            }
        }
        if ((cleared || stuck) && !over) {
            over = true;
            SharedEdit.best(prefs, "bestGame", "bestGameName", gameScore, player);
            SharedEdit.best(prefs, "bestSession", "bestSessionName", session, player);
            prefs.edit().putInt("bestRun", Math.max(prefs.getInt("bestRun", 0), bestRun)).apply();
            sound(cleared ? Sfx.WIN : Sfx.LOSE);
            postDelayed(() -> confirm("TriPeaks", (cleared ? "You cleared all three peaks!\n\n" : "No more moves.\n\n")
                    + "This game: " + money(gameScore) + "\nSession: " + money(session) + "\n\nDeal again?",
                    this::deal, null), 400);
        }
    }

    // ---------------------------------------------------------------- layout and drawing

    @Override
    protected void layoutClient(RectF r) {
        float byWidth = r.width() / 10.6f;
        float byHeight = r.height() / (3 * 0.5f * 96f / 71f + 2 * 96f / 71f + 0.6f);
        setCardWidth(Math.min(byWidth, byHeight));
        rowStep = ch * 0.5f;
        left = r.left + (r.width() - 10 * cw) / 2;
        top = r.top + cw * 0.2f;
        float y = Math.max(top + 3 * rowStep + ch + cw * 0.3f, r.bottom - ch - cw * 0.25f);
        stockRect.set(r.centerX() - cw * 1.3f, y, r.centerX() - cw * 0.3f, y + ch);
        wasteRect.set(r.centerX() + cw * 0.3f, y, r.centerX() + cw * 1.3f, y + ch);
    }

    float[] cardPos(int i) {
        return new float[] {left + X[i] * cw / 2f, top + ROW[i] * rowStep};
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(FELT);
        for (int i = 0; i < 28; i++) {
            if (board[i] != null && !hidden[board[i].id()]) {
                float[] p = cardPos(i);
                drawCard(c, board[i], p[0], p[1]);
            }
        }
        if (stock.isEmpty()) {
            drawEmpty(c, stockRect.left, stockRect.top);
        } else {
            for (int i = 0; i < Math.min(3, 1 + stock.size() / 8); i++) {
                art.drawBack(c, back(), stockRect.left - i * 2, stockRect.top - i * 2);
            }
        }
        boolean drawn = false;
        for (int i = waste.size() - 1; i >= 0 && !drawn; i--) {
            if (!hidden[waste.get(i).id()]) {
                art.drawFace(c, waste.get(i), wasteRect.left, wasteRect.top);
                drawn = true;
            }
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
        if (stockRect.contains(x, y)) {
            turnStock();
            return;
        }
        for (int i = 27; i >= 0; i--) {
            if (board[i] == null) {
                continue;
            }
            float[] p = cardPos(i);
            if (inCard(x, y, p[0], p[1])) {
                if (playable(i)) {
                    play(i);
                } else if (!covered(i)) {
                    sound(Sfx.DROP);
                }
                return;
            }
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            deal();
            return true;
        }
        return false;
    }

    /** Tiny helper for "best so far" records with a name. */
    static final class SharedEdit {
        private SharedEdit() {
        }

        static void best(android.content.SharedPreferences prefs, String key, String nameKey, int value, String name) {
            if (!prefs.contains(key) || value > prefs.getInt(key, Integer.MIN_VALUE)) {
                prefs.edit().putInt(key, value).putString(nameKey, name).apply();
            }
        }
    }
}
