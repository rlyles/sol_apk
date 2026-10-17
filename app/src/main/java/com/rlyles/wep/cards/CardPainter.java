package com.rlyles.wep.cards;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws the built-in card artwork: faces in the style of the classic 71x96 Windows cards, and a
 * set of original card-back patterns. Everything is vector drawn so it stays sharp at any size.
 */
public final class CardPainter {
    public static final int BACK_COUNT = 12;
    public static final String[] BACK_NAMES = {
            "Crosshatch", "Red Lattice", "Basket Weave", "Scales", "Bricks", "Waves",
            "Polka Dots", "Checkers", "Candy Stripe", "Tartan", "Night Sky", "Sunburst"};

    private static final int RED = 0xFFFF0000;
    private static final int BLACK = 0xFF000000;
    private static final String RANKS = "A23456789";

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] suitPaths = new Path[4];
    private final Path tmp = new Path();
    private final Matrix matrix = new Matrix();

    public CardPainter() {
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        for (int s = 0; s < 4; s++) {
            suitPaths[s] = buildSuit(s);
        }
    }

    // ---------------------------------------------------------------- suit shapes (unit box)

    private static Path buildSuit(int suit) {
        Path p = new Path();
        switch (suit) {
            case Card.DIAMONDS:
                p.moveTo(0.5f, 0f);
                p.lineTo(0.92f, 0.5f);
                p.lineTo(0.5f, 1f);
                p.lineTo(0.08f, 0.5f);
                p.close();
                break;
            case Card.HEARTS:
                p.moveTo(0.5f, 0.97f);
                p.cubicTo(0.2f, 0.72f, 0f, 0.52f, 0f, 0.3f);
                p.cubicTo(0f, 0.12f, 0.12f, 0.02f, 0.27f, 0.02f);
                p.cubicTo(0.38f, 0.02f, 0.46f, 0.09f, 0.5f, 0.2f);
                p.cubicTo(0.54f, 0.09f, 0.62f, 0.02f, 0.73f, 0.02f);
                p.cubicTo(0.88f, 0.02f, 1f, 0.12f, 1f, 0.3f);
                p.cubicTo(1f, 0.52f, 0.8f, 0.72f, 0.5f, 0.97f);
                p.close();
                break;
            case Card.SPADES:
                p.moveTo(0.5f, 0f);
                p.cubicTo(0.82f, 0.3f, 1f, 0.45f, 1f, 0.62f);
                p.cubicTo(1f, 0.78f, 0.88f, 0.86f, 0.75f, 0.86f);
                p.cubicTo(0.64f, 0.86f, 0.56f, 0.8f, 0.53f, 0.72f);
                p.cubicTo(0.54f, 0.85f, 0.6f, 0.94f, 0.7f, 1f);
                p.lineTo(0.3f, 1f);
                p.cubicTo(0.4f, 0.94f, 0.46f, 0.85f, 0.47f, 0.72f);
                p.cubicTo(0.44f, 0.8f, 0.36f, 0.86f, 0.25f, 0.86f);
                p.cubicTo(0.12f, 0.86f, 0f, 0.78f, 0f, 0.62f);
                p.cubicTo(0f, 0.45f, 0.18f, 0.3f, 0.5f, 0f);
                p.close();
                break;
            default: // clubs
                p.addCircle(0.5f, 0.27f, 0.23f, Path.Direction.CW);
                p.addCircle(0.24f, 0.6f, 0.23f, Path.Direction.CW);
                p.addCircle(0.76f, 0.6f, 0.23f, Path.Direction.CW);
                p.addCircle(0.5f, 0.52f, 0.14f, Path.Direction.CW);
                Path stem = new Path();
                stem.moveTo(0.46f, 0.55f);
                stem.quadTo(0.44f, 0.9f, 0.28f, 1f);
                stem.lineTo(0.72f, 1f);
                stem.quadTo(0.56f, 0.9f, 0.54f, 0.55f);
                stem.close();
                p.addPath(stem);
                break;
        }
        return p;
    }

    /** Draws a suit symbol centred at (cx, cy) with the given height, optionally upside down. */
    public void drawSuit(Canvas c, int suit, float cx, float cy, float size, boolean flipped, int color) {
        float w = suit == Card.DIAMONDS ? size * 0.82f : size * 0.92f;
        matrix.reset();
        matrix.setScale(w, size);
        matrix.postTranslate(cx - w / 2, cy - size / 2);
        if (flipped) {
            matrix.postRotate(180, cx, cy);
        }
        suitPaths[suit].transform(matrix, tmp);
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        c.drawPath(tmp, fill);
    }

    // ---------------------------------------------------------------- faces

    public Bitmap renderFace(Card card, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        drawBlank(c, w, h, Color.WHITE);
        int color = card.isRed() ? RED : BLACK;
        drawIndex(c, card, w, h, color, false);
        c.save();
        c.rotate(180, w / 2f, h / 2f);
        drawIndex(c, card, w, h, color, true);
        c.restore();
        if (card.rank >= 11) {
            drawCourt(c, card, w, h, color);
        } else if (card.rank == 1) {
            float size = card.suit == Card.SPADES ? h * 0.42f : h * 0.3f;
            drawSuit(c, card.suit, w / 2f, h / 2f, size, false, color);
            if (card.suit == Card.SPADES) {
                // The ace of spades gets its traditional decorated pip.
                drawSuit(c, card.suit, w / 2f, h / 2f + size * 0.03f, size * 0.55f, false, Color.WHITE);
                drawSuit(c, card.suit, w / 2f, h / 2f + size * 0.05f, size * 0.38f, false, color);
            }
        } else {
            drawPips(c, card, w, h, color);
        }
        return b;
    }

    /** White card with the rounded black outline. */
    private void drawBlank(Canvas c, int w, int h, int color) {
        float line = Math.max(1f, w / 71f);
        float r = w * 0.055f;
        RectF rect = new RectF(line / 2, line / 2, w - line / 2, h - line / 2);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawRoundRect(rect, r, r, fill);
        stroke.setColor(BLACK);
        stroke.setStrokeWidth(line);
        c.drawRoundRect(rect, r, r, stroke);
    }

    private void drawIndex(Canvas c, Card card, int w, int h, int color, boolean bottom) {
        String label = card.rank == 10 ? "10" : card.rank <= 9 ? String.valueOf(RANKS.charAt(card.rank - 1))
                : String.valueOf("JQK".charAt(card.rank - 11));
        float cx = w * 0.13f;
        text.setColor(color);
        text.setTextSize(h * 0.17f);
        text.setTextScaleX(card.rank == 10 ? 0.62f : 0.9f);
        c.drawText(label, cx, h * 0.165f, text);
        text.setTextScaleX(1f);
        drawSuit(c, card.suit, cx, h * 0.245f, h * 0.1f, false, color);
    }

    private static final float L = 0.33f, C = 0.5f, R = 0.67f;

    private static float[][] pipLayout(int rank) {
        switch (rank) {
            case 2: return new float[][] {{C, 0}, {C, 1}};
            case 3: return new float[][] {{C, 0}, {C, 0.5f}, {C, 1}};
            case 4: return new float[][] {{L, 0}, {R, 0}, {L, 1}, {R, 1}};
            case 5: return new float[][] {{L, 0}, {R, 0}, {C, 0.5f}, {L, 1}, {R, 1}};
            case 6: return new float[][] {{L, 0}, {R, 0}, {L, 0.5f}, {R, 0.5f}, {L, 1}, {R, 1}};
            case 7: return new float[][] {{L, 0}, {R, 0}, {C, 0.25f}, {L, 0.5f}, {R, 0.5f}, {L, 1}, {R, 1}};
            case 8: return new float[][] {{L, 0}, {R, 0}, {C, 0.25f}, {L, 0.5f}, {R, 0.5f}, {C, 0.75f},
                    {L, 1}, {R, 1}};
            case 9: return new float[][] {{L, 0}, {R, 0}, {L, 1 / 3f}, {R, 1 / 3f}, {C, 0.5f},
                    {L, 2 / 3f}, {R, 2 / 3f}, {L, 1}, {R, 1}};
            default: return new float[][] {{L, 0}, {R, 0}, {C, 1 / 6f}, {L, 1 / 3f}, {R, 1 / 3f},
                    {L, 2 / 3f}, {R, 2 / 3f}, {C, 5 / 6f}, {L, 1}, {R, 1}};
        }
    }

    private void drawPips(Canvas c, Card card, int w, int h, int color) {
        float size = h * 0.14f;
        float top = h * 0.19f, bottom = h * 0.81f;
        for (float[] p : pipLayout(card.rank)) {
            float cy = top + (bottom - top) * p[1];
            drawSuit(c, card.suit, w * p[0], cy, size, p[1] > 0.5f, color);
        }
    }

    // ---------------------------------------------------------------- court cards

    private static final int GOLD = 0xFFFFD700, BLUE = 0xFF1030C0, SKIN = 0xFFFFE0B8, CREAM = 0xFFFFFFE8;

    private void drawCourt(Canvas c, Card card, int w, int h, int color) {
        RectF frame = new RectF(w * 0.2f, h * 0.1f, w * 0.8f, h * 0.9f);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(CREAM);
        c.drawRect(frame, fill);

        c.save();
        c.clipRect(frame);
        drawFigureHalf(c, card, frame, color);
        c.rotate(180, frame.centerX(), frame.centerY());
        drawFigureHalf(c, card, frame, color);
        c.restore();

        stroke.setColor(BLACK);
        stroke.setStrokeWidth(Math.max(1f, w / 71f));
        c.drawLine(frame.left, frame.centerY(), frame.right, frame.centerY(), stroke);
        c.drawRect(frame, stroke);
    }

    /** The top half of a court figure; the bottom half is the same drawing turned around. */
    private void drawFigureHalf(Canvas c, Card card, RectF f, int suitColor) {
        float fw = f.width(), hh = f.height() / 2;
        float cx = f.centerX(), top = f.top;
        float line = Math.max(1f, fw / 45f);
        stroke.setStrokeWidth(line);
        stroke.setColor(BLACK);
        int robeA = card.rank == 12 ? suitColor : BLUE;
        int robeB = card.rank == 12 ? BLUE : suitColor == RED ? RED : 0xFF208020;

        // Robe: shoulders spreading to the bottom of the half.
        Path robe = new Path();
        robe.moveTo(cx - fw * 0.12f, top + hh * 0.56f);
        robe.lineTo(cx + fw * 0.12f, top + hh * 0.56f);
        robe.lineTo(cx + fw * 0.48f, top + hh * 0.82f);
        robe.lineTo(cx + fw * 0.5f, top + hh);
        robe.lineTo(cx - fw * 0.5f, top + hh);
        robe.lineTo(cx - fw * 0.48f, top + hh * 0.82f);
        robe.close();
        fill.setColor(robeA);
        c.drawPath(robe, fill);
        // Gold trim and a contrasting panel down the middle.
        fill.setColor(robeB);
        c.drawRect(cx - fw * 0.12f, top + hh * 0.6f, cx + fw * 0.12f, top + hh, fill);
        fill.setColor(GOLD);
        for (int i = 0; i < 3; i++) {
            float y = top + hh * (0.66f + i * 0.11f);
            c.drawCircle(cx, y, fw * 0.035f, fill);
        }
        c.drawRect(cx - fw * 0.3f, top + hh * 0.9f, cx + fw * 0.3f, top + hh * 0.95f, fill);
        c.drawPath(robe, stroke);

        // Hair or beard behind the face.
        float faceY = top + hh * 0.38f, faceR = fw * 0.15f;
        if (card.rank == 12) {
            fill.setColor(GOLD);
            c.drawRoundRect(cx - faceR * 1.45f, faceY - faceR * 1.1f, cx + faceR * 1.45f, faceY + faceR * 1.9f,
                    faceR, faceR, fill);
        } else if (card.rank == 13) {
            fill.setColor(Color.WHITE);
            Path beard = new Path();
            beard.moveTo(cx - faceR * 1.05f, faceY);
            beard.quadTo(cx, faceY + faceR * 2.6f, cx + faceR * 1.05f, faceY);
            beard.close();
            c.drawPath(beard, fill);
            c.drawPath(beard, stroke);
        } else {
            fill.setColor(0xFF8B4513);
            c.drawCircle(cx, faceY - faceR * 0.2f, faceR * 1.12f, fill);
        }

        // Face.
        fill.setColor(SKIN);
        c.drawCircle(cx, faceY, faceR, fill);
        c.drawCircle(cx, faceY, faceR, stroke);
        fill.setColor(BLACK);
        c.drawCircle(cx - faceR * 0.38f, faceY - faceR * 0.12f, line * 0.9f, fill);
        c.drawCircle(cx + faceR * 0.38f, faceY - faceR * 0.12f, line * 0.9f, fill);
        fill.setColor(RED);
        c.drawRect(cx - faceR * 0.3f, faceY + faceR * 0.45f, cx + faceR * 0.3f, faceY + faceR * 0.45f + line,
                fill);

        // Headwear: a crown for king and queen, a cap for the jack.
        Path hat = new Path();
        float hatBase = faceY - faceR * 0.75f;
        if (card.rank == 11) {
            hat.moveTo(cx - faceR * 1.2f, hatBase);
            hat.quadTo(cx, hatBase - faceR * 1.6f, cx + faceR * 1.2f, hatBase);
            hat.close();
            fill.setColor(robeB);
        } else {
            float peak = card.rank == 13 ? faceR * 1.3f : faceR * 1.0f;
            hat.moveTo(cx - faceR * 1.05f, hatBase);
            hat.lineTo(cx - faceR * 1.05f, hatBase - peak);
            hat.lineTo(cx - faceR * 0.52f, hatBase - peak * 0.55f);
            hat.lineTo(cx, hatBase - peak * 1.1f);
            hat.lineTo(cx + faceR * 0.52f, hatBase - peak * 0.55f);
            hat.lineTo(cx + faceR * 1.05f, hatBase - peak);
            hat.lineTo(cx + faceR * 1.05f, hatBase);
            hat.close();
            fill.setColor(GOLD);
        }
        c.drawPath(hat, fill);
        c.drawPath(hat, stroke);

        // The suit the figure holds, in the corner of the frame.
        drawSuit(c, card.suit, f.left + fw * 0.17f, top + hh * 0.17f, fw * 0.22f, false, suitColor);
    }

    // ---------------------------------------------------------------- backs

    public Bitmap renderBack(int design, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        drawBlank(c, w, h, Color.WHITE);
        float inset = Math.max(2f, w * 0.06f);
        RectF in = new RectF(inset, inset, w - inset, h - inset);
        c.save();
        c.clipRect(in);
        c.translate(in.left, in.top);
        paintPattern(c, design, in.width(), in.height(), w / 71f);
        c.restore();
        stroke.setColor(BLACK);
        stroke.setStrokeWidth(Math.max(1f, w / 71f));
        c.drawRect(in, stroke);
        return b;
    }

    private void paintPattern(Canvas c, int design, float w, float h, float s) {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStrokeWidth(Math.max(1f, s));
        switch (design) {
            case 0: // blue crosshatch
            case 1: { // red lattice
                c.drawColor(design == 0 ? 0xFF0000A8 : 0xFFB00000);
                stroke.setColor(design == 0 ? 0xFF8090FF : 0xFFFFA0A0);
                float step = 6 * s;
                for (float d = -h; d < w + h; d += step) {
                    c.drawLine(d, 0, d + h, h, stroke);
                    c.drawLine(d, h, d + h, 0, stroke);
                }
                break;
            }
            case 2: { // basket weave
                c.drawColor(0xFF6B3E12);
                float cell = 8 * s;
                for (int row = 0; row * cell < h; row++) {
                    for (int col = 0; col * cell < w; col++) {
                        float x = col * cell, y = row * cell;
                        fill.setColor(0xFFD9A55B);
                        if (((row + col) & 1) == 0) {
                            c.drawRect(x + s, y + 2 * s, x + cell - s, y + cell / 2 - s / 2, fill);
                            c.drawRect(x + s, y + cell / 2 + s / 2, x + cell - s, y + cell - 2 * s, fill);
                        } else {
                            c.drawRect(x + 2 * s, y + s, x + cell / 2 - s / 2, y + cell - s, fill);
                            c.drawRect(x + cell / 2 + s / 2, y + s, x + cell - 2 * s, y + cell - s, fill);
                        }
                    }
                }
                break;
            }
            case 3: { // fish scales
                c.drawColor(0xFF0B6B3A);
                float r = 6 * s;
                for (int row = 0; row * r < h + r; row++) {
                    float off = (row & 1) == 0 ? 0 : r;
                    for (float x = -r + off; x < w + r; x += 2 * r) {
                        float y = row * r;
                        fill.setColor(0xFF2FA35F);
                        c.drawCircle(x, y, r, fill);
                        stroke.setColor(0xFFB8F0C8);
                        c.drawCircle(x, y, r, stroke);
                    }
                }
                break;
            }
            case 4: { // bricks
                c.drawColor(0xFFC8C8C0);
                float bh = 6 * s, bw = 14 * s;
                fill.setColor(0xFFA83018);
                for (int row = 0; row * bh < h; row++) {
                    float off = (row & 1) == 0 ? 0 : bw / 2;
                    for (float x = -off; x < w; x += bw) {
                        c.drawRect(x + s, row * bh + s, x + bw - s / 2, row * bh + bh - s / 2, fill);
                    }
                }
                break;
            }
            case 5: { // waves
                c.drawColor(0xFF0050A0);
                stroke.setColor(0xFF9AD8FF);
                stroke.setStrokeWidth(1.5f * s);
                float amp = 2.5f * s, len = 12 * s;
                Path p = new Path();
                for (float y = 3 * s; y < h + amp; y += 6 * s) {
                    p.reset();
                    p.moveTo(-len, y);
                    for (float x = -len; x < w + len; x += len) {
                        p.quadTo(x + len / 4, y - amp, x + len / 2, y);
                        p.quadTo(x + 3 * len / 4, y + amp, x + len, y);
                    }
                    c.drawPath(p, stroke);
                }
                break;
            }
            case 6: { // polka dots
                c.drawColor(0xFFD01020);
                fill.setColor(0xFFFFE040);
                float step = 9 * s;
                for (int row = 0; row * step / 2 < h + step; row++) {
                    float off = (row & 1) == 0 ? 0 : step / 2;
                    for (float x = off; x < w + step; x += step) {
                        c.drawCircle(x, row * step / 2, 2.2f * s, fill);
                    }
                }
                break;
            }
            case 7: { // checkers
                c.drawColor(0xFF101060);
                fill.setColor(0xFF5050D0);
                float cell = 5 * s;
                for (int row = 0; row * cell < h; row++) {
                    for (int col = 0; col * cell < w; col++) {
                        if (((row + col) & 1) == 0) {
                            c.drawRect(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell, fill);
                        }
                    }
                }
                break;
            }
            case 8: { // candy stripe
                c.drawColor(0xFFFFFFFF);
                stroke.setColor(0xFFC02080);
                stroke.setStrokeWidth(3 * s);
                for (float d = -h; d < w + h; d += 8 * s) {
                    c.drawLine(d, 0, d + h, h, stroke);
                }
                break;
            }
            case 9: { // tartan
                c.drawColor(0xFF0A4A20);
                fill.setColor(0x990A1A80);
                float band = 16 * s;
                for (float x = 0; x < w; x += band) {
                    c.drawRect(x, 0, x + band * 0.4f, h, fill);
                }
                for (float y = 0; y < h; y += band) {
                    c.drawRect(0, y, w, y + band * 0.4f, fill);
                }
                stroke.setColor(0xFFE0C020);
                stroke.setStrokeWidth(Math.max(1f, s * 0.8f));
                for (float x = band * 0.7f; x < w; x += band) {
                    c.drawLine(x, 0, x, h, stroke);
                }
                for (float y = band * 0.7f; y < h; y += band) {
                    c.drawLine(0, y, w, y, stroke);
                }
                break;
            }
            case 10: { // night sky
                c.drawColor(0xFF000830);
                fill.setColor(0xFFFFF4C0);
                c.drawCircle(w * 0.68f, h * 0.22f, 9 * s, fill);
                fill.setColor(0xFF000830);
                c.drawCircle(w * 0.62f, h * 0.19f, 8 * s, fill);
                fill.setColor(Color.WHITE);
                java.util.Random rnd = new java.util.Random(7);
                for (int i = 0; i < 40; i++) {
                    c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, (0.5f + rnd.nextFloat()) * s, fill);
                }
                break;
            }
            default: { // sunburst
                c.drawColor(0xFFFF8C00);
                fill.setColor(0xFFFFC820);
                float cx = w / 2, cy = h / 2, r = w + h;
                Path p = new Path();
                for (int i = 0; i < 24; i += 2) {
                    double a0 = Math.PI * 2 * i / 24, a1 = Math.PI * 2 * (i + 1) / 24;
                    p.reset();
                    p.moveTo(cx, cy);
                    p.lineTo(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r);
                    p.lineTo(cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r);
                    p.close();
                    c.drawPath(p, fill);
                }
                fill.setColor(0xFFFFF0A0);
                c.drawCircle(cx, cy, w * 0.18f, fill);
                break;
            }
        }
    }

    // ---------------------------------------------------------------- empty-stock markers

    /** The green "O" (stock can be turned over) or red "X" (no more passes) on an empty stock. */
    public void drawStockMarker(Canvas c, RectF r, boolean canRecycle) {
        stroke.setStrokeWidth(r.width() * 0.09f);
        float cx = r.centerX(), cy = r.centerY(), rad = r.width() * 0.3f;
        if (canRecycle) {
            stroke.setColor(0xFF00FF00);
            c.drawCircle(cx, cy, rad, stroke);
        } else {
            stroke.setColor(0xFFFF0000);
            c.drawLine(cx - rad, cy - rad, cx + rad, cy + rad, stroke);
            c.drawLine(cx - rad, cy + rad, cx + rad, cy - rad, stroke);
        }
    }
}
