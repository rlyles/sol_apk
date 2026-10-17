package com.rlyles.wep.cards;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;

import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;

/** Shared plumbing for the card games: card art and size, empty slots, flying cards, the Deck dialog. */
public abstract class CardGame extends WinApp {
    public static final int FELT = 0xFF008000;
    protected static final int EMPTY_OUTLINE = 0xFF004000;

    protected final CardArt art = new CardArt();
    protected int cw = 71, ch = 96;
    /** Cards (by id) that are not drawn in their pile right now because they are flying or dragged. */
    protected final boolean[] hidden = new boolean[52];
    private final List<Flight> flights = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected CardGame(Context context, Host host, String appId) {
        super(context, host, appId);
        art.setDll(deckArt());
    }

    /** The card pictures to use: the user's cards.dll, or null for the built-in cards. */
    protected CardsDll deckArt() {
        return host.cardsDll();
    }

    @Override
    protected void onShown() {
        art.setDll(deckArt());
    }

    @Override
    public void mediaChanged() {
        art.setDll(deckArt());
        super.mediaChanged();
    }

    protected int back() {
        return art.clampBack(prefs.getInt("back", 0));
    }

    /** Sets the card width in pixels; the height keeps the 71x96 proportions. */
    protected void setCardWidth(float w) {
        cw = Math.max(8, (int) w);
        ch = Math.round(cw * 96f / 71f);
        art.setSize(cw, ch);
    }

    protected void drawCard(Canvas c, Card card, float x, float y) {
        art.drawCard(c, card, back(), x, y);
    }

    protected void drawEmpty(Canvas c, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, cw / 50f));
        paint.setColor(EMPTY_OUTLINE);
        float in = paint.getStrokeWidth() / 2;
        float rad = cw * 0.055f;
        c.drawRoundRect(x + in, y + in, x + cw - in, y + ch - in, rad, rad, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    protected boolean inCard(float x, float y, float cx, float cy) {
        return x >= cx && x < cx + cw && y >= cy && y < cy + ch;
    }

    // ---------------------------------------------------------------- flights

    private static final class Flight {
        Card card;
        float sx, sy, ex, ey;
        long start, duration;
    }

    protected void fly(Card card, float sx, float sy, float ex, float ey, long delay, long duration) {
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

    protected boolean flying() {
        return !flights.isEmpty();
    }

    /** Draws cards in flight; keeps the view animating while any are still moving. */
    protected void drawFlights(Canvas c) {
        if (flights.isEmpty()) {
            return;
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
            drawCard(c, f.card, f.sx + (f.ex - f.sx) * e, f.sy + (f.ey - f.sy) * e);
        }
        for (Flight f : done) {
            flights.remove(f);
            hidden[f.card.id()] = false;
            drawCard(c, f.card, f.ex, f.ey);
        }
        if (flights.isEmpty()) {
            onFlightsDone();
        }
        postInvalidateOnAnimation();
    }

    protected void onFlightsDone() {
    }

    protected void finishFlights() {
        for (Flight f : flights) {
            hidden[f.card.id()] = false;
        }
        flights.clear();
    }

    // ---------------------------------------------------------------- Deck dialog

    /** Card-back picker, with the buttons for loading the user's own cards.dll. */
    public void showDeckDialog() {
        int n = art.backCount();
        int cols = 4;
        int rows = (n + cols - 1) / cols;
        float cellW = 78, cellH = 104;
        float gridH = rows * cellH;
        float width = cols * cellW + 16;
        Win95.Dialog d = new Win95.Dialog("Select Card Back", width, gridH + 116);
        int[] selected = {back()};
        long[] lastTap = {0};
        Runnable ok = () -> {
            prefs.edit().putInt("back", selected[0]).apply();
            closeDialog(d);
            invalidate();
        };
        d.add(new Win95.Custom((c, r, u) -> {
            for (int i = 0; i < n; i++) {
                float cwid = r.width() / cols, chgt = cwid * cellH / cellW;
                RectF cell = new RectF(r.left + (i % cols) * cwid, r.top + (i / cols) * chgt,
                        r.left + (i % cols + 1) * cwid, r.top + (i / cols + 1) * chgt);
                if (i == selected[0]) {
                    u.sunken(c, new RectF(cell.left + 2 * u.dp, cell.top + 2 * u.dp, cell.right - 2 * u.dp,
                            cell.bottom - 2 * u.dp), Win95.FACE);
                }
                art.drawBackIn(c, i, new RectF(cell.centerX() - 32 * u.dp, cell.centerY() - 44 * u.dp,
                        cell.centerX() + 32 * u.dp, cell.centerY() + 44 * u.dp));
            }
        }, (x, y) -> {
            RectF r = d.widgets.get(0).r;
            float cwid = r.width() / cols, chgt = cwid * cellH / cellW;
            int i = (int) ((y - r.top) / chgt) * cols + (int) ((x - r.left) / cwid);
            if (i < 0 || i >= n) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            boolean twice = i == selected[0] && now - lastTap[0] < 400;
            selected[0] = i;
            lastTap[0] = now;
            if (twice) {
                ok.run();
            }
        })).at(8, 8, cols * cellW, gridH);
        float y = gridH + 16;
        d.add(new Win95.Button("Load cards.dll...", false, () -> {
            closeDialog(d);
            host.pickFile(data -> {
                try {
                    host.setCardsDll(data);
                    art.setDll(deckArt());
                    showDeckDialog();
                } catch (java.io.IOException e) {
                    messageBox(title(), "Unable to load cards from that file.\n\n" + e.getMessage(), null);
                }
            });
        })).at(8, y, 168, 36);
        Win95.Button builtIn = d.add(new Win95.Button("Built-in cards", false, () -> {
            closeDialog(d);
            try {
                host.setCardsDll(null);
            } catch (java.io.IOException ignored) {
                // clearing never fails
            }
            art.setDll(null);
            invalidate();
        }));
        builtIn.at(width - 8 - 140, y, 140, 36);
        builtIn.enabled = art.usingDll();
        float by = y + 48;
        d.add(new Win95.Button("OK", true, ok)).at(width / 2 - 108, by, 100, 36);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(width / 2 + 8, by, 100, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }
}
