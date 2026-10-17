package com.rlyles.wep.media;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/** Cutting up and drawing the originals' pixel art: sprite sheets, masks, crisp scaling. */
public final class Sprites {
    private static final Paint PIXEL = new Paint();
    private static final Paint TILE = new Paint();
    private static final Matrix MATRIX = new Matrix();
    private static final RectF DST = new RectF();
    private static final Rect SRC = new Rect();

    static {
        PIXEL.setFilterBitmap(false);
        PIXEL.setAntiAlias(false);
        PIXEL.setDither(false);
        TILE.setFilterBitmap(false);
    }

    private Sprites() {
    }

    /** Draws a bitmap scaled into {@code dst} with hard pixel edges. */
    public static void draw(Canvas c, Bitmap b, RectF dst) {
        c.drawBitmap(b, null, dst, PIXEL);
    }

    public static void draw(Canvas c, Bitmap b, float x, float y, float w, float h) {
        DST.set(x, y, x + w, y + h);
        c.drawBitmap(b, null, DST, PIXEL);
    }

    /** Draws at a uniform scale with the top-left corner at (x, y). */
    public static void drawScaled(Canvas c, Bitmap b, float x, float y, float scale) {
        draw(c, b, x, y, b.getWidth() * scale, b.getHeight() * scale);
    }

    /** Draws part of a bitmap. */
    public static void drawPart(Canvas c, Bitmap b, int sx, int sy, int sw, int sh, RectF dst) {
        SRC.set(sx, sy, sx + sw, sy + sh);
        c.drawBitmap(b, SRC, dst, PIXEL);
    }

    /** Draws with an alpha (0-255). */
    public static void draw(Canvas c, Bitmap b, RectF dst, int alpha) {
        PIXEL.setAlpha(alpha);
        c.drawBitmap(b, null, dst, PIXEL);
        PIXEL.setAlpha(255);
    }

    /** Fills {@code area} with copies of {@code b}, each pixel {@code scale} screen pixels wide. */
    public static void tile(Canvas c, Bitmap b, RectF area, float scale, float originX, float originY) {
        TILE.setShader(new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        MATRIX.setScale(scale, scale);
        MATRIX.postTranslate(originX, originY);
        TILE.getShader().setLocalMatrix(MATRIX);
        c.drawRect(area, TILE);
        TILE.setShader(null);
    }

    public static Bitmap slice(Bitmap sheet, int x, int y, int w, int h) {
        return Bitmap.createBitmap(sheet, x, y, w, h);
    }

    /** A sprite whose transparent pixels are those that are white (or black) in {@code mask}. */
    public static Bitmap masked(Bitmap image, Bitmap mask, boolean whiteIsOpaque) {
        int w = image.getWidth(), h = image.getHeight();
        int[] px = new int[w * h], m = new int[w * h];
        image.getPixels(px, 0, w, 0, 0, w, h);
        mask.getPixels(m, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            boolean white = Color.red(m[i]) + Color.green(m[i]) + Color.blue(m[i]) > 384;
            if (white != whiteIsOpaque) {
                px[i] = Color.TRANSPARENT;
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** A copy with every pixel of colour {@code key} made transparent. */
    public static Bitmap keyed(Bitmap image, int key) {
        int w = image.getWidth(), h = image.getHeight();
        int[] px = new int[w * h];
        image.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            if ((px[i] | 0xFF000000) == (key | 0xFF000000)) {
                px[i] = Color.TRANSPARENT;
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /**
     * A copy with the background made transparent: the colour of the corner pixel, flood-filled
     * from the edges, so the same colour inside the sprite (a white shirt on a white background)
     * stays.
     */
    public static Bitmap cutOut(Bitmap image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] px = new int[w * h];
        image.getPixels(px, 0, w, 0, 0, w, h);
        int key = px[0];
        // Each pixel can be pushed once by each of its four neighbours.
        int[] stack = new int[4 * w * h + 4 * (w + h)];
        boolean[] seen = new boolean[w * h];
        int sp = 0;
        for (int x = 0; x < w; x++) {
            stack[sp++] = x;
            stack[sp++] = (h - 1) * w + x;
        }
        for (int y = 0; y < h; y++) {
            stack[sp++] = y * w;
            stack[sp++] = y * w + w - 1;
        }
        while (sp > 0) {
            int i = stack[--sp];
            if (seen[i] || px[i] != key) {
                continue;
            }
            seen[i] = true;
            int x = i % w, y = i / w;
            if (x > 0) {
                stack[sp++] = i - 1;
            }
            if (x < w - 1) {
                stack[sp++] = i + 1;
            }
            if (y > 0) {
                stack[sp++] = i - w;
            }
            if (y < h - 1) {
                stack[sp++] = i + w;
            }
        }
        for (int i = 0; i < px.length; i++) {
            if (seen[i]) {
                px[i] = Color.TRANSPARENT;
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }
}
