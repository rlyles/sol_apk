package com.rlyles.wep.cards;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Supplies card images at the current on-screen card size, either from the built-in
 * {@link CardPainter} artwork or from a cards.dll the user loaded.
 */
public final class CardArt {
    private final CardPainter painter = new CardPainter();
    private CardsDll dll;
    private int w, h;
    private final Bitmap[] faces = new Bitmap[52];
    private final Bitmap[] backs = new Bitmap[CardPainter.BACK_COUNT];
    /** DLL bitmaps are pixel art, so scale them without smoothing to keep them crisp. */
    private final Paint pixelated = new Paint();
    private final Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final Rect src = new Rect();

    public void setDll(CardsDll dll) {
        this.dll = dll;
    }

    public boolean usingDll() {
        return dll != null;
    }

    public void setSize(int w, int h) {
        if (w == this.w && h == this.h) {
            return;
        }
        this.w = w;
        this.h = h;
        java.util.Arrays.fill(faces, null);
        java.util.Arrays.fill(backs, null);
    }

    public int backCount() {
        return dll != null ? dll.backs.size() : CardPainter.BACK_COUNT;
    }

    public String backName(int back) {
        return dll != null ? "Design " + (back + 1) : CardPainter.BACK_NAMES[back];
    }

    public void drawCard(Canvas c, Card card, int back, float x, float y) {
        if (card.faceUp) {
            drawFace(c, card, x, y);
        } else {
            drawBack(c, back, x, y);
        }
    }

    public void drawFace(Canvas c, Card card, float x, float y) {
        if (dll != null) {
            drawScaled(c, dll.faces[card.id()], x, y, w, h, pixelated);
            return;
        }
        Bitmap b = faces[card.id()];
        if (b == null) {
            b = faces[card.id()] = painter.renderFace(card, w, h);
        }
        c.drawBitmap(b, x, y, null);
    }

    private final Paint inverted = new Paint();

    {
        inverted.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[] {
                -1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0}));
    }

    /** A face drawn in inverted colours: how the Entertainment Pack games show a selected card. */
    public void drawFaceInverted(Canvas c, Card card, float x, float y) {
        if (dll != null) {
            drawScaled(c, dll.faces[card.id()], x, y, w, h, inverted);
            return;
        }
        Bitmap b = faces[card.id()];
        if (b == null) {
            b = faces[card.id()] = painter.renderFace(card, w, h);
        }
        c.drawBitmap(b, x, y, inverted);
    }

    public void drawBack(Canvas c, int back, float x, float y) {
        back = clampBack(back);
        if (dll != null) {
            drawScaled(c, dll.backs.get(back), x, y, w, h, pixelated);
            return;
        }
        c.drawBitmap(builtInBack(back), x, y, null);
    }

    /** Draws a card back into an arbitrary rectangle (the Deck dialog's thumbnails). */
    public void drawBackIn(Canvas c, int back, RectF r) {
        back = clampBack(back);
        if (dll != null) {
            drawScaled(c, dll.backs.get(back), r.left, r.top, r.width(), r.height(), pixelated);
        } else {
            drawScaled(c, builtInBack(back), r.left, r.top, r.width(), r.height(), smooth);
        }
    }

    public void drawStockMarker(Canvas c, RectF r, boolean canRecycle) {
        Bitmap marker = dll == null ? null : canRecycle ? dll.o : dll.x;
        if (marker != null) {
            drawScaled(c, marker, r.left, r.top, r.width(), r.height(), pixelated);
        } else {
            painter.drawStockMarker(c, r, canRecycle);
        }
    }

    public int clampBack(int back) {
        return Math.max(0, Math.min(back, backCount() - 1));
    }

    private Bitmap builtInBack(int back) {
        Bitmap b = backs[back];
        if (b == null) {
            b = backs[back] = painter.renderBack(back, w, h);
        }
        return b;
    }

    private void drawScaled(Canvas c, Bitmap b, float x, float y, float dw, float dh, Paint p) {
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(x, y, x + dw, y + dh);
        c.drawBitmap(b, src, dst, p);
    }
}
