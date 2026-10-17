package com.rlyles.wep.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the resources out of a Windows executable or DLL: 16-bit "NE" files (Windows 3.x, which
 * is what the Entertainment Packs are) and 32/64-bit "PE" files. Nothing in the file is run.
 *
 * <p>Resources are keyed by type and then by name. Numeric types and names are decimal strings
 * ("2" is RT_BITMAP); named ones are upper case, as Windows compares them.
 */
public final class ResFile {
    public static final String BITMAP = "2", RCDATA = "10";

    public final boolean sixteenBit;
    private final Map<String, Map<String, byte[]>> types = new LinkedHashMap<>();

    private ResFile(boolean sixteenBit) {
        this.sixteenBit = sixteenBit;
    }

    public Map<String, byte[]> type(String type) {
        Map<String, byte[]> m = types.get(type);
        return m != null ? m : new LinkedHashMap<String, byte[]>();
    }

    public byte[] get(String type, String name) {
        Map<String, byte[]> m = types.get(type);
        return m == null ? null : m.get(name.toUpperCase(java.util.Locale.US));
    }

    private void put(String type, String name, byte[] data) {
        Map<String, byte[]> m = types.get(type);
        if (m == null) {
            m = new LinkedHashMap<>();
            types.put(type, m);
        }
        if (!m.containsKey(name)) {
            m.put(name, data);
        }
    }

    /** True if the bytes look like an executable this class can read. */
    public static boolean looksLikeExe(byte[] data) {
        if (data.length < 0x40 || data[0] != 'M' || data[1] != 'Z') {
            return false;
        }
        int off = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(0x3C);
        return off > 0 && off + 4 < data.length
                && ((data[off] == 'N' && data[off + 1] == 'E') || (data[off] == 'P' && data[off + 1] == 'E'));
    }

    public static ResFile parse(byte[] data) throws IOException {
        try {
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            if (data.length < 0x40 || u16(bb, 0) != 0x5A4D) {
                throw new IOException("Not a Windows program or DLL.");
            }
            int hdr = bb.getInt(0x3C);
            if (hdr <= 0 || hdr + 64 > data.length) {
                throw new IOException("Not a Windows program or DLL.");
            }
            if (u16(bb, hdr) == 0x454E) { // "NE"
                return parseNe(data, bb, hdr);
            }
            if (bb.getInt(hdr) == 0x00004550) { // "PE\0\0"
                return parsePe(data, bb, hdr);
            }
            throw new IOException("This is a DOS program, not a Windows one.");
        } catch (IndexOutOfBoundsException e) {
            throw new IOException("The file is damaged or truncated.");
        }
    }

    // ---------------------------------------------------------------- NE (16-bit)

    private static ResFile parseNe(byte[] data, ByteBuffer bb, int ne) {
        ResFile out = new ResFile(true);
        int table = ne + u16(bb, ne + 0x24);
        int shift = u16(bb, table);
        int p = table + 2;
        try {
            readNeTypes(data, bb, out, table, shift, p);
        } catch (IndexOutOfBoundsException e) {
            // A damaged or unusual table (some VBX/control DLLs): keep what was read.
        }
        return out;
    }

    private static void readNeTypes(byte[] data, ByteBuffer bb, ResFile out, int table, int shift, int p) {
        while (true) {
            int typeId = u16(bb, p);
            if (typeId == 0) {
                break;
            }
            int count = u16(bb, p + 2);
            p += 8;
            String type = (typeId & 0x8000) != 0 ? Integer.toString(typeId & 0x7FFF) : neName(data, table, typeId);
            for (int i = 0; i < count; i++, p += 12) {
                long off = (long) u16(bb, p) << shift;
                long len = (long) u16(bb, p + 2) << shift;
                int id = u16(bb, p + 6);
                if (off <= 0 || off >= data.length) {
                    continue;
                }
                len = Math.min(len, data.length - off);
                String name = (id & 0x8000) != 0 ? Integer.toString(id & 0x7FFF) : neName(data, table, id);
                byte[] res = new byte[(int) len];
                System.arraycopy(data, (int) off, res, 0, (int) len);
                out.put(type, name, res);
            }
        }
    }

