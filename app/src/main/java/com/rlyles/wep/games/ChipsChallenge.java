package com.rlyles.wep.games;

import static com.rlyles.wep.games.ChipsEngine.SIZE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Chip's Challenge: collect the chips, open the socket and reach the exit. Ten practice levels are
 * built in; load your own CHIPS.DAT from the Level menu for the original 149.
 */
@SuppressLint("ViewConstructor")
public final class ChipsChallenge extends WinApp {
    private static final int VIEW = 9;
    private static final float TICK = 0.1f;

    final ChipsEngine engine = new ChipsEngine();
    List<ChipsEngine.Level> levels;
    int index;
    private boolean usingDat;
    boolean paused, finished;
    private float acc;
    private int unlocked;

    private float tile;
    private final RectF viewRect = new RectF(), panel = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean holding;
    private float holdX, holdY;

    public ChipsChallenge(Context context, Host host) {
        super(context, host, "chips");
        loadLevelSet();
        index = Math.min(prefs.getInt(key("level"), 0), levels.size() - 1);
        unlocked = prefs.getInt(key("unlocked"), 0);
        startLevel(index);
        setAnimating(true);
    }

    private String key(String k) {
        return (usingDat ? "dat_" : "") + k;
    }

    private File datFile() {
        return new File(getContext().getFilesDir(), "chips.dat");
    }

