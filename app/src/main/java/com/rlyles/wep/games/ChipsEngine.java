package com.rlyles.wep.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chip's Challenge rules, in the spirit of the Microsoft version. Tiles use the standard CHIPS.DAT
 * codes. The board is 32x32; the game advances in ticks of a tenth of a second.
 */
public final class ChipsEngine {
    public static final int SIZE = 32;

    // Terrain and item codes (CHIPS.DAT numbering).
    public static final int FLOOR = 0x00, WALL = 0x01, CHIP = 0x02, WATER = 0x03, FIRE = 0x04, INVISIBLE = 0x05,
            THIN_N = 0x06, THIN_W = 0x07, THIN_S = 0x08, THIN_E = 0x09, BLOCK_TILE = 0x0A, DIRT = 0x0B, ICE = 0x0C,
            FORCE_S = 0x0D, CLONE_BLOCK_N = 0x0E, FORCE_N = 0x12, FORCE_E = 0x13, FORCE_W = 0x14, EXIT = 0x15,
            DOOR_BLUE = 0x16, DOOR_RED = 0x17, DOOR_GREEN = 0x18, DOOR_YELLOW = 0x19, ICE_SE = 0x1A, ICE_SW = 0x1B,
            ICE_NW = 0x1C, ICE_NE = 0x1D, BLUE_FAKE = 0x1E, BLUE_WALL = 0x1F, THIEF = 0x21, SOCKET = 0x22,
            BUTTON_GREEN = 0x23, BUTTON_RED = 0x24, TOGGLE_CLOSED = 0x25, TOGGLE_OPEN = 0x26, BUTTON_BROWN = 0x27,
            BUTTON_BLUE = 0x28, TELEPORT = 0x29, BOMB = 0x2A, TRAP = 0x2B, HIDDEN = 0x2C, GRAVEL = 0x2D, POPUP = 0x2E,
            HINT = 0x2F, THIN_SE = 0x30, CLONER = 0x31, FORCE_RANDOM = 0x32, KEY_BLUE = 0x64, KEY_RED = 0x65,
            KEY_GREEN = 0x66, KEY_YELLOW = 0x67, FLIPPERS = 0x68, FIRE_BOOTS = 0x69, SKATES = 0x6A, SUCTION = 0x6B;

    // Creature kinds.
    public static final int BUG = 0, FIREBALL = 1, BALL = 2, TANK = 3, GLIDER = 4, TEETH = 5, WALKER = 6, BLOB = 7,
            PARAMECIUM = 8, BLOCK = 9, PLAYER = 10;

