package com.rlyles.wep.games;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.Sprites;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * SkiFree: ski down the endless mountain, dodging trees and rocks and catching air off the ramps.
 * Hold your finger where you want to go; double-tap to jump. Go far enough and something big and
 * hairy comes looking for you.
 */
@SuppressLint("ViewConstructor")
public final class SkiFree extends WinApp {
    // Object kinds
    static final int TREE = 0, SMALL_TREE = 1, DEAD_TREE = 2, ROCK = 3, STUMP = 4, MOGUL = 5, RAMP = 6, FLAG = 7;
    private static final int TILE = 220;
    private static final float YETI_DISTANCE = 2000;

    /** -3 (stopped, facing left) .. 0 (straight down) .. 3 (stopped, facing right). */
    int dir = 0;
    float x, y, speed, airTime, crashTime, style;
    boolean fast, eaten, started;
    private float elapsed;
    private float yetiX, yetiY;
    boolean yetiActive;
    private float eatTime;
    private final List<float[]> dogs = new ArrayList<>();

    private float k;
    private final RectF view = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean steering;
    private float touchX, touchY;
    private long lastTap;
    private final List<int[]> knocked = new ArrayList<>();

    public SkiFree(Context context, Host host) {
        super(context, host, "ski");
        reset();
        setAnimating(true);
    }

    @Override
    public String title() {
        return "SkiFree";
    }

    @Override
    protected void buildMenus(List<Menu> menus) {
        menus.add(new Menu("&Game", items -> {
            items.add(new Item("&New Game", "F2", "Back to the top of the hill", true, false, this::reset));
            items.add(new Item("&Fast", "F", "Ski faster", true, fast, () -> fast = !fast));
            items.add(Item.sep());
            items.add(new Item("E&xit", "", "Exit SkiFree", true, false, () -> host.closeApp(this)));
        }));
        addHelpMenu(menus, "Ski down the mountain. Hold your finger below the skier and he heads toward it; the "
                + "further to the side, the slower he goes. Put your finger beside or above him to stop.\n\n"
                + "Double-tap to jump. Ramps throw you high into the air, where you can fly over trees and rocks for "
                + "style points. Bumps give you a little hop.\n\nHitting a tree, rock or stump knocks you down for a "
                + "moment.\n\nKeyboard: the arrow keys steer, Space jumps and F toggles fast skiing.\n\nThere is no "
                + "online help for SkiFree, and no explanation for what lives at the bottom of the hill.",
                "SkiFree for the Entertainment Pack.");
    }

    void reset() {
        dir = 3;
        x = 0;
        y = 0;
        speed = 0;
        airTime = crashTime = style = 0;
        elapsed = 0;
        eaten = started = false;
        yetiActive = false;
        eatTime = 0;
        dogs.clear();
        knocked.clear();
        invalidate();
    }

    // ---------------------------------------------------------------- the world

    private static long hash(int a, int b, int c) {
        long h = a * 73856093L ^ b * 19349663L ^ c * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        return h ^ h >>> 15;
    }

