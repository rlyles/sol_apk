package com.rlyles.wep.games;

import static com.rlyles.wep.games.ChipsEngine.SIZE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** The built-in practice levels, and a reader for the user's own CHIPS.DAT. */
public final class ChipsLevels {
    private ChipsLevels() {
    }

    /** ASCII legend for the built-in levels. */
    private static int code(char ch) {
        switch (ch) {
            case '#': return ChipsEngine.WALL;
            case 'c': return ChipsEngine.CHIP;
            case 'E': return ChipsEngine.EXIT;
            case 'S': return ChipsEngine.SOCKET;
            case '@': return 0x6E;
            case 'b': return ChipsEngine.KEY_BLUE;
            case 'r': return ChipsEngine.KEY_RED;
            case 'g': return ChipsEngine.KEY_GREEN;
            case 'y': return ChipsEngine.KEY_YELLOW;
            case 'B': return ChipsEngine.DOOR_BLUE;
            case 'R': return ChipsEngine.DOOR_RED;
            case 'G': return ChipsEngine.DOOR_GREEN;
            case 'Y': return ChipsEngine.DOOR_YELLOW;
            case '~': return ChipsEngine.WATER;
            case '^': return ChipsEngine.FIRE;
            case 'f': return ChipsEngine.FLIPPERS;
            case 'h': return ChipsEngine.FIRE_BOOTS;
            case 'k': return ChipsEngine.SKATES;
            case 's': return ChipsEngine.SUCTION;
            case 'i': return ChipsEngine.ICE;
            case '8': return ChipsEngine.FORCE_N;
            case '2': return ChipsEngine.FORCE_S;
            case '4': return ChipsEngine.FORCE_W;
            case '6': return ChipsEngine.FORCE_E;
            case '=': return ChipsEngine.BLOCK_TILE;
            case 'D': return ChipsEngine.DIRT;
            case '.': return ChipsEngine.GRAVEL;
            case '?': return ChipsEngine.HINT;
            case 'T': return ChipsEngine.TELEPORT;
            case '*': return ChipsEngine.BOMB;
            case '%': return ChipsEngine.BUTTON_GREEN;
            case '[': return ChipsEngine.TOGGLE_CLOSED;
            case ']': return ChipsEngine.TOGGLE_OPEN;
            case '$': return ChipsEngine.THIEF;
            case 'u': return 0x43; // bug, east
            case 'O': return 0x4B; // pink ball, east
            case 'w': return 0x5B; // walker, east
            case 'F': return 0x46; // fireball, south
            case 'L': return 0x53; // glider, east
            case 'm': return 0x56; // teeth, south
            case 'p': return 0x63; // paramecium, east
            default: return ChipsEngine.FLOOR;
        }
    }