    /** Directions: north, east, south, west. */
    public static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};

    public static final class Level {
        public int number, time, chips;
        public final int[] top = new int[SIZE * SIZE], bottom = new int[SIZE * SIZE];
        public String title = "", password = "", hint = "";
        /** {button, trap} and {button, cloner} pairs; null means "connect in reading order". */
        public List<int[]> traps, clones;
        /** Positions of the creatures that move; null means all except cloner templates. */
        public List<Integer> monsters;
    }

    public static final class Creature {
        public int kind, pos, dir;
        public boolean moves = true, dead;
        int slideDir = -1;
        int wait;
    }

    public final int[] terrain = new int[SIZE * SIZE];
    public final List<Creature> creatures = new ArrayList<>();
    public Creature chip;
    public final boolean[] keys = new boolean[4];
    public final int[] keyCount = new int[4];
    public final boolean[] boots = new boolean[4];
    public int chipsLeft, timeLeft, ticks;
    public boolean won, dead;
    public String deathReason = "";
    public String hint = "";
    public boolean onHint;
    private boolean timed;
    private final List<int[]> trapLinks = new ArrayList<>(), cloneLinks = new ArrayList<>();
    private final Random rng = new Random(7);
    private int input = -1;
    private int chipCooldown;
    /** Pop-up walls Chip is standing on (they rise once he leaves). */
    private int popupUnderChip = -1;

    // ---------------------------------------------------------------- loading

    public void load(Level lv) {
        java.util.Arrays.fill(terrain, FLOOR);
        creatures.clear();
        chip = null;
        java.util.Arrays.fill(keys, false);
        java.util.Arrays.fill(keyCount, 0);
        java.util.Arrays.fill(boots, false);
        won = dead = false;
        deathReason = "";
        hint = lv.hint;
        onHint = false;
        ticks = 0;
        chipCooldown = 0;
        input = -1;
        popupUnderChip = -1;
        timed = lv.time > 0;
        timeLeft = lv.time;
        chipsLeft = lv.chips;
        for (int i = 0; i < SIZE * SIZE; i++) {
            int t = lv.top[i];
            Creature c = creatureFor(t, i);
            if (c != null) {
                creatures.add(c);
                terrain[i] = creatureFor(lv.bottom[i], i) == null ? lv.bottom[i] : FLOOR;
                if (c.kind == PLAYER) {
                    chip = c;
                }
            } else {
                terrain[i] = t;
            }
        }
        if (chip == null) {
            chip = new Creature();
            chip.kind = PLAYER;
            chip.dir = 2;
            creatures.add(chip);
        }
        creatures.remove(chip);
        trapLinks.clear();
        cloneLinks.clear();
        if (lv.traps != null) {
            trapLinks.addAll(lv.traps);
        } else {
            link(BUTTON_BROWN, TRAP, trapLinks);
        }
        if (lv.clones != null) {
            cloneLinks.addAll(lv.clones);
        } else {
            link(BUTTON_RED, CLONER, cloneLinks);
        }
        for (Creature c : creatures) {
            if (lv.monsters != null) {
                c.moves = c.kind != BLOCK && lv.monsters.contains(c.pos);
            } else {
                c.moves = c.kind != BLOCK && terrain[c.pos] != CLONER;
            }
        }
    }

    /** Lynx-style wiring: each button controls the next trap/cloner after it in reading order. */
    private void link(int button, int target, List<int[]> out) {
        for (int i = 0; i < terrain.length; i++) {
            if (terrain[i] != button) {
                continue;
            }
            for (int k = 1; k <= terrain.length; k++) {
                int j = (i + k) % terrain.length;
                if (terrain[j] == target) {
                    out.add(new int[] {i, j});
                    break;
                }
            }
        }
    }

    static Creature creatureFor(int code, int pos) {
        int kind = -1, msDir = 0;
        if (code == BLOCK_TILE) {
            kind = BLOCK;
            msDir = 2;
        } else if (code >= CLONE_BLOCK_N && code <= CLONE_BLOCK_N + 3) {
            kind = BLOCK;
            msDir = code - CLONE_BLOCK_N;
        } else if (code >= 0x40 && code < 0x64) {
            kind = (code - 0x40) / 4;
            msDir = code & 3;
        } else if (code >= 0x6C && code <= 0x6F) {
            kind = PLAYER;
            msDir = code & 3;
        }
        if (kind < 0) {
            return null;
        }
        Creature c = new Creature();
        c.kind = kind;
        c.pos = pos;
        // CHIPS.DAT orders directions N, W, S, E.
        c.dir = new int[] {0, 3, 2, 1}[msDir];
        return c;
    }

    /** A deep copy of the whole game state (used by the level-checking tests). */
    ChipsEngine copy() {
        ChipsEngine e = new ChipsEngine();
        System.arraycopy(terrain, 0, e.terrain, 0, terrain.length);
        for (Creature c : creatures) {
            e.creatures.add(copyOf(c));
        }
        e.chip = copyOf(chip);
        System.arraycopy(keys, 0, e.keys, 0, 4);
        System.arraycopy(keyCount, 0, e.keyCount, 0, 4);
        System.arraycopy(boots, 0, e.boots, 0, 4);
        e.chipsLeft = chipsLeft;
        e.timeLeft = timeLeft;
        e.ticks = ticks;
        e.won = won;
        e.dead = dead;
        e.hint = hint;
        e.timed = timed;
        e.trapLinks.addAll(trapLinks);
        e.cloneLinks.addAll(cloneLinks);
        e.chipCooldown = chipCooldown;
        e.popupUnderChip = popupUnderChip;
        return e;
    }

    private static Creature copyOf(Creature c) {
        Creature n = new Creature();
        n.kind = c.kind;
        n.pos = c.pos;
        n.dir = c.dir;
        n.moves = c.moves;
        n.dead = c.dead;
        n.slideDir = c.slideDir;
        n.wait = c.wait;
        return n;
    }

    /** A string that identifies the state for search. */
    String stateKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(chip.pos).append(',').append(chip.slideDir).append(',').append(chipsLeft).append(',')
                .append(ticks % 4);
        for (int k : keyCount) {
            sb.append(',').append(k);
        }
        for (boolean b : boots) {
            sb.append(b ? '1' : '0');
        }
        for (Creature c : creatures) {
            sb.append(';').append(c.pos).append(':').append(c.dir);
        }
        sb.append('|').append(java.util.Arrays.hashCode(terrain));
        return sb.toString();
    }

    // ---------------------------------------------------------------- queries

    public Creature creatureAt(int pos) {
        if (chip != null && chip.pos == pos && !chip.dead) {
            return chip;
        }
        for (Creature c : creatures) {
            if (c.pos == pos && !c.dead) {
                return c;
            }
        }
        return null;
    }

    private int neighbour(int pos, int dir) {
        int x = pos % SIZE + DX[dir], y = pos / SIZE + DY[dir];
        return x < 0 || y < 0 || x >= SIZE || y >= SIZE ? -1 : y * SIZE + x;
    }

    /** Whether a thin wall or ice corner on {@code pos} blocks its {@code side}. */
    private static boolean sideBlocked(int tile, int side) {
        switch (tile) {
            case THIN_N: return side == 0;
            case THIN_E: return side == 1;
            case THIN_S: return side == 2;
            case THIN_W: return side == 3;
            case THIN_SE: return side == 2 || side == 1;
            case ICE_SE: return side == 2 || side == 1;
            case ICE_SW: return side == 2 || side == 3;
            case ICE_NW: return side == 0 || side == 3;
            case ICE_NE: return side == 0 || side == 1;
            default: return false;
        }
    }

    static boolean isIce(int t) {
        return t == ICE || t >= ICE_SE && t <= ICE_NE;
    }

    static boolean isForce(int t) {
        return t == FORCE_N || t == FORCE_E || t == FORCE_S || t == FORCE_W || t == FORCE_RANDOM;
    }

    private int forceDir(int t) {
        switch (t) {
            case FORCE_N: return 0;
            case FORCE_E: return 1;
            case FORCE_S: return 2;
            case FORCE_W: return 3;
            default: return rng.nextInt(4);
        }
    }

    /** The direction something leaving an ice corner takes after entering it moving {@code dir}. */
    private static int iceTurn(int tile, int dir) {
        switch (tile) {
            case ICE_SE: return dir == 2 ? 3 : dir == 1 ? 0 : dir;
            case ICE_SW: return dir == 2 ? 1 : dir == 3 ? 0 : dir;
            case ICE_NW: return dir == 0 ? 1 : dir == 3 ? 2 : dir;
            case ICE_NE: return dir == 0 ? 3 : dir == 1 ? 2 : dir;
            default: return dir;
        }
    }

    private boolean trapOpen(int trap) {
        for (int[] l : trapLinks) {
            if (l[1] == trap && creatureAt(l[0]) != null) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- input and ticking

    /** The direction the player is holding, or -1. */
    public void setInput(int dir) {
        input = dir;
    }

    public void tick() {
        if (won || dead) {
            return;
        }
        ticks++;
        // Chip
        if (chipCooldown > 0) {
            chipCooldown--;
        }
        int under = terrain[chip.pos];
        boolean sliding = chip.slideDir >= 0;
        if (sliding) {
            boolean forced = isForce(under);
            if (forced && input >= 0 && chipCooldown == 0 && input != chip.slideDir) {
                // On a force floor Chip may try to step off sideways.
                if (move(chip, input)) {
                    chipCooldown = 2;
                    afterChipMove();
                    return;
                }
            }
            int d = chip.slideDir;
            if (!move(chip, d)) {
                if (isIce(terrain[chip.pos])) {
                    chip.slideDir = (d + 2) % 4;
                } else {
                    chip.slideDir = -1;
                }
            }
            afterChipMove();
        } else if (input >= 0 && chipCooldown == 0) {
            chip.dir = input;
            if (terrain[chip.pos] == TRAP && !trapOpen(chip.pos)) {
                // stuck
            } else {
                move(chip, input);
                chipCooldown = 2;
                afterChipMove();
            }
        }
        if (dead || won) {
            return;
        }
        // Monsters and blocks
        for (Creature c : new ArrayList<>(creatures)) {
            if (c.dead) {
                continue;
            }
            if (c.slideDir >= 0) {
                if (!move(c, c.slideDir)) {
                    if (isIce(terrain[c.pos])) {
                        c.slideDir = (c.slideDir + 2) % 4;
                    } else {
                        c.slideDir = -1;
                    }
                }
                continue;
            }
            if (!c.moves || c.kind == BLOCK) {
                continue;
            }
            int every = c.kind == TEETH || c.kind == BLOB ? 4 : 2;
            if (ticks % every != 0) {
                continue;
            }
            if (terrain[c.pos] == TRAP && !trapOpen(c.pos)) {
                continue;
            }
            moveMonster(c);
            if (dead) {
                return;
            }
        }
        for (java.util.Iterator<Creature> it = creatures.iterator(); it.hasNext(); ) {
            if (it.next().dead) {
                it.remove();
            }
        }
        if (timed && ticks % 10 == 0) {
            timeLeft--;
            if (timeLeft <= 0) {
                die("Ooops! Out of time!");
            }
        }
    }

    private void afterChipMove() {
        onHint = terrain[chip.pos] == HINT;
    }

    private void die(String why) {
        if (!dead) {
            dead = true;
            deathReason = why;
        }
    }

    // ---------------------------------------------------------------- movement

    /** Tries to move {@code c} one tile in {@code dir}; returns whether it moved. */
    boolean move(Creature c, int dir) {
        int from = c.pos;
        if (sideBlocked(terrain[from], dir)) {
            return false;
        }
        int to = neighbour(from, dir);
        if (to < 0) {
            return false;
        }
        int t = terrain[to];
        if (sideBlocked(t, (dir + 2) % 4)) {
            return false;
        }
        Creature other = creatureAt(to);
        if (c.kind == PLAYER) {
            if (other != null) {
                if (other.kind == BLOCK) {
                    if (!move(other, dir)) {
                        return false;
                    }
                } else {
                    c.pos = to;
                    die("Ooops! Look out for creatures!");
                    return true;
                }
            }
            if (!chipCanEnter(to, t)) {
                return false;
            }
        } else if (c.kind == BLOCK) {
            if (other != null) {
                if (other == chip) {
                    c.pos = to;
                    die("Ooops! Watch out for moving blocks!");
                    return true;
                }
                return false;
            }
            if (!blockCanEnter(t)) {
                return false;
            }
        } else {
            if (other != null) {
                if (other == chip) {
                    c.pos = to;
                    c.dir = dir;
                    die("Ooops! Look out for creatures!");
                    return true;
                }
                return false;
            }
            if (!monsterCanEnter(c, t)) {
                return false;
            }
        }
        // Leaving a tile
        if (c == chip && popupUnderChip >= 0) {
            terrain[popupUnderChip] = WALL;
            popupUnderChip = -1;
        }
        c.pos = to;
        c.dir = dir;
        enter(c, to, dir);
        return true;
    }

    private boolean chipCanEnter(int to, int t) {
        switch (t) {
            case WALL:
            case INVISIBLE:
            case BLUE_WALL:
            case TOGGLE_CLOSED:
            case CLONER:
                return false;
            case HIDDEN:
                terrain[to] = WALL;
                return false;
            case BLUE_FAKE:
                terrain[to] = FLOOR;
                return true;
            case SOCKET:
                if (chipsLeft > 0) {
                    return false;
                }
                terrain[to] = FLOOR;
                return true;
            case DOOR_BLUE:
            case DOOR_RED:
            case DOOR_GREEN:
            case DOOR_YELLOW: {
                int k = t - DOOR_BLUE;
                if (keyCount[k] <= 0) {
                    return false;
                }
                if (t != DOOR_GREEN) {
                    keyCount[k]--;
                }
                keys[k] = keyCount[k] > 0;
                terrain[to] = FLOOR;
                return true;
            }
            default:
                return true;
        }
    }

    private static boolean blockCanEnter(int t) {
        switch (t) {
            case FLOOR:
            case WATER:
            case BOMB:
            case ICE:
            case ICE_SE:
            case ICE_SW:
            case ICE_NW:
            case ICE_NE:
            case FORCE_N:
            case FORCE_E:
            case FORCE_S:
            case FORCE_W:
            case FORCE_RANDOM:
            case BUTTON_GREEN:
            case BUTTON_RED:
            case BUTTON_BROWN:
            case BUTTON_BLUE:
            case TRAP:
            case TELEPORT:
            case TOGGLE_OPEN:
            case THIN_N:
            case THIN_E:
            case THIN_S:
            case THIN_W:
            case THIN_SE:
                return true;
            default:
                return false;
        }
    }

    private static boolean monsterCanEnter(Creature c, int t) {
        switch (t) {
            case WATER:
                return c.kind == GLIDER;
            case FIRE:
                return c.kind == FIREBALL;
            case FLOOR:
            case ICE:
            case ICE_SE:
            case ICE_SW:
            case ICE_NW:
            case ICE_NE:
            case FORCE_N:
            case FORCE_E:
            case FORCE_S:
            case FORCE_W:
            case FORCE_RANDOM:
            case BUTTON_GREEN:
            case BUTTON_RED:
            case BUTTON_BROWN:
            case BUTTON_BLUE:
            case TRAP:
            case TELEPORT:
            case BOMB:
            case TOGGLE_OPEN:
            case THIN_N:
            case THIN_E:
            case THIN_S:
            case THIN_W:
            case THIN_SE:
                return true;
            default:
                return false;
        }
    }

    private void enter(Creature c, int pos, int dir) {
        int t = terrain[pos];
        c.slideDir = -1;
        if (c == chip) {
            enterChip(pos, t, dir);
        } else if (c.kind == BLOCK) {
            if (t == WATER) {
                terrain[pos] = DIRT;
                c.dead = true;
                return;
            }
            if (t == BOMB) {
                terrain[pos] = FLOOR;
                c.dead = true;
                return;
            }
        } else if (t == BOMB) {
            terrain[pos] = FLOOR;
            c.dead = true;
            return;
        }
        if (c.dead || dead) {
            return;
        }
        pressButton(t, c);
        boolean skates = c == chip && boots[2], suction = c == chip && boots[3];
        if (isIce(t) && !skates) {
            c.slideDir = iceTurn(t, dir);
        } else if (isForce(t) && !suction) {
            c.slideDir = forceDir(t);
        } else if (t == TELEPORT) {
            teleport(c, dir);
        }
    }

    private void enterChip(int pos, int t, int dir) {
        switch (t) {
            case WATER:
                if (!boots[0]) {
                    die("Ooops! Chip can't swim without flippers!");
                }
                break;
            case FIRE:
                if (!boots[1]) {
                    die("Ooops! Don't step in the fire without fire boots!");
                }
                break;
            case BOMB:
                terrain[pos] = FLOOR;
                die("Ooops! Don't touch the bombs!");
                break;
            case DIRT:
                terrain[pos] = FLOOR;
                break;
            case CHIP:
                terrain[pos] = FLOOR;
                chipsLeft = Math.max(0, chipsLeft - 1);
                break;
            case KEY_BLUE:
            case KEY_RED:
            case KEY_GREEN:
            case KEY_YELLOW:
                keyCount[t - KEY_BLUE]++;
                keys[t - KEY_BLUE] = true;
                terrain[pos] = FLOOR;
                break;
            case FLIPPERS:
            case FIRE_BOOTS:
            case SKATES:
            case SUCTION:
                boots[t - FLIPPERS] = true;
                terrain[pos] = FLOOR;
                break;
            case THIEF:
                java.util.Arrays.fill(boots, false);
                break;
            case EXIT:
                won = true;
                break;
            case POPUP:
                popupUnderChip = pos;
                break;
            default:
                break;
        }
    }

    private void pressButton(int t, Creature by) {
        switch (t) {
            case BUTTON_GREEN:
                for (int i = 0; i < terrain.length; i++) {
                    if (terrain[i] == TOGGLE_CLOSED) {
                        terrain[i] = TOGGLE_OPEN;
                    } else if (terrain[i] == TOGGLE_OPEN) {
                        terrain[i] = TOGGLE_CLOSED;
                    }
                }
                break;
            case BUTTON_BLUE:
                for (Creature c : creatures) {
                    if (c.kind == TANK) {
                        c.dir = (c.dir + 2) % 4;
                    }
                }
                break;
            case BUTTON_RED:
                for (int[] l : cloneLinks) {
                    if (l[0] == by.pos) {
                        cloneFrom(l[1]);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void cloneFrom(int machine) {
        Creature template = null;
        for (Creature c : creatures) {
            if (c.pos == machine && !c.dead) {
                template = c;
            }
        }
        if (template == null) {
            return;
        }
        int to = neighbour(machine, template.dir);
        if (to < 0 || creatureAt(to) != null) {
            return;
        }
        Creature copy = new Creature();
        copy.kind = template.kind;
        copy.dir = template.dir;
        copy.pos = machine;
        copy.moves = template.kind != BLOCK;
        creatures.add(copy);
        if (!move(copy, copy.dir)) {
            creatures.remove(copy);
        }
    }

    private void teleport(Creature c, int dir) {
        List<Integer> ports = new ArrayList<>();
        for (int i = 0; i < terrain.length; i++) {
            if (terrain[i] == TELEPORT) {
                ports.add(i);
            }
        }
        int me = ports.indexOf(c.pos);
        for (int k = 1; k <= ports.size(); k++) {
            int p = ports.get(((me - k) % ports.size() + ports.size()) % ports.size());
            int out = neighbour(p, dir);
            if (out >= 0 && creatureAt(p) == null && creatureAt(out) == null && terrain[out] != WALL) {
                c.pos = p;
                c.slideDir = dir;
                return;
            }
        }
        c.slideDir = dir;
    }

    private void moveMonster(Creature c) {
        int f = c.dir, l = (f + 3) % 4, r = (f + 1) % 4, b = (f + 2) % 4;
        int[] order;
        switch (c.kind) {
            case BUG: order = new int[] {l, f, r, b}; break;
            case PARAMECIUM: order = new int[] {r, f, l, b}; break;
            case FIREBALL: order = new int[] {f, r, l, b}; break;
            case GLIDER: order = new int[] {f, l, r, b}; break;
            case BALL: order = new int[] {f, b}; break;
            case TANK: order = new int[] {f}; break;
            case WALKER: order = new int[] {f, rng.nextInt(4), rng.nextInt(4), rng.nextInt(4)}; break;
            case BLOB: order = new int[] {rng.nextInt(4), rng.nextInt(4), rng.nextInt(4), rng.nextInt(4)}; break;
            case TEETH: {
                int dx = chip.pos % SIZE - c.pos % SIZE, dy = chip.pos / SIZE - c.pos / SIZE;
                int h = dx > 0 ? 1 : 3, v = dy > 0 ? 2 : 0;
                order = Math.abs(dx) > Math.abs(dy) ? new int[] {h, v} : new int[] {v, h};
                if (dx == 0) {
                    order = new int[] {v};
                } else if (dy == 0) {
                    order = new int[] {h};
                }
                break;
            }
            default:
                return;
        }
        for (int d : order) {
            if (move(c, d)) {
                return;
            }
        }
        if (c.kind == TEETH && order.length > 0) {
            c.dir = order[0];
        }
    }

    public boolean timed() {
        return timed;
    }
}