    /** Objects in world tile (tx, ty): {kind, x, y}. Deterministic, so the mountain is endless and fixed. */
    private List<int[]> tileObjects(int tx, int ty) {
        List<int[]> out = new ArrayList<>();
        if (ty < 0) {
            return out;
        }
        Random r = new Random(hash(tx, ty, 17));
        int n = 2 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int roll = r.nextInt(100);
            int kind = roll < 34 ? TREE : roll < 50 ? SMALL_TREE : roll < 58 ? DEAD_TREE : roll < 68 ? ROCK
                    : roll < 76 ? STUMP : roll < 92 ? MOGUL : RAMP;
            int ox = tx * TILE + r.nextInt(TILE), oy = ty * TILE + r.nextInt(TILE);
            if (ty == 0 && oy < 120 && Math.abs(ox) < 120) {
                continue; // keep the start clear
            }
            out.add(new int[] {kind, ox, oy});
        }
        return out;
    }

    /** Footprint (half-width, height above the base) of each kind, in world units. */
    private static float[] size(int kind) {
        switch (kind) {
            case TREE: return new float[] {12, 40};
            case SMALL_TREE: return new float[] {8, 24};
            case DEAD_TREE: return new float[] {9, 30};
            case ROCK: return new float[] {11, 10};
            case STUMP: return new float[] {8, 9};
            case MOGUL: return new float[] {12, 6};
            case RAMP: return new float[] {16, 8};
            default: return new float[] {4, 20};
        }
    }

    // ---------------------------------------------------------------- simulation

    private float targetSpeed() {
        int a = Math.abs(dir);
        float max = fast ? 340 : 230;
        return a == 3 ? 0 : a == 2 ? max * 0.55f : a == 1 ? max * 0.85f : max;
    }

    @Override
    protected void frame(float dt) {
        if (eaten) {
            eatTime += dt;
            return;
        }
        if (steering && airTime <= 0 && crashTime <= 0) {
            steerToward(touchX, touchY);
        }
        if (crashTime > 0) {
            crashTime -= dt;
            speed = 0;
            if (crashTime <= 0) {
                dir = dir < 0 ? -3 : 3;
            }
        } else {
            float target = targetSpeed();
            speed += (target - speed) * Math.min(1, dt * (target > speed ? 1.5f : 3f));
        }
        if (speed > 1 || airTime > 0) {
            started = true;
        }
        if (started) {
            elapsed += dt;
        }
        double angle = Math.toRadians(dir * 25);
        if (airTime <= 0 && Math.abs(dir) == 3 && crashTime <= 0) {
            angle = 0;
        }
        x += (float) Math.sin(angle) * speed * dt;
        y += (float) Math.cos(angle) * speed * dt;
        if (airTime > 0) {
            airTime -= dt;
            if (airTime <= 0) {
                sound(Sfx.DROP);
            }
        } else if (crashTime <= 0) {
            collide();
        }
        moveDogs(dt);
        moveYeti(dt);
    }

    private void collide() {
        int tx0 = (int) Math.floor((x - 40) / TILE), tx1 = (int) Math.floor((x + 40) / TILE);
        int ty0 = (int) Math.floor((y - 50) / TILE), ty1 = (int) Math.floor((y + 50) / TILE);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int[] o : tileObjects(tx, ty)) {
                    float[] s = size(o[0]);
                    if (Math.abs(o[1] - x) < s[0] + 5 && y > o[2] - 4 && y < o[2] + 4 && !wasKnocked(o)) {
                        hit(o);
                        return;
                    }
                }
            }
        }
        for (float[] dog : dogs) {
            if (Math.abs(dog[0] - x) < 10 && Math.abs(dog[1] - y) < 6) {
                crash();
                return;
            }
        }
    }

    private boolean wasKnocked(int[] o) {
        for (int[] k2 : knocked) {
            if (k2[1] == o[1] && k2[2] == o[2]) {
                return true;
            }
        }
        return false;
    }

    private void hit(int[] o) {
        switch (o[0]) {
            case RAMP:
                airTime = 0.9f + speed / 400f;
                style += 20;
                sound(Sfx.BLIP);
                knocked.add(o);
                break;
            case MOGUL:
                airTime = 0.25f;
                style += 2;
                knocked.add(o);
                break;
            default:
                knocked.add(o);
                crash();
                break;
        }
        if (knocked.size() > 40) {
            knocked.remove(0);
        }
    }

    private void crash() {
        crashTime = 1.0f;
        speed = 0;
        style = Math.max(0, style - 10);
        sound(Sfx.BOUNCE);
    }

    void jump() {
        if (airTime <= 0 && crashTime <= 0 && !eaten && speed > 40) {
            airTime = 0.55f;
            style += 5;
            sound(Sfx.POP);
        }
    }

    private void moveDogs(float dt) {
        if (dogs.size() < 2 && Math.random() < dt * 0.15) {
            dogs.add(new float[] {x + (float) (Math.random() - 0.5) * 600, y + 500, Math.random() < 0.5 ? -40 : 40});
        }
        for (int i = dogs.size() - 1; i >= 0; i--) {
            float[] d = dogs.get(i);
            d[0] += d[2] * dt;
            if (d[1] < y - 400) {
                dogs.remove(i);
            }
        }
    }

    private void moveYeti(float dt) {
        float dist = y / 10f;
        if (!yetiActive && dist >= YETI_DISTANCE) {
            yetiActive = true;
            yetiX = x + (Math.random() < 0.5 ? -350 : 350);
            yetiY = y + 280;
            sound(Sfx.LOSE);
        }
        if (!yetiActive) {
            return;
        }
        float dx = x - yetiX, dy = y - yetiY;
        float d = (float) Math.hypot(dx, dy);
        float yetiSpeed = 250;
        if (d > 1) {
            yetiX += dx / d * yetiSpeed * dt;
            yetiY += dy / d * yetiSpeed * dt;
        }
        if (d < 14 && airTime <= 0) {
            eaten = true;
            sound(Sfx.BOOM);
        }
    }

    private void steerToward(float sx, float sy) {
        float px = view.centerX(), py = view.top + view.height() * 0.3f;
        float dx = sx - px, dy = sy - py;
        if (dy < 10 * dp) {
            dir = dx < 0 ? -3 : 3;
            return;
        }
        double a = Math.toDegrees(Math.atan2(dx, dy));
        int d = (int) Math.round(a / 25);
        dir = Math.max(-3, Math.min(3, d));
    }

    // ---------------------------------------------------------------- drawing

    /** The original sprites from the user's SKI.EXE, by resource number, or null. */
    private static Bitmap[] art() {
        return Media.get().cached("ski.art", m -> {
            if (!m.hasArt("ski.exe")) {
                return null;
            }
            Bitmap[] a = new Bitmap[87];
            for (int i = 1; i < a.length; i++) {
                Bitmap b = m.bitmap("ski.exe", Integer.toString(i));
                a[i] = b == null ? null : Sprites.cutOut(b);
            }
            return a[1] == null ? null : a;
        });
    }

    /** Draws original sprite {@code id} standing on (bx, by); false if there is no such sprite. */
    private boolean sprite(Canvas c, int id, float bx, float by) {
        Bitmap[] a = art();
        if (a == null || a[id] == null) {
            return false;
        }
        Bitmap b = a[id];
        Sprites.draw(c, b, bx - b.getWidth() * k / 2, by - b.getHeight() * k, b.getWidth() * k, b.getHeight() * k);
        return true;
    }

    private static int frame(float fps, int frames) {
        return (int) (SystemClock.uptimeMillis() * fps / 1000) % frames;
    }

    @Override
    protected void layoutClient(RectF r) {
        view.set(r);
        k = Math.min(r.width() / 640f, r.height() / 480f) * 1.4f;
    }

    private float sx(float wx) {
        return view.centerX() + (wx - x) * k;
    }

    private float sy(float wy) {
        return view.top + view.height() * 0.3f + (wy - y) * k;
    }

    @Override
    protected void drawClient(Canvas c) {
        c.drawColor(0xFFFFFFFF);
        float halfW = view.width() / 2 / k + 40, above = view.height() * 0.3f / k + 60, below = view.height() * 0.7f / k + 60;
        int tx0 = (int) Math.floor((x - halfW) / TILE), tx1 = (int) Math.floor((x + halfW) / TILE);
        int ty0 = (int) Math.floor((y - above) / TILE), ty1 = (int) Math.floor((y + below) / TILE);
        List<int[]> objs = new ArrayList<>();
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int ty = ty0; ty <= ty1; ty++) {
                objs.addAll(tileObjects(tx, ty));
            }
        }
        // Painter's order: things further up the hill first.
        java.util.Collections.sort(objs, (a, b) -> a[2] - b[2]);
        boolean skierDrawn = false;
        for (int[] o : objs) {
            if (!skierDrawn && o[2] > y) {
                drawSkierAndFriends(c);
                skierDrawn = true;
            }
            drawObject(c, o[0], sx(o[1]), sy(o[2]));
        }
        if (!skierDrawn) {
            drawSkierAndFriends(c);
        }
        drawInfo(c);
    }

    private void drawSkierAndFriends(Canvas c) {
        for (float[] d : dogs) {
            drawDog(c, sx(d[0]), sy(d[1]), d[2] > 0);
        }
        if (!eaten) {
            drawSkier(c, sx(x), sy(y) - (airTime > 0 ? (float) Math.sin(Math.min(1, airTime) * Math.PI) * 18 * k : 0));
        }
        if (yetiActive) {
            drawYeti(c, sx(yetiX), sy(yetiY));
        }
    }

    private void drawInfo(Canvas c) {
        int ms = (int) (elapsed * 100);
        String time = String.format(Locale.US, "%d:%02d:%02d.%02d", ms / 360000, ms / 6000 % 60, ms / 100 % 60, ms % 100);
        String[] lines = {"Time:  " + time, "Dist:  " + (int) (y / 10) + "m", "Speed: " + (int) (speed / 10) + "m/s",
                "Style: " + (int) style};
        float w = 150 * dp, h = 74 * dp;
        RectF box = new RectF(view.right - w - 6 * dp, view.top + 6 * dp, view.right - 6 * dp, view.top + 6 * dp + h);
        paint.setColor(0xFFFFFFFF);
        c.drawRect(box, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp);
        paint.setColor(0xFF000000);
        c.drawRect(box, paint);
        paint.setStyle(Paint.Style.FILL);
        ui.withFont(12, false, () -> {
            for (int i = 0; i < lines.length; i++) {
                ui.label(c, lines[i], box.left + 6 * dp, box.top + (16 + i * 17) * dp, Win95.DARK);
            }
        });
        if (eaten && eatTime > 1) {
            ui.withFont(18, true, () -> ui.centered(c, "Tap to ski again", new RectF(view.left, view.centerY(),
                    view.right, view.centerY() + 40 * dp), 0xFF000000));
        }
    }

    private void drawObject(Canvas c, int kind, float bx, float by) {
        int[] ids = {49, 49, 50, 45, 46, 48, 52, 23};
        if (kind >= 0 && kind < ids.length && sprite(c, ids[kind], bx, by + (kind == RAMP || kind == MOGUL ? 4 * k : 0))) {
            return;
        }
        float s = k;
        Path p = new Path();
        switch (kind) {
            case TREE:
            case SMALL_TREE: {
                float sc = kind == TREE ? 1 : 0.62f;
                paint.setColor(0xFF804000);
                c.drawRect(bx - 2.5f * s * sc, by - 8 * s * sc, bx + 2.5f * s * sc, by, paint);
                paint.setColor(0xFF008000);
                for (int tier = 0; tier < 3; tier++) {
                    float top = by - (40 - tier * 9) * s * sc, base = by - (16 - tier * 3) * s * sc - tier * 6 * s * sc;
                    float half = (12 - tier * 2.5f) * s * sc;
                    p.reset();
                    p.moveTo(bx, top);
                    p.lineTo(bx + half, base);
                    p.lineTo(bx - half, base);
                    p.close();
                    c.drawPath(p, paint);
                }
                break;
            }
            case DEAD_TREE:
                paint.setColor(0xFF6B3A10);
                paint.setStrokeWidth(2.5f * s);
                paint.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(bx, by, bx, by - 30 * s, paint);
                c.drawLine(bx, by - 18 * s, bx - 8 * s, by - 26 * s, paint);
                c.drawLine(bx, by - 22 * s, bx + 7 * s, by - 29 * s, paint);
                c.drawLine(bx, by - 12 * s, bx + 6 * s, by - 17 * s, paint);
                break;
            case ROCK:
                paint.setColor(0xFF808080);
                c.drawOval(bx - 11 * s, by - 10 * s, bx + 11 * s, by + 2 * s, paint);
                paint.setColor(0xFFB0B0B0);
                c.drawOval(bx - 7 * s, by - 9 * s, bx + 2 * s, by - 4 * s, paint);
                break;
            case STUMP:
                paint.setColor(0xFF804000);
                c.drawRect(bx - 7 * s, by - 8 * s, bx + 7 * s, by, paint);
                paint.setColor(0xFFC08040);
                c.drawOval(bx - 7 * s, by - 11 * s, bx + 7 * s, by - 5 * s, paint);
                break;
            case MOGUL:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * s);
                paint.setColor(0xFFA0A0C0);
                c.drawArc(new RectF(bx - 12 * s, by - 6 * s, bx, by + 4 * s), 200, 140, false, paint);
                c.drawArc(new RectF(bx - 2 * s, by - 7 * s, bx + 12 * s, by + 4 * s), 200, 140, false, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            case RAMP: {
                int[] stripes = {0xFFFF0000, 0xFFFFA000, 0xFFFFFF00, 0xFF00C000, 0xFF0000FF};
                for (int i = 0; i < stripes.length; i++) {
                    paint.setColor(stripes[i]);
                    float y0 = by - 8 * s + i * 1.6f * s;
                    c.drawRect(bx - 16 * s, y0, bx + 16 * s, y0 + 1.6f * s, paint);
                }
                break;
            }
            default:
                break;
        }
    }

    private void drawSkier(Canvas c, float bx, float by) {
        // Original frames: 1 straight down, 2-4 turning left to stopped, 5-7 the same to the right,
        // 12 "OUCH!", 13 sitting in the snow, 14-15 in the air.
        int id = crashTime > 0 ? (crashTime > 0.5f ? 12 : 13)
                : airTime > 0 ? (airTime > 0.6f ? 15 : 14)
                : dir == 0 ? 1 : dir < 0 ? 1 - dir : 4 + dir;
        if (sprite(c, id, bx, by)) {
            return;
        }
        float s = k;
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStrokeCap(Paint.Cap.ROUND);
        if (crashTime > 0) {
            // Face-down in the snow with skis everywhere.
            paint.setColor(0xFFFF0000);
            c.drawOval(bx - 10 * s, by - 6 * s, bx + 8 * s, by, paint);
            paint.setColor(0xFF000000);
            c.drawCircle(bx - 11 * s, by - 4 * s, 3 * s, paint);
            st.setStrokeWidth(1.5f * s);
            st.setColor(0xFF000000);
            c.drawLine(bx - 12 * s, by + 3 * s, bx + 10 * s, by - 9 * s, st);
            c.drawLine(bx - 6 * s, by - 10 * s, bx + 12 * s, by + 2 * s, st);
            return;
        }
        float lean = airTime > 0 ? 0 : dir;
        double ang = Math.toRadians(Math.abs(dir) == 3 && airTime <= 0 ? (dir < 0 ? 90 : -90) : -lean * 25);
        float sxv = (float) Math.sin(ang), syv = (float) Math.cos(ang);
        // Skis
        st.setStrokeWidth(1.6f * s);
        st.setColor(0xFF000000);
        for (int side = -1; side <= 1; side += 2) {
            float ox = bx + side * 3 * s * syv, oy = by - 1 * s + side * 3 * s * sxv * 0.3f;
            c.drawLine(ox - sxv * 10 * s, oy - syv * 10 * s * 0.4f, ox + sxv * 10 * s, oy + syv * 10 * s * 0.4f, st);
        }
        // Legs, body, head
        paint.setColor(0xFF0000C0);
        c.drawRect(bx - 3 * s, by - 9 * s, bx + 3 * s, by - 1 * s, paint);
        paint.setColor(0xFFFF0000);
        c.drawRect(bx - 4.5f * s, by - 18 * s, bx + 4.5f * s, by - 9 * s, paint);
        paint.setColor(0xFFFFD0A0);
        c.drawCircle(bx, by - 21 * s, 3.2f * s, paint);
        paint.setColor(0xFF000000);
        c.drawRect(bx - 3.5f * s, by - 25 * s, bx + 3.5f * s, by - 22 * s, paint);
        // Poles
        st.setStrokeWidth(1f * s);
        c.drawLine(bx - 5 * s, by - 14 * s, bx - 8 * s - lean * 2 * s, by - 2 * s, st);
        c.drawLine(bx + 5 * s, by - 14 * s, bx + 8 * s - lean * 2 * s, by - 2 * s, st);
    }

    private void drawDog(Canvas c, float bx, float by, boolean right) {
        if (sprite(c, 33 + frame(4, 2), bx, by)) {
            return;
        }
        float s = k, f = right ? 1 : -1;
        paint.setColor(0xFF8B5A2B);
        c.drawOval(bx - 7 * s, by - 8 * s, bx + 7 * s, by - 3 * s, paint);
        c.drawCircle(bx + f * 7 * s, by - 9 * s, 3 * s, paint);
        paint.setStrokeWidth(1.4f * s);
        paint.setColor(0xFF5A3A1B);
        for (int i = -1; i <= 1; i += 2) {
            c.drawLine(bx + i * 4 * s, by - 4 * s, bx + i * 5 * s, by, paint);
        }
    }

    private void drawYeti(Canvas c, float bx, float by) {
        // Original frames: 70-73 running, 74-75 running up the hill, 76-79 eating, 80-81 after.
        int id;
        if (eaten) {
            int step = (int) (eatTime * 4);
            id = step < 4 ? 76 + step : 80 + frame(3, 2);
        } else {
            float dx = x - yetiX, dy = y - yetiY;
            id = dy < 0 && Math.abs(dy) > Math.abs(dx) ? 74 + frame(6, 2) : (dx > 0 ? 70 : 72) + frame(6, 2);
        }
        if (sprite(c, id, bx, by)) {
            return;
        }
        float s = k * 1.2f;
        paint.setColor(0xFFEFEFEF);
        c.drawOval(bx - 10 * s, by - 34 * s, bx + 10 * s, by - 4 * s, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * s);
        paint.setColor(0xFF606060);
        c.drawOval(bx - 10 * s, by - 34 * s, bx + 10 * s, by - 4 * s, paint);
        paint.setStyle(Paint.Style.FILL);
        float wave = (float) Math.sin(SystemClock.uptimeMillis() / 120.0) * 4 * s;
        paint.setColor(0xFFEFEFEF);
        c.drawOval(bx - 18 * s, by - 34 * s + wave, bx - 8 * s, by - 26 * s + wave, paint);
        c.drawOval(bx + 8 * s, by - 34 * s - wave, bx + 18 * s, by - 26 * s - wave, paint);
        c.drawOval(bx - 8 * s, by - 6 * s, bx - 1 * s, by + 1 * s, paint);
        c.drawOval(bx + 1 * s, by - 6 * s, bx + 8 * s, by + 1 * s, paint);
        paint.setColor(0xFF000000);
        c.drawCircle(bx - 3 * s, by - 28 * s, 1.3f * s, paint);
        c.drawCircle(bx + 3 * s, by - 28 * s, 1.3f * s, paint);
        paint.setColor(eaten ? 0xFFFF0000 : 0xFF800000);
        c.drawOval(bx - 4 * s, by - 24 * s, bx + 4 * s, by - (eaten ? 17 : 21) * s, paint);
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void clientTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (eaten) {
                    reset();
                    return;
                }
                long now = SystemClock.uptimeMillis();
                if (now - lastTap < 300) {
                    jump();
                }
                lastTap = now;
                steering = true;
                touchX = e.getX();
                touchY = e.getY();
                break;
            }
            case MotionEvent.ACTION_MOVE:
                touchX = e.getX();
                touchY = e.getY();
                break;
            default:
                steering = false;
                break;
        }
    }

    @Override
    protected boolean key(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                dir = Math.max(-3, dir - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dir = Math.min(3, dir + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dir = 0;
                return true;
            case KeyEvent.KEYCODE_SPACE:
                jump();
                return true;
            case KeyEvent.KEYCODE_F:
                fast = !fast;
                return true;
            case KeyEvent.KEYCODE_F2:
                reset();
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onHidden() {
        steering = false;
    }

    /** Test hook: ski straight down for a while. */
    public void skiDown(float seconds) {
        dir = 0;
        stepForTest(seconds);
    }
}