    private static String neName(byte[] data, int table, int off) {
        int o = table + off;
        if (o < 0 || o >= data.length) {
            return "?";
        }
        int n = data[o] & 0xFF;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && o + 1 + i < data.length; i++) {
            sb.append((char) (data[o + 1 + i] & 0xFF));
        }
        return sb.toString().toUpperCase(java.util.Locale.US);
    }

    // ---------------------------------------------------------------- PE (32/64-bit)

    private static ResFile parsePe(byte[] data, ByteBuffer bb, int pe) throws IOException {
        ResFile out = new ResFile(false);
        int sectionCount = u16(bb, pe + 6);
        int optSize = u16(bb, pe + 20);
        int opt = pe + 24;
        int magic = u16(bb, opt);
        int dataDirs = opt + (magic == 0x20B ? 112 : 96);
        int resourceRva = bb.getInt(dataDirs + 2 * 8);
        if (resourceRva == 0) {
            return out;
        }
        int sections = opt + optSize;
        int[][] sec = new int[sectionCount][];
        for (int i = 0; i < sectionCount; i++) {
            int s = sections + i * 40;
            sec[i] = new int[] {bb.getInt(s + 12), Math.max(bb.getInt(s + 8), bb.getInt(s + 16)), bb.getInt(s + 20)};
        }
        int root = rvaToOffset(sec, resourceRva);
        int typeCount = u16(bb, root + 12) + u16(bb, root + 14);
        for (int t = 0; t < typeCount; t++) {
            int tEntry = root + 16 + t * 8;
            String type = peName(bb, root, bb.getInt(tEntry));
            int tTarget = bb.getInt(tEntry + 4);
            if ((tTarget & 0x80000000) == 0) {
                continue;
            }
            int namesDir = root + (tTarget & 0x7FFFFFFF);
            int count = u16(bb, namesDir + 12) + u16(bb, namesDir + 14);
            for (int i = 0; i < count; i++) {
                int entry = namesDir + 16 + i * 8;
                String name = peName(bb, root, bb.getInt(entry));
                int target = bb.getInt(entry + 4);
                if ((target & 0x80000000) == 0) {
                    continue;
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
                byte[] res = new byte[size];
                System.arraycopy(data, off, res, 0, size);
                out.put(type, name, res);
            }
        }
        return out;
    }

    private static String peName(ByteBuffer bb, int root, int name) {
        if ((name & 0x80000000) == 0) {
            return Integer.toString(name);
        }
        int p = root + (name & 0x7FFFFFFF);
        int n = u16(bb, p);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append((char) u16(bb, p + 2 + i * 2));
        }
        return sb.toString().toUpperCase(java.util.Locale.US);
    }

    private static int rvaToOffset(int[][] sections, int rva) throws IOException {
        for (int[] s : sections) {
            if (rva >= s[0] && rva < s[0] + s[1]) {
                return s[2] + rva - s[0];
            }
        }
        throw new IOException("The file is damaged.");
    }

    private static int u16(ByteBuffer bb, int off) {
        return bb.getShort(off) & 0xFFFF;
    }

    /**
     * Visual Basic 1.0 programs (Rodent's Revenge) keep their pictures as whole .BMP files inside
     * the form data in RCDATA resources. Returns them in the order they appear, as DIBs.
     */
    public java.util.List<byte[]> embeddedBitmaps() {
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        for (byte[] d : type(RCDATA).values()) {
            ByteBuffer bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
            for (int p = 0; p + 18 <= d.length; p++) {
                if (d[p] != 'B' || d[p + 1] != 'M') {
                    continue;
                }
                int size = bb.getInt(p + 2);
                int header = bb.getInt(p + 14);
                if ((header != 40 && header != 12) || size <= 54 || p + size > d.length) {
                    continue;
                }
                byte[] dib = new byte[size - 14];
                System.arraycopy(d, p + 14, dib, 0, dib.length);
                out.add(dib);
                p += size - 1;
            }
        }
        return out;
    }
}