    private void loadLevelSet() {
        usingDat = false;
        levels = ChipsLevels.builtIn();
        File f = datFile();
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] data = new byte[(int) f.length()];
                int n = 0;
                while (n < data.length) {
                    int r = in.read(data, n, data.length - n);
                    if (r < 0) {
                        break;
                    }
                    n += r;
                }
                levels = ChipsLevels.parseDat(data);
                usingDat = true;
            } catch (IOException e) {
                levels = ChipsLevels.builtIn();
            }
        }
    }

    @Override
    public String title() {
        return "Chip's Challenge";
    }

    @Override
    public void save() {
        prefs.edit().putInt(key("level"), index).putInt(key("unlocked"), unlocked).apply();
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New Game", "F2", "Start again at level 1", true, false, () -> startLevel(0)));
            items.add(new Item(paused ? "&Resume" : "&Pause", "F3", "Pause the game", true, false, () -> paused = !paused));
            items.add(new Item("Best &Times...", "", "Your best times", true, false, this::bestTimes));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit Chip's Challenge", true, false, () -> host.closeApp(this)));
        }));
        menus.add(new Menu("&Level", items -> {
            items.add(new Item("&Restart", "Ctrl+R", "Start this level again", true, false, () -> startLevel(index)));
            items.add(new Item("&Next", "Ctrl+N", "Go to the next level", index < unlocked && index + 1 < levels.size(),
                    false, () -> startLevel(index + 1)));
            items.add(new Item("&Previous", "Ctrl+P", "Go to the previous level", index > 0, false,
                    () -> startLevel(index - 1)));
            items.add(new Item("&Go To...", "", "Jump to a level", true, false, this::goTo));
            items.add(Item.sep());
            items.add(new Item("&Load CHIPS.DAT...", "", "Play your own copy of the original levels", true, false,
                    this::loadDat));
            items.add(new Item("&Built-in Levels", "", "Play the practice levels", usingDat, false, () -> {
                //noinspection ResultOfMethodCallIgnored
                datFile().delete();
                loadLevelSet();
                unlocked = prefs.getInt(key("unlocked"), 0);
                startLevel(0);
            }));
        }));
        addHelpMenu(menus, "Guide Chip through each level. Pick up all the computer chips so the socket opens, "
                + "then step on the exit.\n\nHold your finger above, below or beside Chip to walk that way.\n\n"
                + "Keys open doors of their own colour. Flippers let Chip swim, fire boots let him walk through fire, "
                + "skates stop him sliding on ice, and suction boots hold him on force floors. A thief takes your "
                + "boots. Blocks can be pushed, and turn water into dirt. Bombs are deadly; push a block onto one "
                + "to clear it. Teleports send you to another teleport.\n\nButtons: green swaps the green walls, "
                + "blue turns tanks around, brown opens traps and red works a clone machine.\n\nStepping on a hint "
                + "(the question mark) shows a tip.\n\nTen practice levels are built in. If you have the original "
                + "CHIPS.DAT, choose Level > Load CHIPS.DAT to play those levels.",
                "Chip's Challenge for the Entertainment Pack.");
    }

    @Override
    protected boolean hasStatusBar() {
        return true;
    }

    @Override
    protected String statusText() {
        ChipsEngine.Level lv = levels.get(index);
        return paused ? "Paused" : lv.title + (lv.password.isEmpty() ? "" : "  Password: " + lv.password);
    }

    // ---------------------------------------------------------------- levels

    void startLevel(int i) {
        index = Math.max(0, Math.min(levels.size() - 1, i));
        engine.load(levels.get(index));
        paused = finished = false;
        acc = 0;
        save();
        invalidate();
    }

    private void goTo() {
        prompt("Go To Level", "Level number (1 to " + levels.size() + "):", String.valueOf(index + 1), true, 3, t -> {
            if (t.isEmpty()) {
                return;
            }
            int n = Math.max(1, Math.min(levels.size(), Integer.parseInt(t))) - 1;
            ChipsEngine.Level lv = levels.get(n);
            if (n <= unlocked || lv.password.isEmpty()) {
                startLevel(n);
                return;
            }
            prompt("Password", "Password for level " + (n + 1) + ":", "", false, 8, pw -> {
                if (pw.equalsIgnoreCase(lv.password)) {
                    unlocked = Math.max(unlocked, n);
                    startLevel(n);
                } else {
                    messageBox("Chip's Challenge", "That is not the correct password.", null);
                }
            });
        });
    }

    private void loadDat() {
        host.pickFile(data -> {
            try {
                ChipsLevels.parseDat(data);
                try (FileOutputStream out = new FileOutputStream(datFile());
                     ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                loadLevelSet();
                unlocked = prefs.getInt(key("unlocked"), 0);
                startLevel(Math.min(prefs.getInt(key("level"), 0), levels.size() - 1));
                messageBox("Chip's Challenge", levels.size() + " levels loaded.", null);
            } catch (IOException e) {
                messageBox("Chip's Challenge", "Unable to load levels from that file.\n\n" + e.getMessage(), null);
            }
        });
    }

    private void bestTimes() {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < levels.size() && shown < 12; i++) {
            int t = prefs.getInt(key("time" + i), -1);
            if (t >= 0) {
                sb.append("Level ").append(i + 1).append(":  ").append(t).append(" seconds\n");
                shown++;
            }
        }
        messageBox("Best Times", shown == 0 ? "You have not finished any levels yet." : sb.toString().trim(), null);
    }

    @Override
    protected void frame(float dt) {
        if (paused || finished) {
            return;
        }
        acc += dt;
        while (acc >= TICK) {
            acc -= TICK;
            int chipsBefore = engine.chipsLeft;
            engine.setInput(holding ? directionTo(holdX, holdY) : -1);
            engine.tick();
            if (engine.chipsLeft < chipsBefore) {
                sound(Sfx.BLIP);
            }
            if (engine.dead) {
                finished = true;
                sound(Sfx.BOOM);
                post(() -> messageBox("Chip's Challenge", engine.deathReason, () -> startLevel(index)));
                return;
            }
            if (engine.won) {
                finished = true;
                sound(Sfx.WIN);
                int time = engine.timed() ? engine.timeLeft : 0;
                int best = prefs.getInt(key("time" + index), -1);
                if (engine.timed() && time > best) {
                    prefs.edit().putInt(key("time" + index), time).apply();
                }
                unlocked = Math.max(unlocked, index + 1);
                save();
                boolean last = index + 1 >= levels.size();
                post(() -> messageBox("Chip's Challenge", last ? "Great job! You have finished every level!"
                        : "Yowser! You made it!\n\n" + (engine.timed() ? "Time bonus: " + time * 10 + "\n" : "")
                        + "On to level " + (index + 2) + ".", () -> startLevel(last ? index : index + 1)));
                return;
            }
        }
    }

    private int directionTo(float x, float y) {
        float[] c = tileCentre(engine.chip.pos);
        float dx = x - c[0], dy = y - c[1];
        if (Math.abs(dx) < tile * 0.5f && Math.abs(dy) < tile * 0.5f) {
            return -1;
        }
        return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0);
    }

    @Override
    protected void tick() {
        invalidate();
    }

    @Override
    protected void onHidden() {
        paused = true;
        holding = false;
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void layoutClient(RectF r) {
        float pad = 8 * dp;
        boolean wide = r.width() >= r.height();
        if (wide) {
            tile = Math.min((r.height() - 2 * pad) / VIEW, (r.width() - 3 * pad) / (VIEW + 4.6f));
            float total = VIEW * tile + pad + 4.6f * tile;
            float left = r.centerX() - total / 2, top = r.centerY() - VIEW * tile / 2;
            viewRect.set(left, top, left + VIEW * tile, top + VIEW * tile);
            panel.set(viewRect.right + pad, top, viewRect.right + pad + 4.6f * tile, viewRect.bottom);
        } else {
            tile = Math.min((r.width() - 2 * pad) / VIEW, (r.height() - 3 * pad) / (VIEW + 3.2f));
            float left = r.centerX() - VIEW * tile / 2, top = r.top + pad;
            viewRect.set(left, top, left + VIEW * tile, top + VIEW * tile);
            panel.set(left, viewRect.bottom + pad, viewRect.right, viewRect.bottom + pad + 3.2f * tile);
        }
    }

    private int viewX0() {
        return Math.max(0, Math.min(SIZE - VIEW, engine.chip.pos % SIZE - VIEW / 2));
    }

    private int viewY0() {
        return Math.max(0, Math.min(SIZE - VIEW, engine.chip.pos / SIZE - VIEW / 2));
    }

    private float[] tileCentre(int pos) {
        return new float[] {viewRect.left + (pos % SIZE - viewX0() + 0.5f) * tile,
                viewRect.top + (pos / SIZE - viewY0() + 0.5f) * tile};
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(0xFF008000);
        ui.sunken(c, new RectF(viewRect.left - 3 * dp, viewRect.top - 3 * dp, viewRect.right + 3 * dp,
                viewRect.bottom + 3 * dp), 0xFF000000);
        int x0 = viewX0(), y0 = viewY0();
        c.save();
        c.clipRect(viewRect);
        for (int vy = 0; vy < VIEW; vy++) {
            for (int vx = 0; vx < VIEW; vx++) {
                int pos = (y0 + vy) * SIZE + x0 + vx;
                float x = viewRect.left + vx * tile, y = viewRect.top + vy * tile;
                drawTerrain(c, engine.terrain[pos], x, y, tile);
            }
        }
        for (ChipsEngine.Creature cr : engine.creatures) {
            int cx = cr.pos % SIZE - x0, cy = cr.pos / SIZE - y0;
            if (cx >= 0 && cy >= 0 && cx < VIEW && cy < VIEW) {
                drawCreature(c, cr.kind, cr.dir, viewRect.left + cx * tile, viewRect.top + cy * tile, tile);
            }
        }
        int px = engine.chip.pos % SIZE - x0, py = engine.chip.pos / SIZE - y0;
        drawChip(c, engine.chip.dir, viewRect.left + px * tile, viewRect.top + py * tile, tile,
                engine.terrain[engine.chip.pos] == ChipsEngine.WATER);
        if (paused) {
            paint.setColor(0xFF000000);
            c.drawRect(viewRect, paint);
            ui.withFont(20, true, () -> ui.centered(c, "PAUSED", viewRect, 0xFFFFFFFF));
        }
        c.restore();
        drawPanel(c);
    }

    private void drawPanel(Canvas c) {
        ui.raised(c, panel);
        float pad = 6 * dp;
        boolean wide = panel.height() > panel.width();
        String[] labels = {"LEVEL", "TIME", "CHIPS LEFT"};
        int[] values = {levels.get(index).number, engine.timed() ? engine.timeLeft : -1, engine.chipsLeft};
        float ledH = Math.min(tile * 0.7f, 34 * dp), ledW = ledH * 1.8f;
        if (engine.onHint && !engine.hint.isEmpty()) {
            RectF box = new RectF(panel.left + pad, panel.top + pad, panel.right - pad, panel.bottom - pad);
            ui.sunken(c, box, 0xFF000000);
            ui.withFont(13, true, () -> {
                ui.text.setColor(0xFF00FFFF);
                float lh = ui.text.getFontSpacing(), y = box.top + lh;
                for (String line : Win95.Text.wrap(ui.text, "Hint: " + engine.hint, box.width() - 2 * pad)) {
                    c.drawText(line, box.left + pad, y, ui.text);
                    y += lh;
                }
            });
            return;
        }
        for (int i = 0; i < 3; i++) {
            float lx, ly;
            if (wide) {
                lx = panel.left + pad;
                ly = panel.top + pad + i * (ledH + 26 * dp);
            } else {
                lx = panel.left + pad + i * (panel.width() / 3);
                ly = panel.top + pad;
            }
            float fx = lx, fy = ly;
            String label = labels[i];
            ui.withFont(11, true, () -> ui.label(c, label, fx, fy + 12 * dp, 0xFF000080));
            RectF led = new RectF(lx, ly + 16 * dp, lx + ledW, ly + 16 * dp + ledH);
            if (values[i] < 0) {
                paint.setColor(0xFF000000);
                c.drawRect(led, paint);
                ui.withFont(13, true, () -> ui.centered(c, "---", led, 0xFFFF0000));
            } else {
                ui.led(c, led, values[i], 3);
            }
        }
        // Inventory: four keys over four boots.
        float slot = wide ? (panel.width() - 2 * pad) / 4 : Math.min((panel.width() / 3 - pad), (panel.height() - 2 * pad) / 2);
        float ix = wide ? panel.left + pad : panel.left + pad + 2 * (panel.width() / 3);
        float iy = wide ? panel.bottom - pad - 2 * slot : panel.top + pad;
        if (!wide) {
            slot = Math.min(slot, (panel.right - pad - ix) / 4);
        }
        for (int i = 0; i < 8; i++) {
            float x = ix + (i % 4) * slot, y = iy + (i / 4) * slot;
            ui.sunken(c, new RectF(x + 1, y + 1, x + slot - 1, y + slot - 1), 0xFFC0C0C0);
            boolean have = i < 4 ? engine.keyCount[i] > 0 : engine.boots[i - 4];
            if (have) {
                drawTerrain(c, i < 4 ? ChipsEngine.KEY_BLUE + i : ChipsEngine.FLIPPERS + i - 4, x, y, slot);
            }
        }
    }

    // ---------------------------------------------------------------- tile art (original drawings)

    private void drawTerrain(Canvas c, int t, float x, float y, float s) {
        RectF r = new RectF(x, y, x + s, y + s);
        paint.setStyle(Paint.Style.FILL);
        switch (t) {
            case ChipsEngine.WALL:
            case ChipsEngine.BLUE_WALL:
            case ChipsEngine.BLUE_FAKE:
            case ChipsEngine.TOGGLE_CLOSED:
                floor(c, r);
                int base = t == ChipsEngine.WALL ? 0xFF808080 : t == ChipsEngine.TOGGLE_CLOSED ? 0xFF00A000 : 0xFF2040E0;
                paint.setColor(base);
                c.drawRect(r, paint);
                paint.setColor(0x60FFFFFF);
                c.drawRect(x, y, x + s, y + s * 0.12f, paint);
                c.drawRect(x, y, x + s * 0.12f, y + s, paint);
                paint.setColor(0x60000000);
                c.drawRect(x, y + s * 0.88f, x + s, y + s, paint);
                c.drawRect(x + s * 0.88f, y, x + s, y + s, paint);
                return;
            case ChipsEngine.TOGGLE_OPEN:
                floor(c, r);
                outline(c, r, 0xFF00A000, s * 0.08f);
                return;
            case ChipsEngine.WATER:
                paint.setColor(0xFF0040FF);
                c.drawRect(r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.06f);
                paint.setColor(0xFF80C0FF);
                for (int i = 0; i < 3; i++) {
                    float wy = y + s * (0.25f + i * 0.25f);
                    c.drawArc(new RectF(x + s * 0.1f, wy - s * 0.08f, x + s * 0.5f, wy + s * 0.08f), 180, 180, false, paint);
                    c.drawArc(new RectF(x + s * 0.5f, wy - s * 0.08f, x + s * 0.9f, wy + s * 0.08f), 0, 180, false, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                return;
            case ChipsEngine.FIRE:
                paint.setColor(0xFF800000);
                c.drawRect(r, paint);
                flame(c, x + s * 0.3f, y + s * 0.85f, s * 0.35f);
                flame(c, x + s * 0.68f, y + s * 0.9f, s * 0.45f);
                return;
            case ChipsEngine.ICE:
            case ChipsEngine.ICE_NE:
            case ChipsEngine.ICE_NW:
            case ChipsEngine.ICE_SE:
            case ChipsEngine.ICE_SW:
                paint.setColor(0xFFE0F0FF);
                c.drawRect(r, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawRect(x + s * 0.15f, y + s * 0.15f, x + s * 0.35f, y + s * 0.2f, paint);
                if (t != ChipsEngine.ICE) {
                    paint.setColor(0xFF808080);
                    boolean n = t == ChipsEngine.ICE_NE || t == ChipsEngine.ICE_NW;
                    boolean e = t == ChipsEngine.ICE_NE || t == ChipsEngine.ICE_SE;
                    c.drawRect(x, n ? y : y + s * 0.85f, x + s, n ? y + s * 0.15f : y + s, paint);
                    c.drawRect(e ? x + s * 0.85f : x, y, e ? x + s : x + s * 0.15f, y + s, paint);
                }
                return;
            case ChipsEngine.FORCE_N:
            case ChipsEngine.FORCE_E:
            case ChipsEngine.FORCE_S:
            case ChipsEngine.FORCE_W:
            case ChipsEngine.FORCE_RANDOM: {
                paint.setColor(0xFF606060);
                c.drawRect(r, paint);
                int d = t == ChipsEngine.FORCE_N ? 0 : t == ChipsEngine.FORCE_E ? 1 : t == ChipsEngine.FORCE_S ? 2
                        : t == ChipsEngine.FORCE_W ? 3 : -1;
                paint.setColor(0xFF20E020);
                if (d < 0) {
                    for (int k = 0; k < 4; k++) {
                        ui.arrow(c, x + s / 2 + ChipsEngine.DX[k] * s * 0.25f, y + s / 2 + ChipsEngine.DY[k] * s * 0.25f,
                                s * 0.25f, k == 0 ? 0 : k == 1 ? 1 : k == 2 ? 2 : 3, 0xFF20E020);
                    }
                } else {
                    int arrowDir = d == 0 ? 0 : d == 1 ? 1 : d == 2 ? 2 : 3;
                    ui.arrow(c, x + s / 2 - ChipsEngine.DX[d] * s * 0.2f, y + s / 2 - ChipsEngine.DY[d] * s * 0.2f, s * 0.4f, arrowDir, 0xFF20E020);
                    ui.arrow(c, x + s / 2 + ChipsEngine.DX[d] * s * 0.2f, y + s / 2 + ChipsEngine.DY[d] * s * 0.2f, s * 0.4f, arrowDir, 0xFF20E020);
                }
                return;
            }
            case ChipsEngine.DIRT:
                paint.setColor(0xFF806030);
                c.drawRect(r, paint);
                return;
            case ChipsEngine.GRAVEL:
                paint.setColor(0xFFA0A0A0);
                c.drawRect(r, paint);
                paint.setColor(0xFF606060);
                for (int i = 0; i < 9; i++) {
                    c.drawCircle(x + s * (0.2f + (i % 3) * 0.3f), y + s * (0.2f + (i / 3) * 0.3f), s * 0.06f, paint);
                }
                return;
            default:
                break;
        }
        floor(c, r);
        switch (t) {
            case ChipsEngine.CHIP:
                paint.setColor(0xFF202020);
                c.drawRect(x + s * 0.22f, y + s * 0.22f, x + s * 0.78f, y + s * 0.78f, paint);
                paint.setColor(0xFFC0C0C0);
                for (int i = 0; i < 4; i++) {
                    float p = s * (0.28f + i * 0.14f);
                    c.drawRect(x + p, y + s * 0.12f, x + p + s * 0.06f, y + s * 0.22f, paint);
                    c.drawRect(x + p, y + s * 0.78f, x + p + s * 0.06f, y + s * 0.88f, paint);
                }
                paint.setColor(0xFFFFD000);
                c.drawRect(x + s * 0.35f, y + s * 0.42f, x + s * 0.65f, y + s * 0.58f, paint);
                break;
            case ChipsEngine.EXIT:
                paint.setColor(0xFF0000A0);
                c.drawRect(x + s * 0.1f, y + s * 0.1f, x + s * 0.9f, y + s * 0.9f, paint);
                paint.setColor(0xFF00FFFF);
                for (int i = 0; i < 3; i++) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(s * 0.05f);
                    float in = s * (0.18f + i * 0.1f);
                    c.drawRect(x + in, y + in, x + s - in, y + s - in, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                break;
            case ChipsEngine.SOCKET:
                paint.setColor(0xFF505050);
                c.drawRect(x + s * 0.08f, y + s * 0.08f, x + s * 0.92f, y + s * 0.92f, paint);
                paint.setColor(0xFFFFD000);
                for (int i = 0; i < 3; i++) {
                    c.drawRect(x + s * (0.25f + i * 0.2f), y + s * 0.2f, x + s * (0.3f + i * 0.2f), y + s * 0.8f, paint);
                }
                break;
            case ChipsEngine.DOOR_BLUE:
            case ChipsEngine.DOOR_RED:
            case ChipsEngine.DOOR_GREEN:
            case ChipsEngine.DOOR_YELLOW: {
                int color = keyColor(t - ChipsEngine.DOOR_BLUE);
                paint.setColor(color);
                c.drawRect(r, paint);
                paint.setColor(0xFF000000);
                c.drawCircle(x + s * 0.5f, y + s * 0.42f, s * 0.1f, paint);
                c.drawRect(x + s * 0.46f, y + s * 0.42f, x + s * 0.54f, y + s * 0.68f, paint);
                break;
            }
            case ChipsEngine.KEY_BLUE:
            case ChipsEngine.KEY_RED:
            case ChipsEngine.KEY_GREEN:
            case ChipsEngine.KEY_YELLOW:
                paint.setColor(keyColor(t - ChipsEngine.KEY_BLUE));
                c.drawCircle(x + s * 0.32f, y + s * 0.5f, s * 0.16f, paint);
                c.drawRect(x + s * 0.4f, y + s * 0.45f, x + s * 0.85f, y + s * 0.55f, paint);
                c.drawRect(x + s * 0.7f, y + s * 0.55f, x + s * 0.78f, y + s * 0.68f, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(x + s * 0.32f, y + s * 0.5f, s * 0.06f, paint);
                break;
            case ChipsEngine.FLIPPERS:
                paint.setColor(0xFF00A0FF);
                c.drawOval(x + s * 0.15f, y + s * 0.2f, x + s * 0.45f, y + s * 0.85f, paint);
                c.drawOval(x + s * 0.55f, y + s * 0.2f, x + s * 0.85f, y + s * 0.85f, paint);
                break;
            case ChipsEngine.FIRE_BOOTS:
            case ChipsEngine.SKATES:
            case ChipsEngine.SUCTION: {
                int color = t == ChipsEngine.FIRE_BOOTS ? 0xFFFF4000 : t == ChipsEngine.SKATES ? 0xFF8080FF : 0xFF00A000;
                paint.setColor(color);
                c.drawRect(x + s * 0.3f, y + s * 0.2f, x + s * 0.55f, y + s * 0.7f, paint);
                c.drawRect(x + s * 0.3f, y + s * 0.6f, x + s * 0.8f, y + s * 0.78f, paint);
                if (t == ChipsEngine.SKATES) {
                    paint.setColor(0xFF404040);
                    c.drawRect(x + s * 0.25f, y + s * 0.82f, x + s * 0.85f, y + s * 0.87f, paint);
                } else if (t == ChipsEngine.SUCTION) {
                    paint.setColor(0xFF404040);
                    c.drawCircle(x + s * 0.4f, y + s * 0.84f, s * 0.06f, paint);
                    c.drawCircle(x + s * 0.65f, y + s * 0.84f, s * 0.06f, paint);
                }
                break;
            }
            case ChipsEngine.THIEF:
                paint.setColor(0xFF404040);
                c.drawCircle(x + s * 0.5f, y + s * 0.38f, s * 0.18f, paint);
                c.drawRect(x + s * 0.3f, y + s * 0.55f, x + s * 0.7f, y + s * 0.9f, paint);
                paint.setColor(0xFF000000);
                c.drawRect(x + s * 0.32f, y + s * 0.33f, x + s * 0.68f, y + s * 0.42f, paint);
                break;
            case ChipsEngine.HINT:
                paint.setColor(0xFF00A0A0);
                c.drawRect(x + s * 0.12f, y + s * 0.12f, x + s * 0.88f, y + s * 0.88f, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(s * 0.7f);
                paint.setFakeBoldText(true);
                c.drawText("?", x + s / 2, y + s * 0.75f, paint);
                paint.setFakeBoldText(false);
                paint.setTextAlign(Paint.Align.LEFT);
                break;
            case ChipsEngine.TELEPORT:
                paint.setColor(0xFF0000A0);
                c.drawRect(x + s * 0.1f, y + s * 0.1f, x + s * 0.9f, y + s * 0.9f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.06f);
                paint.setColor(0xFFFFFF00);
                c.drawCircle(x + s / 2, y + s / 2, s * 0.28f, paint);
                c.drawCircle(x + s / 2, y + s / 2, s * 0.14f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            case ChipsEngine.BOMB:
                paint.setColor(0xFF000000);
                c.drawCircle(x + s * 0.5f, y + s * 0.58f, s * 0.28f, paint);
                paint.setColor(0xFFFF8000);
                c.drawRect(x + s * 0.48f, y + s * 0.18f, x + s * 0.56f, y + s * 0.32f, paint);
                paint.setColor(0xFFFFFF00);
                c.drawCircle(x + s * 0.55f, y + s * 0.16f, s * 0.06f, paint);
                break;
            case ChipsEngine.TRAP:
                paint.setColor(0xFF806040);
                c.drawRect(x + s * 0.1f, y + s * 0.1f, x + s * 0.9f, y + s * 0.9f, paint);
                paint.setColor(0xFF000000);
                c.drawRect(x + s * 0.2f, y + s * 0.2f, x + s * 0.8f, y + s * 0.8f, paint);
                break;
            case ChipsEngine.CLONER:
                paint.setColor(0xFF808080);
                c.drawRect(r, paint);
                paint.setColor(0xFFFF0000);
                c.drawRect(x + s * 0.2f, y + s * 0.2f, x + s * 0.8f, y + s * 0.8f, paint);
                break;
            case ChipsEngine.BUTTON_GREEN:
            case ChipsEngine.BUTTON_RED:
            case ChipsEngine.BUTTON_BROWN:
            case ChipsEngine.BUTTON_BLUE: {
                int color = t == ChipsEngine.BUTTON_GREEN ? 0xFF00C000 : t == ChipsEngine.BUTTON_RED ? 0xFFFF0000
                        : t == ChipsEngine.BUTTON_BROWN ? 0xFF8B4513 : 0xFF0000FF;
                paint.setColor(0xFF808080);
                c.drawCircle(x + s / 2, y + s / 2, s * 0.32f, paint);
                paint.setColor(color);
                c.drawCircle(x + s / 2, y + s / 2, s * 0.24f, paint);
                break;
            }
            case ChipsEngine.THIN_N:
            case ChipsEngine.THIN_E:
            case ChipsEngine.THIN_S:
            case ChipsEngine.THIN_W:
            case ChipsEngine.THIN_SE:
                paint.setColor(0xFF808080);
                float w = s * 0.12f;
                if (t == ChipsEngine.THIN_N) {
                    c.drawRect(x, y, x + s, y + w, paint);
                }
                if (t == ChipsEngine.THIN_W) {
                    c.drawRect(x, y, x + w, y + s, paint);
                }
                if (t == ChipsEngine.THIN_S || t == ChipsEngine.THIN_SE) {
                    c.drawRect(x, y + s - w, x + s, y + s, paint);
                }
                if (t == ChipsEngine.THIN_E || t == ChipsEngine.THIN_SE) {
                    c.drawRect(x + s - w, y, x + s, y + s, paint);
                }
                break;
            case ChipsEngine.POPUP:
                paint.setColor(0xFFA0A0A0);
                c.drawRect(x + s * 0.2f, y + s * 0.2f, x + s * 0.8f, y + s * 0.8f, paint);
                break;
            default:
                break;
        }
    }

    private void floor(Canvas c, RectF r) {
        paint.setColor(0xFFC0C0C0);
        c.drawRect(r, paint);
        paint.setColor(0xFFA8A8A8);
        c.drawRect(r.left, r.bottom - r.height() * 0.05f, r.right, r.bottom, paint);
        c.drawRect(r.right - r.width() * 0.05f, r.top, r.right, r.bottom, paint);
    }

    private void outline(Canvas c, RectF r, int color, float w) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w);
        paint.setColor(color);
        c.drawRect(r.left + w, r.top + w, r.right - w, r.bottom - w, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void flame(Canvas c, float bx, float by, float h) {
        Path p = new Path();
        p.moveTo(bx - h * 0.35f, by);
        p.quadTo(bx - h * 0.4f, by - h * 0.6f, bx, by - h);
        p.quadTo(bx + h * 0.4f, by - h * 0.6f, bx + h * 0.35f, by);
        p.close();
        paint.setColor(0xFFFF4000);
        c.drawPath(p, paint);
        paint.setColor(0xFFFFD000);
        c.drawCircle(bx, by - h * 0.25f, h * 0.18f, paint);
    }

    private static int keyColor(int k) {
        return new int[] {0xFF2060FF, 0xFFFF2020, 0xFF20C020, 0xFFFFE000}[k];
    }

    private void drawCreature(Canvas c, int kind, int dir, float x, float y, float s) {
        float cx = x + s / 2, cy = y + s / 2;
        switch (kind) {
            case ChipsEngine.BLOCK:
                paint.setColor(0xFF8B5A2B);
                c.drawRect(x + s * 0.05f, y + s * 0.05f, x + s * 0.95f, y + s * 0.95f, paint);
                paint.setColor(0xFFB07A40);
                c.drawRect(x + s * 0.15f, y + s * 0.15f, x + s * 0.85f, y + s * 0.85f, paint);
                paint.setColor(0xFF6B3A1B);
                c.drawRect(x + s * 0.15f, y + s * 0.45f, x + s * 0.85f, y + s * 0.55f, paint);
                return;
            case ChipsEngine.BUG:
                paint.setColor(0xFF804000);
                c.drawOval(cx - s * 0.25f, cy - s * 0.3f, cx + s * 0.25f, cy + s * 0.3f, paint);
                paint.setColor(0xFF000000);
                for (int i = -1; i <= 1; i++) {
                    c.drawRect(cx - s * 0.4f, cy + i * s * 0.18f, cx + s * 0.4f, cy + i * s * 0.18f + s * 0.04f, paint);
                }
                break;
            case ChipsEngine.FIREBALL:
                flame(c, cx, cy + s * 0.35f, s * 0.7f);
                return;
            case ChipsEngine.BALL:
                paint.setColor(0xFFFF60C0);
                c.drawCircle(cx, cy, s * 0.32f, paint);
                paint.setColor(0xFFFFC0E0);
                c.drawCircle(cx - s * 0.1f, cy - s * 0.1f, s * 0.1f, paint);
                return;
            case ChipsEngine.TANK:
                paint.setColor(0xFF0000C0);
                c.drawRect(cx - s * 0.32f, cy - s * 0.3f, cx + s * 0.32f, cy + s * 0.3f, paint);
                paint.setColor(0xFF6060FF);
                c.drawCircle(cx, cy, s * 0.16f, paint);
                break;
            case ChipsEngine.GLIDER:
                paint.setColor(0xFF00C000);
                Path p = new Path();
                p.moveTo(cx, cy - s * 0.38f);
                p.lineTo(cx + s * 0.35f, cy + s * 0.3f);
                p.lineTo(cx, cy + s * 0.15f);
                p.lineTo(cx - s * 0.35f, cy + s * 0.3f);
                p.close();
                c.save();
                c.rotate(dir * 90, cx, cy);
                c.drawPath(p, paint);
                c.restore();
                return;
            case ChipsEngine.TEETH:
                paint.setColor(0xFF800080);
                c.drawCircle(cx, cy, s * 0.36f, paint);
                paint.setColor(0xFFFFFFFF);
                for (int i = 0; i < 4; i++) {
                    Path t = new Path();
                    float tx = cx - s * 0.24f + i * s * 0.12f;
                    t.moveTo(tx, cy);
                    t.lineTo(tx + s * 0.12f, cy);
                    t.lineTo(tx + s * 0.06f, cy + s * 0.14f);
                    t.close();
                    c.drawPath(t, paint);
                }
                paint.setColor(0xFFFFFF00);
                c.drawCircle(cx - s * 0.12f, cy - s * 0.14f, s * 0.07f, paint);
                c.drawCircle(cx + s * 0.12f, cy - s * 0.14f, s * 0.07f, paint);
                return;
            case ChipsEngine.WALKER:
                paint.setColor(0xFFC08000);
                c.drawCircle(cx, cy, s * 0.3f, paint);
                paint.setColor(0xFF000000);
                c.drawRect(cx - s * 0.35f, cy - s * 0.05f, cx + s * 0.35f, cy + s * 0.05f, paint);
                return;
            case ChipsEngine.BLOB:
                paint.setColor(0xFF00C080);
                c.drawOval(cx - s * 0.38f, cy - s * 0.26f, cx + s * 0.38f, cy + s * 0.32f, paint);
                return;
            case ChipsEngine.PARAMECIUM:
                paint.setColor(0xFFC0C000);
                c.drawOval(cx - s * 0.18f, cy - s * 0.38f, cx + s * 0.18f, cy + s * 0.38f, paint);
                break;
            default:
                return;
        }
        // Direction marker for creatures whose heading matters.
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(cx + ChipsEngine.DX[dir] * s * 0.22f, cy + ChipsEngine.DY[dir] * s * 0.22f, s * 0.06f, paint);
    }

    private void drawChip(Canvas c, int dir, float x, float y, float s, boolean swimming) {
        float cx = x + s / 2;
        if (swimming) {
            paint.setColor(0xFFFFD0A0);
            c.drawCircle(cx, y + s * 0.5f, s * 0.2f, paint);
            paint.setColor(0xFF000000);
            c.drawRect(cx - s * 0.18f, y + s * 0.4f, cx + s * 0.18f, y + s * 0.47f, paint);
            return;
        }
        paint.setColor(0xFF0040C0);
        c.drawRect(cx - s * 0.16f, y + s * 0.62f, cx + s * 0.16f, y + s * 0.92f, paint);
        paint.setColor(0xFF20A020);
        c.drawRect(cx - s * 0.22f, y + s * 0.38f, cx + s * 0.22f, y + s * 0.66f, paint);
        paint.setColor(0xFFFFD0A0);
        c.drawCircle(cx, y + s * 0.26f, s * 0.17f, paint);
        paint.setColor(0xFF603000);
        c.drawRect(cx - s * 0.17f, y + s * 0.08f, cx + s * 0.17f, y + s * 0.17f, paint);
        if (dir != 0) {
            // Glasses face the way Chip is walking.
            paint.setColor(0xFF000000);
            float off = dir == 1 ? s * 0.05f : dir == 3 ? -s * 0.05f : 0;
            c.drawCircle(cx - s * 0.07f + off, y + s * 0.25f, s * 0.05f, paint);
            c.drawCircle(cx + s * 0.07f + off, y + s * 0.25f, s * 0.05f, paint);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void clientTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (paused) {
                    paused = false;
                    return;
                }
                holding = true;
                holdX = e.getX();
                holdY = e.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                holdX = e.getX();
                holdY = e.getY();
                break;
            default:
                holding = false;
                break;
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        int d;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: d = 0; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: d = 1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: d = 2; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: d = 3; break;
            case KeyEvent.KEYCODE_F3: paused = !paused; return true;
            case KeyEvent.KEYCODE_R:
                if (event.isCtrlPressed()) {
                    startLevel(index);
                    return true;
                }
                return false;
            default: return false;
        }
        engine.setInput(d);
        engine.tick();
        engine.setInput(-1);
        return true;
    }
}
