package com.rlyles.wep.savers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D Pipes: shiny pipes grow through a 3D grid, turning corners with ball joints or elbows, until
 * the screen fills up and it starts again.
 */
public final class Pipes3D extends Saver {
    private static final int GX = 14, GY = 10, GZ = 10;
    private static final int[] COLORS = {0xFFFF2020, 0xFF20C020, 0xFF2060FF, 0xFFFFD020, 0xFFFF40FF, 0xFF20E0E0,
            0xFFFF8020, 0xFFE0E0E0};
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private boolean multiple = true;
    private int joints = 2; // 0 ball, 1 elbow, 2 mixed
    private int speed = 5;

    private Bitmap canvasBitmap;
    private Canvas board;
    private boolean[] used;
    private final List<int[]> pipes = new ArrayList<>(); // x, y, z, dir, color, steps
    private float timer;
    private int segments;
    private float yaw, pitch;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Mini3D view = new Mini3D();

    @Override
    public String name() {
        return "3D Pipes";
    }

    @Override
    protected void loadSettings() {
        multiple = prefs.getBoolean("multiple", true);
        joints = prefs.getInt("joints", 2);
        speed = prefs.getInt("speed", 5);
    }

    @Override
    protected void reset() {
        if (canvasBitmap == null || canvasBitmap.getWidth() != w || canvasBitmap.getHeight() != h) {
            if (canvasBitmap != null) {
                canvasBitmap.recycle();
            }
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            board = new Canvas(canvasBitmap);
        }
        board.drawColor(0xFF000000);
        used = new boolean[GX * GY * GZ];
        pipes.clear();
        segments = 0;
        yaw = rand(-0.5f, 0.5f);
        pitch = rand(-0.35f, 0.35f);
        view.camera = 3.2f;
        view.viewport(w, h);
        view.focal = Math.max(w, h) * 0.75f;
        int n = multiple ? 3 + rnd.nextInt(3) : 1;
        for (int i = 0; i < n; i++) {
            startPipe();
        }
    }

    private int idx(int x, int y, int z) {
        return (z * GY + y) * GX + x;
    }

    private void startPipe() {
        for (int tries = 0; tries < 50; tries++) {
            int x = rnd.nextInt(GX), y = rnd.nextInt(GY), z = rnd.nextInt(GZ);
            if (!used[idx(x, y, z)]) {
                used[idx(x, y, z)] = true;
                pipes.add(new int[] {x, y, z, rnd.nextInt(6), COLORS[rnd.nextInt(COLORS.length)], 0});
                drawJoint(x, y, z, pipes.get(pipes.size() - 1)[4], true);
                return;
            }
        }
    }

    /** Grid cell to view space, centred on the origin, with the scene's tilt applied. */
    private float[] world(float x, float y, float z) {
        float s = 2.4f / GX;
        float[] p = {(x - (GX - 1) / 2f) * s, (y - (GY - 1) / 2f) * s, (z - (GZ - 1) / 2f) * s};
        return Mini3D.rotate(p, yaw, pitch, 0);
    }

    private float radius(float[] p) {
        return 0.055f * view.focal / (p[2] + view.camera);
    }

