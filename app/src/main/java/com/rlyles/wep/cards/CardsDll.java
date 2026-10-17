package com.rlyles.wep.cards;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.rlyles.wep.media.Dib;
import com.rlyles.wep.media.ResFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the card bitmaps out of a Windows cards.dll supplied by the user.
 *
 * <p>Both the 16-bit DLL from Windows 3.x/95 and the 32-bit one from later versions work. Only
 * the resource directory is read; nothing in the DLL is executed. Faces are bitmap
 * ids 1-52 (clubs, diamonds, hearts, spades; ace to king), card backs live in 53-66, and 67/68
 * are the "X" and "O" markers drawn on an empty stock.
 */
public final class CardsDll {
    public static final int CARD_W = 71, CARD_H = 96;

    public final Bitmap[] faces = new Bitmap[52];
    public final List<Bitmap> backs = new ArrayList<>();
    public Bitmap x, o;

    private CardsDll() {
    }

    public static CardsDll parse(byte[] data) throws IOException {
        TreeMap<Integer, byte[]> dibs = readBitmapResources(data);
        CardsDll art = new CardsDll();
        for (int i = 0; i < 52; i++) {
            byte[] dib = dibs.get(i + 1);
            if (dib == null) {
                throw new IOException("This file has no card faces in it.");
            }
            art.faces[i] = roundCorners(decodeDib(dib), true);
        }
        for (int id = 53; id <= 66; id++) {
            byte[] dib = dibs.get(id);
            if (dib == null) {
                continue;
            }
            Bitmap b = decodeDib(dib);
            // Some versions keep a plain grey placeholder in this range; real backs have colour.
            if (b.getWidth() == CARD_W && b.getHeight() == CARD_H && hasColour(b)) {
                art.backs.add(roundCorners(b, false));
            }
        }
        if (art.backs.isEmpty()) {
            throw new IOException("This file has no card backs in it.");
        }
        if (dibs.containsKey(67)) {
            art.x = decodeDib(dibs.get(67));
        }
        if (dibs.containsKey(68)) {
            art.o = decodeDib(dibs.get(68));
        }
        return art;
    }

    /**
     * A deck from bitmaps found elsewhere (TriPeaks keeps its own smaller deck in TRIPEAKS.EXE):
     * 52 faces in cards.dll order and at least one back.
     */
    public static CardsDll fromBitmaps(Bitmap[] faces, List<Bitmap> backs) {
        CardsDll art = new CardsDll();
        for (int i = 0; i < 52; i++) {
            art.faces[i] = roundCorners(faces[i], true);
        }
        for (Bitmap b : backs) {
            art.backs.add(roundCorners(b, false));
        }
        return art;
    }

    private static boolean hasColour(Bitmap b) {
        for (int y = 0; y < b.getHeight(); y += 3) {
            for (int x = 0; x < b.getWidth(); x += 3) {
                int c = b.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), bl = Color.blue(c);
                if (Math.abs(r - g) > 24 || Math.abs(g - bl) > 24 || Math.abs(r - bl) > 24) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TreeMap<Integer, byte[]> readBitmapResources(byte[] data) throws IOException {
        TreeMap<Integer, byte[]> out = new TreeMap<>();
        for (Map.Entry<String, byte[]> e : ResFile.parse(data).type(ResFile.BITMAP).entrySet()) {
            try {
                out.put(Integer.parseInt(e.getKey()), e.getValue());
            } catch (NumberFormatException ignored) {
                // named resources are not card bitmaps
            }
        }
        return out;
    }

    /** Decodes a device-independent bitmap (as stored in RT_BITMAP resources). */
    public static Bitmap decodeDib(byte[] d) throws IOException {
        return Dib.decode(d);
    }

    /**
     * cards.dll paints the rounded corners and the black frame itself rather than storing them,
     * so do the same here: clear the corner pixels and, for faces, draw the frame in black (the
     * red suits' monochrome bitmaps would otherwise get a red frame).
     */
    private static Bitmap roundCorners(Bitmap src, boolean blackFrame) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        int w = b.getWidth(), h = b.getHeight();
        if (blackFrame) {
            for (int xx = 2; xx < w - 2; xx++) {
                b.setPixel(xx, 0, Color.BLACK);
                b.setPixel(xx, h - 1, Color.BLACK);
            }
            for (int y = 2; y < h - 2; y++) {
                b.setPixel(0, y, Color.BLACK);
                b.setPixel(w - 1, y, Color.BLACK);
            }
            b.setPixel(1, 1, Color.BLACK);
            b.setPixel(w - 2, 1, Color.BLACK);
            b.setPixel(1, h - 2, Color.BLACK);
            b.setPixel(w - 2, h - 2, Color.BLACK);
        }
        int[][] corner = {{0, 0}, {1, 0}, {0, 1}};
        for (int[] p : corner) {
            b.setPixel(p[0], p[1], Color.TRANSPARENT);
            b.setPixel(w - 1 - p[0], p[1], Color.TRANSPARENT);
            b.setPixel(p[0], h - 1 - p[1], Color.TRANSPARENT);
            b.setPixel(w - 1 - p[0], h - 1 - p[1], Color.TRANSPARENT);
        }
        return b;
    }
}
