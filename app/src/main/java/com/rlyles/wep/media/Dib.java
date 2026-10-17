package com.rlyles.wep.media;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes the device-independent bitmaps stored in RT_BITMAP resources, including RLE ones. */
public final class Dib {
    private Dib() {
    }

    /** The pixels of a DIB as ARGB, with the width and height in {@code size[0..1]}. */
    public static int[] pixels(byte[] d, int[] size) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = bb.getInt(0);
        int w, h, bpp, compression = 0, colorsUsed = 0, paletteEntry;
        if (headerSize == 12) { // BITMAPCOREHEADER
            w = bb.getShort(4) & 0xFFFF;
            h = bb.getShort(6);
            bpp = bb.getShort(10) & 0xFFFF;
            paletteEntry = 3;
        } else if (headerSize >= 40) { // BITMAPINFOHEADER and later
            w = bb.getInt(4);
            h = bb.getInt(8);
            bpp = bb.getShort(14) & 0xFFFF;
            compression = bb.getInt(16);
            colorsUsed = bb.getInt(32);
            paletteEntry = 4;
        } else {
            throw new IOException("Unsupported bitmap header.");
        }
        if (w <= 0 || w > 2048 || h == 0 || Math.abs(h) > 2048) {
            throw new IOException("Unsupported bitmap size.");
        }
        boolean rle = compression == 1 && bpp == 8 || compression == 2 && bpp == 4;
        if (compression != 0 && !rle) {
            throw new IOException("Unsupported bitmap compression.");
        }
        boolean bottomUp = h > 0;
        h = Math.abs(h);
        int colors = bpp <= 8 ? (colorsUsed > 0 && colorsUsed <= 256 ? colorsUsed : 1 << bpp) : 0;
        int[] palette = new int[1 << Math.min(bpp, 8)];
        for (int i = 0; i < colors; i++) {
            int p = headerSize + i * paletteEntry;
            palette[i] = Color.rgb(d[p + 2] & 0xFF, d[p + 1] & 0xFF, d[p] & 0xFF);
        }
        int pixels = headerSize + colors * paletteEntry;
        int stride = ((w * bpp + 31) / 32) * 4;
        byte[] src = d;
        if (rle) {
            src = unRle(d, pixels, w, h, stride, bpp == 4);
            pixels = 0;
        } else if (pixels + stride * h > d.length) {
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
                        c = palette[(src[base + (xx >> 3)] >> (7 - (xx & 7))) & 1];
                        break;
                    case 4:
                        c = palette[(src[base + (xx >> 1)] >> ((xx & 1) == 0 ? 4 : 0)) & 0xF];
                        break;
                    case 8:
                        c = palette[src[base + xx] & 0xFF];
                        break;
                    case 24: {
                        int p = base + xx * 3;
                        c = Color.rgb(src[p + 2] & 0xFF, src[p + 1] & 0xFF, src[p] & 0xFF);
                        break;
                    }
                    case 32: {
                        int p = base + xx * 4;
                        c = Color.rgb(src[p + 2] & 0xFF, src[p + 1] & 0xFF, src[p] & 0xFF);
                        break;
                    }
                    default:
                        throw new IOException("Unsupported bit depth " + bpp + ".");
                }
                argb[y * w + xx] = c;
            }
        }
        size[0] = w;
        size[1] = h;
        return argb;
    }

    public static Bitmap decode(byte[] d) throws IOException {
        int[] size = new int[2];
        int[] argb = pixels(d, size);
        return Bitmap.createBitmap(argb, size[0], size[1], Bitmap.Config.ARGB_8888);
    }

    /** Expands BI_RLE8 / BI_RLE4 data into ordinary bottom-up rows. */
    private static byte[] unRle(byte[] d, int at, int w, int h, int stride, boolean four) {
        byte[] out = new byte[stride * h];
        int x = 0, y = 0, i = at;
        while (i + 1 < d.length && y < h) {
            int n = d[i] & 0xFF, v = d[i + 1] & 0xFF;
            i += 2;
            if (n > 0) {
                for (int k = 0; k < n; k++, x++) {
                    put(out, x, y, w, h, stride, four, four ? ((k & 1) == 0 ? v >> 4 : v & 15) : v);
                }
            } else if (v == 0) {
                x = 0;
                y++;
            } else if (v == 1) {
                break;
            } else if (v == 2) {
                if (i + 1 >= d.length) {
                    break;
                }
                x += d[i] & 0xFF;
                y += d[i + 1] & 0xFF;
                i += 2;
            } else {
                for (int k = 0; k < v; k++, x++) {
                    int idx = i + (four ? k >> 1 : k);
                    if (idx >= d.length) {
                        break;
                    }
                    int b = d[idx] & 0xFF;
                    put(out, x, y, w, h, stride, four, four ? ((k & 1) == 0 ? b >> 4 : b & 15) : b);
                }
                int bytes = four ? (v + 1) / 2 : v;
                i += bytes + (bytes & 1);
            }
        }
        return out;
    }

    private static void put(byte[] out, int x, int y, int w, int h, int stride, boolean four, int v) {
        if (x >= w || y >= h) {
            return;
        }
        int o = y * stride;
        if (four) {
            int b = out[o + (x >> 1)] & 0xFF;
            b = (x & 1) == 0 ? (b & 0x0F) | (v << 4) : (b & 0xF0) | v;
            out[o + (x >> 1)] = (byte) b;
        } else {
            out[o + x] = (byte) v;
        }
    }
}