    private static int shade(int color, float k) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * k)), g = Math.min(255, (int) (((color >> 8) & 0xFF) * k)),
                b = Math.min(255, (int) ((color & 0xFF) * k));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void drawSegment(int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        float[] a = world(x0, y0, z0), b = world(x1, y1, z1);
        float ax = view.sx(a), ay = view.sy(a), bx = view.sx(b), by = view.sy(b);
        float r = (radius(a) + radius(b)) / 2;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        // A cylinder: dark edges, mid body, a bright streak toward the light.
        float dx = bx - ax, dy = by - ay, len = (float) Math.hypot(dx, dy);
        float nx = len == 0 ? 0 : -dy / len, ny = len == 0 ? 0 : dx / len;
        if (nx * -0.6f + ny * -0.8f < 0) {
            nx = -nx;
            ny = -ny;
        }
        float[][] bands = {{2f, 0.35f, 0}, {1.6f, 0.65f, 0.1f}, {1.1f, 0.9f, 0.2f}, {0.6f, 1.15f, 0.3f}, {0.25f, 1.6f, 0.4f}};
        for (float[] band : bands) {
            paint.setStrokeWidth(r * band[0]);
            paint.setColor(shade(color, band[1]));
            float ox = nx * r * band[2], oy = ny * r * band[2];
            board.drawLine(ax + ox, ay + oy, bx + ox, by + oy, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawJoint(int x, int y, int z, int color, boolean ball) {
        float[] p = world(x, y, z);
        float cx = view.sx(p), cy = view.sy(p), r = radius(p) * (ball ? 1.45f : 1.05f);
        paint.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.4f,
                new int[] {shade(color, 1.8f), color, shade(color, 0.3f)}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        board.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
    }

    private void grow() {
        if (pipes.isEmpty()) {
            reset();
            return;
        }
        for (int i = pipes.size() - 1; i >= 0; i--) {
            int[] p = pipes.get(i);
            List<Integer> options = new ArrayList<>();
            for (int d = 0; d < 6; d++) {
                int nx = p[0] + DIRS[d][0], ny = p[1] + DIRS[d][1], nz = p[2] + DIRS[d][2];
                if (nx >= 0 && ny >= 0 && nz >= 0 && nx < GX && ny < GY && nz < GZ && !used[idx(nx, ny, nz)]) {
                    options.add(d);
                }
            }
            if (options.isEmpty()) {
                drawJoint(p[0], p[1], p[2], p[4], true);
                pipes.remove(i);
                if (multiple && segments < 600) {
                    startPipe();
                }
                continue;
            }
            int d = p[3];
            boolean turn = !options.contains(d) || rnd.nextInt(4) == 0;
            if (turn) {
                d = options.get(rnd.nextInt(options.size()));
            }
            int nx = p[0] + DIRS[d][0], ny = p[1] + DIRS[d][1], nz = p[2] + DIRS[d][2];
            if (d != p[3] && p[5] > 0) {
                drawJoint(p[0], p[1], p[2], p[4], joints == 0 || joints == 2 && rnd.nextBoolean());
            }
            drawSegment(p[0], p[1], p[2], nx, ny, nz, p[4]);
            used[idx(nx, ny, nz)] = true;
            p[0] = nx;
            p[1] = ny;
            p[2] = nz;
            p[3] = d;
            p[5]++;
            segments++;
        }
        if (segments > 700) {
            reset();
        }
    }

    @Override
    public void draw(Canvas c, float dt) {
        if (canvasBitmap == null) {
            c.drawColor(0xFF000000);
            return;
        }
        timer += dt;
        float step = 0.16f - speed * 0.013f;
        int guard = 0;
        while (timer >= step && guard++ < 6) {
            timer -= step;
            grow();
        }
        c.drawBitmap(canvasBitmap, 0, 0, null);
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, RetroView host) {
        Win95.Check multi = d.add(new Win95.Check("&Multiple pipes", multiple));
        multi.at(10, top, 280, 30);
        d.add(new Win95.Group("Joint type")).at(10, top + 36, 280, 120);
        d.add(new Win95.Radio("&Ball", 0, joints == 0)).at(22, top + 62, 250, 28);
        d.add(new Win95.Radio("&Elbow", 0, joints == 1)).at(22, top + 92, 250, 28);
        d.add(new Win95.Radio("Mi&xed", 0, joints == 2)).at(22, top + 122, 250, 28);
        d.add(new Win95.Text("&Speed", false)).at(10, top + 164, 280, 22);
        Win95.Slider sp = d.add(new Win95.Slider(0, 10, speed));
        sp.leftLabel = "Slow";
        sp.rightLabel = "Fast";
        sp.at(10, top + 186, 280, 44);
        apply.add(() -> prefs.edit().putBoolean("multiple", multi.checked).putInt("joints", Win95.Radio.selected(d, 0))
                .putInt("speed", sp.value).apply());
        return 236;
    }

    @Override
    public void release() {
        if (canvasBitmap != null) {
            canvasBitmap.recycle();
            canvasBitmap = null;
        }
    }
}
