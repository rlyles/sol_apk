package com.rlyles.solitaire;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Reads the card bitmaps out of a Windows cards.dll supplied by the user.
 *
 * <p>Only the PE resource directory is parsed; nothing in the DLL is executed. Faces are bitmap
 * ids 1-52 (clubs, diamonds, hearts, spades; ace to king), card backs live in 53-66, and 67/68
 * are the "X" and "O" markers drawn on an empty stock.
 */
final class CardsDll {
    static final int CARD_W = 71, CARD_H = 96;
    private static final int RT_BITMAP = 2;

    final Bitmap[] faces = new Bitmap[52];
    final List<Bitmap> backs = new ArrayList<>();
    Bitmap x, o;

    private CardsDll() {
    }

    static CardsDll parse(byte[] data) throws IOException {
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

    // ---------------------------------------------------------------- PE resources

    private static TreeMap<Integer, byte[]> readBitmapResources(byte[] data) throws IOException {
        try {
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            if (u16(bb, 0) != 0x5A4D) {
                throw new IOException("Not a Windows DLL.");
            }
            int pe = bb.getInt(0x3C);
            if (bb.getInt(pe) != 0x00004550) {
                throw new IOException("Not a Windows DLL.");
            }
            int sectionCount = u16(bb, pe + 6);
            int optSize = u16(bb, pe + 20);
            int opt = pe + 24;
            int magic = u16(bb, opt);
            int dataDirs = opt + (magic == 0x20B ? 112 : 96);
            int resourceRva = bb.getInt(dataDirs + 2 * 8);
            if (resourceRva == 0) {
                throw new IOException("This DLL has no resources.");
            }
            int sections = opt + optSize;
            int[][] sec = new int[sectionCount][];
            for (int i = 0; i < sectionCount; i++) {
                int s = sections + i * 40;
                sec[i] = new int[] {bb.getInt(s + 12), Math.max(bb.getInt(s + 8), bb.getInt(s + 16)), bb.getInt(s + 20)};
            }
            int root = rvaToOffset(sec, resourceRva);

            TreeMap<Integer, byte[]> out = new TreeMap<>();
            int typeDir = findEntry(bb, root, RT_BITMAP);
            if (typeDir == NOT_FOUND || (typeDir & 0x80000000) == 0) {
                return out;
            }
            int namesDir = root + (typeDir & 0x7FFFFFFF);
            int count = u16(bb, namesDir + 12) + u16(bb, namesDir + 14);
            for (int i = 0; i < count; i++) {
                int entry = namesDir + 16 + i * 8;
                int name = bb.getInt(entry);
                int target = bb.getInt(entry + 4);
                if ((name & 0x80000000) != 0 || (target & 0x80000000) == 0) {
                    continue; // named resources are not card bitmaps
                }
                int langDir = root + (target & 0x7FFFFFFF);
                if (u16(bb, langDir + 12) + u16(bb, langDir + 14) == 0) {
                    continue;
                }
                int leaf = bb.getInt(langDir + 16 + 4);
                if ((leaf & 0x80000000) != 0) {
                    continue;
                }
                int dataEntry = root + leaf;
                int off = rvaToOffset(sec, bb.getInt(dataEntry));
                int size = bb.getInt(dataEntry + 4);
                if (off < 0 || size <= 0 || off + size > data.length) {
                    continue;
                }
                byte[] dib = new byte[size];
                System.arraycopy(data, off, dib, 0, size);
                out.put(name, dib);
            }
            return out;
        } catch (IndexOutOfBoundsException e) {
            throw new IOException("The DLL is damaged or truncated.");
        }
    }

    /** Subdirectory offsets have the top bit set, so "missing" can't simply be any negative number. */
    private static final int NOT_FOUND = -1;

    private static int findEntry(ByteBuffer bb, int dir, int id) {
        int count = u16(bb, dir + 12) + u16(bb, dir + 14);
        for (int i = 0; i < count; i++) {
            int entry = dir + 16 + i * 8;
            if (bb.getInt(entry) == id) {
                return bb.getInt(entry + 4);
            }
        }
        return NOT_FOUND;
    }

    private static int rvaToOffset(int[][] sections, int rva) throws IOException {
        for (int[] s : sections) {
            if (rva >= s[0] && rva < s[0] + s[1]) {
                return s[2] + rva - s[0];
            }
        }
        throw new IOException("The DLL is damaged.");
    }

    private static int u16(ByteBuffer bb, int off) {
        return bb.getShort(off) & 0xFFFF;
    }

    // ---------------------------------------------------------------- DIB decoding

    /** Decodes an uncompressed device-independent bitmap (as stored in RT_BITMAP resources). */
    static Bitmap decodeDib(byte[] d) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = bb.getInt(0);
        int w, h, bpp, compression = 0, colorsUsed = 0, paletteEntry;
        if (headerSize == 12) { // BITMAPCOREHEADER
            w = u16(bb, 4);
            h = bb.getShort(6);
            bpp = u16(bb, 10);
            paletteEntry = 3;
        } else if (headerSize >= 40) { // BITMAPINFOHEADER and later
            w = bb.getInt(4);
            h = bb.getInt(8);
            bpp = u16(bb, 14);
            compression = bb.getInt(16);
            colorsUsed = bb.getInt(32);
            paletteEntry = 4;
        } else {
            throw new IOException("Unsupported bitmap header.");
        }
        if (compression != 0 || w <= 0 || w > 1024 || h == 0 || Math.abs(h) > 1024) {
            throw new IOException("Unsupported bitmap format.");
        }
        boolean bottomUp = h > 0;
        h = Math.abs(h);
        int colors = bpp <= 8 ? (colorsUsed != 0 ? colorsUsed : 1 << bpp) : 0;
        int[] palette = new int[colors];
        for (int i = 0; i < colors; i++) {
            int p = headerSize + i * paletteEntry;
            palette[i] = Color.rgb(d[p + 2] & 0xFF, d[p + 1] & 0xFF, d[p] & 0xFF);
        }
        int pixels = headerSize + colors * paletteEntry;
        int stride = ((w * bpp + 31) / 32) * 4;
        if (pixels + stride * h > d.length) {
            throw new IOException("Truncated bitmap.");
        }
        int[] argb = new int[w * h];
        for (int row = 0; row < h; row++) {
            int y = bottomUp ? h - 1 - row : row;
            int base = pixels + row * stride;
            for (int xx = 0; xx < w; xx++) {
                int c;
                switch (bpp) {
                    case 1:
                        c = palette[(d[base + (xx >> 3)] >> (7 - (xx & 7))) & 1];
                        break;
                    case 4:
                        c = palette[(d[base + (xx >> 1)] >> ((xx & 1) == 0 ? 4 : 0)) & 0xF];
                        break;
                    case 8:
                        c = palette[d[base + xx] & 0xFF];
                        break;
                    case 24: {
                        int p = base + xx * 3;
                        c = Color.rgb(d[p + 2] & 0xFF, d[p + 1] & 0xFF, d[p] & 0xFF);
                        break;
                    }
                    case 32: {
                        int p = base + xx * 4;
                        c = Color.rgb(d[p + 2] & 0xFF, d[p + 1] & 0xFF, d[p] & 0xFF);
                        break;
                    }
                    default:
                        throw new IOException("Unsupported bit depth " + bpp + ".");
                }
                argb[y * w + xx] = c;
            }
        }
        return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
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