    private static ChipsEngine.Level ascii(int number, String title, int time, String hint, String... rows) {
        ChipsEngine.Level lv = new ChipsEngine.Level();
        lv.number = number;
        lv.title = title;
        lv.time = time;
        lv.hint = hint;
        lv.password = "";
        java.util.Arrays.fill(lv.top, ChipsEngine.WALL);
        int h = rows.length, w = rows[0].length();
        int ox = (SIZE - w) / 2, oy = (SIZE - h) / 2;
        int chips = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                int c = code(rows[y].charAt(x));
                lv.top[(oy + y) * SIZE + ox + x] = c;
                if (c == ChipsEngine.CHIP) {
                    chips++;
                }
            }
        }
        lv.chips = chips;
        return lv;
    }

    public static List<ChipsEngine.Level> builtIn() {
        List<ChipsEngine.Level> out = new ArrayList<>();
        out.add(ascii(1, "GETTING STARTED", 100,
                "Collect the computer chips. When you have them all, the socket opens and you can reach the exit.",
                "##############",
                "#c    ?     c#",
                "#            #",
                "#     @      #",
                "#            #",
                "#c          c#",
                "######S#######",
                "#     E      #",
                "##############"));
        out.add(ascii(2, "LOCK AND KEY", 100,
                "Each key opens one door of its colour.",
                "#################",
                "#@  #   #   #   #",
                "# r R b B y Y c #",
                "# ? #   #   #   #",
                "##############S##",
                "#       E       #",
                "#################"));
        out.add(ascii(3, "WATER AND FIRE", 150,
                "Flippers let you swim. Fire boots let you walk through fire.",
                "###############",
                "#@  f ~~~ c   #",
                "#   ? ~~~     #",
                "#######~#######",
                "#c  h     ^^^c#",
                "#         ^^^ #",
                "#         ^^^ #",
                "######S########",
                "#     E       #",
                "###############"));
        out.add(ascii(4, "ON THIN ICE", 150,
                "Without skates you slide across ice until you reach solid ground.",
                "###############",
                "#@  ?        c#",
                "#iiiiiiiiiiiii#",
                "#iiiiiiiiiiiii#",
                "#c    #      c#",
                "#iiiiiiiiiiiii#",
                "#iiiiiiiiiiiii#",
                "#k     S      #",
                "#######E#######",
                "###############"));
        out.add(ascii(5, "BLOCK PARTY", 100,
                "Push a block into the water to make dry land.",
                "###############",
                "#@   =   ~ c  #",
                "# ?  =   ~    #",
                "#        ~ c  #",
                "######S########",
                "#     E       #",
                "###############"));
        out.add(ascii(6, "BUG OUT", 150,
                "Bugs follow the wall on their left. Keep out of their way!",
                "###############",
                "#@    ?       #",
                "#  #########  #",
                "#  #c     c#  #",
                "#  #  u    #  #",
                "#  ##### ###  #",
                "#             #",
                "######S########",
                "#     E       #",
                "###############"));
        out.add(ascii(7, "FORCE OF HABIT", 100,
                "Force floors push you along. Suction boots let you walk on them normally.",
                "################",
                "#@  s   ?      #",
                "#   ############",
                "#   444444444c##",
                "#   ############",
                "#              #",
                "#######S########",
                "#      E       #",
                "################"));
        out.add(ascii(8, "SHARP TEETH", 150,
                "Teeth always come straight for you, but they are slow.",
                "###############",
                "#@    c   ?   #",
                "#  #######    #",
                "#  #  m  #  c #",
                "#  #     #    #",
                "#  ### ###    #",
                "#c            #",
                "######S########",
                "#     E       #",
                "###############"));
        out.add(ascii(9, "FLIP THE SWITCH", 150,
                "The green button swaps the green walls: closed ones open and open ones close.",
                "###############",
                "#@  %   [   c #",
                "# ?     [     #",
                "#   ]   [     #",
                "#   ]####     #",
                "####c  #      #",
                "########S######",
                "#       E     #",
                "###############"));
        out.add(ascii(10, "BEAM ME UP", 150,
                "Step into a teleport and you come out of another one.",
                "###############",
                "#@  T   #  c  #",
                "#  c  ? #     #",
                "####S#####  T #",
                "#   E   #######",
                "###############"));
        return out;
    }

    // ---------------------------------------------------------------- CHIPS.DAT

    /** Reads a Microsoft-format CHIPS.DAT level pack. */
    public static List<ChipsEngine.Level> parseDat(byte[] data) throws IOException {
        try {
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int magic = bb.getInt();
            if (magic != 0x0002AAAC && magic != 0x0102AAAC) {
                throw new IOException("This is not a CHIPS.DAT level file.");
            }
            int count = bb.getShort() & 0xFFFF;
            List<ChipsEngine.Level> out = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int size = bb.getShort() & 0xFFFF;
                int end = bb.position() + size;
                ChipsEngine.Level lv = new ChipsEngine.Level();
                lv.number = bb.getShort() & 0xFFFF;
                lv.time = bb.getShort() & 0xFFFF;
                lv.chips = bb.getShort() & 0xFFFF;
                bb.getShort(); // map detail
                readLayer(bb, lv.top);
                readLayer(bb, lv.bottom);
                int fieldBytes = bb.getShort() & 0xFFFF;
                int fieldsEnd = bb.position() + fieldBytes;
                while (bb.position() < fieldsEnd) {
                    int type = bb.get() & 0xFF, len = bb.get() & 0xFF;
                    byte[] f = new byte[len];
                    bb.get(f);
                    switch (type) {
                        case 3: lv.title = cString(f, false); break;
                        case 6: lv.password = cString(f, true); break;
                        case 7: lv.hint = cString(f, false); break;
                        case 4: lv.traps = links(f, 10); break;
                        case 5: lv.clones = links(f, 8); break;
                        case 10: {
                            lv.monsters = new ArrayList<>();
                            for (int k = 0; k + 1 < len; k += 2) {
                                lv.monsters.add((f[k + 1] & 0xFF) * SIZE + (f[k] & 0xFF));
                            }
                            break;
                        }
                        default: break;
                    }
                }
                if (lv.traps == null) {
                    lv.traps = new ArrayList<>();
                }
                if (lv.clones == null) {
                    lv.clones = new ArrayList<>();
                }
                out.add(lv);
                bb.position(end);
            }
            if (out.isEmpty()) {
                throw new IOException("The level file is empty.");
            }
            return out;
        } catch (RuntimeException e) {
            throw new IOException("The level file is damaged.");
        }
    }

    private static void readLayer(ByteBuffer bb, int[] layer) {
        int bytes = bb.getShort() & 0xFFFF;
        int end = bb.position() + bytes;
        int pos = 0;
        while (bb.position() < end && pos < layer.length) {
            int b = bb.get() & 0xFF;
            if (b == 0xFF) {
                int n = bb.get() & 0xFF, tile = bb.get() & 0xFF;
                for (int k = 0; k < n && pos < layer.length; k++) {
                    layer[pos++] = tile;
                }
            } else {
                layer[pos++] = b;
            }
        }
        bb.position(end);
    }

    private static String cString(byte[] f, boolean password) {
        StringBuilder sb = new StringBuilder();
        for (byte b : f) {
            int c = b & 0xFF;
            if (password) {
                c ^= 0x99;
            }
            if (c == 0) {
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    /** Button/target pairs: each record starts with button x, y and target x, y as 16-bit values. */
    private static List<int[]> links(byte[] f, int record) {
        List<int[]> out = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN);
        for (int k = 0; k + record <= f.length; k += record) {
            bb.position(k);
            int bx = bb.getShort(), by = bb.getShort(), tx = bb.getShort(), ty = bb.getShort();
            out.add(new int[] {by * SIZE + bx, ty * SIZE + tx});
        }
        return out;
    }
}
