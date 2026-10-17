package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.cards.Card;
import com.rlyles.wep.cards.CardGame;
import com.rlyles.wep.win.Sfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * BlackJack against the house: a four-deck shoe, dealer stands on 17, blackjack pays 3 to 2,
 * double on any two cards and split pairs. With Hints on, the status bar suggests the play.
 */
@SuppressLint("ViewConstructor")
public final class BlackJack extends CardGame {
    private static final int DECKS = 4;

    static final class Hand {
        final List<Card> cards = new ArrayList<>();
        int bet;
        boolean done, doubled, fromSplitAces;
        String result = "";
    }

    private final Random rng = new Random();
    private final List<Card> shoe = new ArrayList<>();
    final List<Hand> hands = new ArrayList<>();
    final Hand dealer = new Hand();
    int bank, bet, active;
    boolean inRound, holeShown, hints;
    private String message = "Place your bet and press DEAL.";
    private int won, lost, pushed;

    private static final String[] LABELS = {"&DEAL", "&HIT", "&STAY", "DOU&BLE", "SPLI&T", "&+10", "&-10"};
    private final RectF[] buttons = new RectF[LABELS.length];
    private int pressed = -1;
    private float dealerY, playerY;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BlackJack(Context context, Host host) {
        super(context, host, "blackjack");
        bank = prefs.getInt("bank", 1000);
        bet = prefs.getInt("bet", 10);
        hints = prefs.getBoolean("hints", true);
        won = prefs.getInt("won", 0);
        lost = prefs.getInt("lost", 0);
        pushed = prefs.getInt("pushed", 0);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new RectF();
        }
        fillShoe();
    }

    @Override
    public String title() {
        return "BlackJack";
    }

    @Override
    public void save() {
        prefs.edit().putInt("bank", bank + (inRound ? totalBets() : 0)).putInt("bet", bet).putBoolean("hints", hints)
                .putInt("won", won).putInt("lost", lost).putInt("pushed", pushed).apply();
    }

    private int totalBets() {
        int t = 0;
        for (Hand h : hands) {
            t += h.bet;
        }
        return t;
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&Deal", "F2", "Deal a hand", !inRound, false, this::deal));
            items.add(new Item("&New Bankroll", "", "Start again with $1000", true, false, () -> {
                bank = 1000;
                inRound = false;
                hands.clear();
                dealer.cards.clear();
                message = "You have a fresh $1000. Good luck!";
                save();
                invalidate();
            }));
            items.add(new Item("&Statistics...", "", "Hands won and lost", true, false, () -> messageBox("Statistics",
                    "Hands won: " + won + "\nHands lost: " + lost + "\nPushes: " + pushed + "\n\nBank: $" + bank, null)));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit BlackJack", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Options", items -> {
            items.add(new Item("&Hints", "", "Suggest the best play", true, hints, () -> hints = !hints));
            items.add(new Item("&Deck...", "", "Choose the card back", true, false, this::showDeckDialog));
        }));
        addHelpMenu(menus, "Get closer to 21 than the dealer without going over. Number cards count their "
                + "value, face cards count 10, and an Ace counts 1 or 11.\n\nSet your bet with +10 and -10, then "
                + "press DEAL. HIT takes another card; STAY stands. DOUBLE doubles your bet for exactly one more card. "
                + "SPLIT turns a pair into two hands.\n\nThe dealer must hit until reaching 17. A blackjack (an Ace and "
                + "a ten-card) pays 3 to 2; other wins pay even money.\n\nWith Hints on, the status bar suggests the "
                + "play that basic strategy recommends.", "BlackJack for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected void statusPanels(List<String> panels) {
        panels.add("Bet: $" + bet);
        panels.add("Bank: $" + bank);
    }

    @Override
    protected String statusText() {
        if (inRound && hints && active < hands.size()) {
            return "Hint: " + hint(hands.get(active));
        }
        return message;
    }

    // ---------------------------------------------------------------- shoe and scoring

    private void fillShoe() {
        shoe.clear();
        for (int d = 0; d < DECKS; d++) {
            for (int s = 0; s < 4; s++) {
                for (int r = 1; r <= 13; r++) {
                    Card c = new Card(s, r);
                    c.faceUp = true;
                    shoe.add(c);
                }
            }
        }
        Collections.shuffle(shoe, rng);
    }

    private Card draw() {
        if (shoe.size() < 52) {
            fillShoe();
            message = "Shuffling...";
        }
        return shoe.remove(shoe.size() - 1);
    }

    static int value(List<Card> cards) {
        int total = 0, aces = 0;
        for (Card c : cards) {
            if (!c.faceUp) {
                continue;
            }
            total += Math.min(10, c.rank);
            if (c.rank == 1) {
                aces++;
            }
        }
        if (aces > 0 && total + 10 <= 21) {
            total += 10;
        }
        return total;
    }

    static boolean soft(List<Card> cards) {
        int total = 0;
        boolean ace = false;
        for (Card c : cards) {
            total += Math.min(10, c.rank);
            ace |= c.rank == 1;
        }
        return ace && total + 10 <= 21;
    }

    static boolean blackjack(Hand h) {
        return h.cards.size() == 2 && value(h.cards) == 21 && !h.fromSplitAces;
    }

    // ---------------------------------------------------------------- play

    void deal() {
        if (inRound) {
            return;
        }
        if (bank < bet) {
            if (bank < 10) {
                message = "You're broke! Choose Game > New Bankroll.";
                sound(Sfx.LOSE);
                invalidate();
                return;
            }
            bet = bank / 10 * 10;
        }
        hands.clear();
        dealer.cards.clear();
        Hand h = new Hand();
        h.bet = bet;
        bank -= bet;
        hands.add(h);
        active = 0;
        holeShown = false;
        h.cards.add(draw());
        dealer.cards.add(draw());
        h.cards.add(draw());
        Card hole = draw();
        hole.faceUp = false;
        dealer.cards.add(hole);
        inRound = true;
        message = "";
        sound(Sfx.CLICK);
        Card up = dealer.cards.get(0);
        if (up.rank == 1 || up.rank >= 10) {
            List<Card> peek = new ArrayList<>(dealer.cards);
            peek.get(1).faceUp = true;
            boolean dealerBj = value(peek) == 21;
            peek.get(1).faceUp = false;
            if (dealerBj) {
                finishRound();
                return;
            }
        }
        if (blackjack(h)) {
            finishRound();
            return;
        }
        invalidate();
    }

    void hit() {
        if (!inRound) {
            return;
        }
        Hand h = hands.get(active);
        h.cards.add(draw());
        sound(Sfx.CLICK);
        if (value(h.cards) >= 21) {
            nextHand();
        }
        invalidate();
    }

    void stay() {
        if (inRound) {
            nextHand();
        }
    }

    boolean canDouble() {
        return inRound && hands.get(active).cards.size() == 2 && bank >= hands.get(active).bet
                && !hands.get(active).fromSplitAces;
    }

    void doubleDown() {
        if (!canDouble()) {
            return;
        }
        Hand h = hands.get(active);
        bank -= h.bet;
        h.bet *= 2;
        h.doubled = true;
        h.cards.add(draw());
        nextHand();
    }

    boolean canSplit() {
        if (!inRound || hands.size() >= 4) {
            return false;
        }
        Hand h = hands.get(active);
        return h.cards.size() == 2 && Math.min(10, h.cards.get(0).rank) == Math.min(10, h.cards.get(1).rank)
                && bank >= h.bet && !h.fromSplitAces;
    }

    void split() {
        if (!canSplit()) {
            return;
        }
        Hand h = hands.get(active);
        Hand other = new Hand();
        other.bet = h.bet;
        bank -= h.bet;
        other.cards.add(h.cards.remove(1));
        boolean aces = h.cards.get(0).rank == 1;
        h.cards.add(draw());
        other.cards.add(draw());
        h.fromSplitAces = other.fromSplitAces = aces;
        hands.add(active + 1, other);
        if (aces) {
            // Split aces get one card each.
            active = hands.size();
            dealerTurn();
        }
        invalidate();
    }

    private void nextHand() {
        hands.get(active).done = true;
        active++;
        if (active >= hands.size()) {
            dealerTurn();
        }
        invalidate();
    }

    private void dealerTurn() {
        boolean anyLive = false;
        for (Hand h : hands) {
            if (value(h.cards) <= 21) {
                anyLive = true;
            }
        }
        dealer.cards.get(1).faceUp = true;
        holeShown = true;
        while (anyLive && value(dealer.cards) < 17) {
            dealer.cards.add(draw());
        }
        finishRound();
    }

    private void finishRound() {
        dealer.cards.get(1).faceUp = true;
        holeShown = true;
        int d = value(dealer.cards);
        boolean dealerBj = dealer.cards.size() == 2 && d == 21;
        int net = 0;
        for (Hand h : hands) {
            int p = value(h.cards);
            int payout;
            if (p > 21) {
                h.result = "BUST";
                payout = 0;
            } else if (blackjack(h) && !dealerBj) {
                h.result = "BLACKJACK!";
                payout = h.bet + h.bet * 3 / 2;
            } else if (dealerBj && !blackjack(h)) {
                h.result = "LOSE";
                payout = 0;
            } else if (d > 21 || p > d) {
                h.result = "WIN";
                payout = h.bet * 2;
            } else if (p == d) {
                h.result = "PUSH";
                payout = h.bet;
            } else {
                h.result = "LOSE";
                payout = 0;
            }
            bank += payout;
            net += payout - h.bet;
            if (payout > h.bet) {
                won++;
            } else if (payout == h.bet) {
                pushed++;
            } else {
                lost++;
            }
        }
        inRound = false;
        active = hands.size();
        message = net > 0 ? "You win $" + net + "!" : net < 0 ? "You lose $" + (-net) + "." : "Push.";
        if (dealerBj) {
            message = "Dealer has BlackJack. " + message;
        }
        sound(net > 0 ? Sfx.WIN : net < 0 ? Sfx.LOSE : Sfx.BLIP);
        save();
        invalidate();
    }

    /** Basic strategy for a multi-deck game where the dealer stands on soft 17. */
    String hint(Hand h) {
        int up = Math.min(10, dealer.cards.get(0).rank);
        if (up == 1) {
            up = 11;
        }
        int total = value(h.cards);
        boolean pair = h.cards.size() == 2 && Math.min(10, h.cards.get(0).rank) == Math.min(10, h.cards.get(1).rank);
        boolean two = h.cards.size() == 2;
        if (pair && canSplit()) {
            int r = Math.min(10, h.cards.get(0).rank);
            if (r == 1 || r == 8) {
                return "SPLIT";
            }
            if ((r == 2 || r == 3 || r == 7) && up <= 7 || r == 6 && up <= 6 || r == 9 && up != 7 && up < 10
                    || r == 4 && (up == 5 || up == 6)) {
                return "SPLIT";
            }
        }
        if (soft(h.cards) && total < 21) {
            if (total >= 19) {
                return "STAY";
            }
            if (total == 18) {
                return up >= 3 && up <= 6 && two ? "DOUBLE" : up >= 9 ? "HIT" : "STAY";
            }
            boolean dbl = two && (total == 17 && up >= 3 && up <= 6 || total >= 15 && up >= 4 && up <= 6
                    || total >= 13 && up >= 5 && up <= 6);
            return dbl ? "DOUBLE" : "HIT";
        }
        if (total >= 17) {
            return "STAY";
        }
        if (total >= 13) {
            return up <= 6 ? "STAY" : "HIT";
        }
        if (total == 12) {
            return up >= 4 && up <= 6 ? "STAY" : "HIT";
        }
        if (two && (total == 11 || total == 10 && up <= 9 || total == 9 && up >= 3 && up <= 6)) {
            return "DOUBLE";
        }
        return "HIT";
    }

    // ---------------------------------------------------------------- layout and drawing

    @Override
    protected void layoutClient(RectF r) {
        float btnH = Math.min(44 * dp, r.height() * 0.12f);
        float bw = r.width() / buttons.length;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].set(r.left + i * bw + 3 * dp, r.bottom - btnH - 4 * dp, r.left + (i + 1) * bw - 3 * dp,
                    r.bottom - 4 * dp);
        }
        float play = r.height() - btnH - 8 * dp;
        float byHeight = play / 3.1f * 71f / 96f;
        setCardWidth(Math.min(r.width() / 7.5f, byHeight));
        dealerY = r.top + play * 0.08f;
        playerY = r.top + play * 0.52f;
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(FELT);
        float step = cw * 0.32f;
        ui.withFont(14, true, () -> {
            ui.text.setColor(0xFFFFFF80);
            String dl = "Dealer" + (dealer.cards.isEmpty() ? "" : ": " + (holeShown ? value(dealer.cards)
                    : value(dealer.cards.subList(0, 1))));
            c.drawText(dl, client.left + 10 * dp, dealerY - 6 * dp + ch / 2, ui.text);
        });
        drawHand(c, dealer.cards, client.centerX() - (cw + step * (dealer.cards.size() - 1)) / 2, dealerY, false);

        int n = hands.size();
        float slot = client.width() / Math.max(1, n);
        for (int i = 0; i < n; i++) {
            Hand h = hands.get(i);
            float w = cw + step * (h.cards.size() - 1);
            float x = client.left + slot * i + (slot - w) / 2;
            drawHand(c, h.cards, x, playerY, inRound && i == active && n > 1);
            float ty = playerY + ch + 18 * dp;
            int fi = i;
            ui.withFont(13, true, () -> {
                String label = value(h.cards) + "   $" + h.bet + (h.result.isEmpty() ? "" : "   " + h.result);
                ui.text.setColor(fi == active && inRound ? 0xFFFFFF00 : 0xFFFFFFFF);
                ui.text.setTextAlign(Paint.Align.CENTER);
                c.drawText(label, client.left + slot * fi + slot / 2, ty, ui.text);
                ui.text.setTextAlign(Paint.Align.LEFT);
            });
        }
        if (hands.isEmpty()) {
            ui.withFont(16, true, () -> {
                ui.text.setColor(0xFFFFFFFF);
                ui.text.setTextAlign(Paint.Align.CENTER);
                c.drawText("Bet: $" + bet, client.centerX(), playerY + ch / 2, ui.text);
                ui.text.setTextAlign(Paint.Align.LEFT);
            });
        }
        boolean[] enabled = {!inRound, inRound, inRound, canDouble(), canSplit(), !inRound && bet < 500 && bet + 10 <= bank,
                !inRound && bet > 10};
        for (int i = 0; i < buttons.length; i++) {
            ui.button(c, buttons[i], LABELS[i], pressed == i && enabled[i], enabled[i], false);
        }
    }

    private void drawHand(Canvas c, List<Card> cards, float x, float y, boolean highlight) {
        float step = cw * 0.32f;
        if (highlight) {
            paint.setColor(0x60FFFF00);
            c.drawRect(x - 4 * dp, y - 4 * dp, x + cw + step * (cards.size() - 1) + 4 * dp, y + ch + 4 * dp, paint);
        }
        for (int i = 0; i < cards.size(); i++) {
            drawCard(c, cards.get(i), x + i * step, y);
        }
    }

    @Override
    protected void clientTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int hit = -1;
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].contains(x, y)) {
                hit = i;
            }
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = hit;
                break;
            case MotionEvent.ACTION_UP:
                if (hit >= 0 && hit == pressed) {
                    press(hit);
                }
                pressed = -1;
                break;
            case MotionEvent.ACTION_MOVE:
                if (hit != pressed) {
                    pressed = -1;
                }
                break;
            default:
                pressed = -1;
                break;
        }
    }

    void press(int button) {
        switch (button) {
            case 0: deal(); break;
            case 1: hit(); break;
            case 2: stay(); break;
            case 3: doubleDown(); break;
            case 4: split(); break;
            case 5:
                if (!inRound && bet < 500 && bet + 10 <= bank) {
                    bet += 10;
                }
                break;
            default:
                if (!inRound && bet > 10) {
                    bet -= 10;
                }
                break;
        }
        invalidate();
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_F2:
            case KeyEvent.KEYCODE_D: press(0); return true;
            case KeyEvent.KEYCODE_H: press(1); return true;
            case KeyEvent.KEYCODE_S: press(2); return true;
            case KeyEvent.KEYCODE_B: press(3); return true;
            case KeyEvent.KEYCODE_T: press(4); return true;
            default: return false;
        }
    }
}
