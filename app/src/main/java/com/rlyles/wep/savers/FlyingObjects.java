package com.rlyles.wep.savers;

import android.graphics.Canvas;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.List;

/** 3D Flying Objects: a waving four-colour flag, a twisting ribbon or a spinning knot. */
public final class FlyingObjects extends Saver {
    private static final String[] STYLES = {"Waving &flag", "&Ribbon", "&Knot"};
    private int style;
    private int speed = 5;
    private float t;
    private final Mini3D view = new Mini3D();

    @Override
    public String name() {
        return "3D Flying Objects";
    }

    @Override
    protected void loadSettings() {
        style = prefs.getInt("style", 0);
        speed = prefs.getInt("speed", 5);
    }

    @Override
    protected void reset() {
        view.viewport(w, h);
        view.camera = 4.5f;
    }

    @Override
    public void draw(Canvas c, float dt) {
        t += dt * (0.4f + speed * 0.12f);
        c.drawColor(0xFF000000);
        if (style == 1) {
            ribbon();
        } else if (style == 2) {
            knot();
        } else {
            flag();
        }
        view.flush(c, false);
    }

    /** A flag in four coloured quarters, rippling as it drifts and turns. */
    private void flag() {
        int nx = 16, ny = 12;
        float yaw = (float) Math.sin(t * 0.4) * 0.7f, pitch = (float) Math.sin(t * 0.3) * 0.35f;
        float[][][] p = new float[nx + 1][ny + 1][];
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) {
                float u = i / (float) nx, v = j / (float) ny;
                float x = (u - 0.5f) * 2.4f, y = (v - 0.5f) * 1.8f;
                float z = (float) Math.sin(u * 5 - t * 3) * 0.18f * u + (float) Math.sin(v * 3 + t) * 0.05f;
                // A slight perspective skew makes it look like a window flying past.
                p[i][j] = Mini3D.rotate(new float[] {x, y - u * 0.15f, z}, yaw, pitch, 0);
            }
        }
        int[] quarter = {0xFFFF3020, 0xFF20C030, 0xFF2060FF, 0xFFFFD020};
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                boolean gap = i == nx / 2 || j == ny / 2;
                int color = gap ? 0xFF101010 : quarter[(i < nx / 2 ? 0 : 1) + (j < ny / 2 ? 0 : 2)];
                view.add(p[i][j], p[i + 1][j], p[i + 1][j + 1], color);
                view.add(p[i][j], p[i + 1][j + 1], p[i][j + 1], color);
            }
        }
    }

    /** A ribbon twisting along a looping path. */
    private void ribbon() {
        int n = 80;
        float[][] left = new float[n][], right = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) n * (float) Math.PI * 2;
            float cx = (float) Math.sin(s * 2 + t * 0.5) * 1.3f, cy = (float) Math.sin(s * 3 + t * 0.3) * 0.8f,
                    cz = (float) Math.cos(s * 2 + t * 0.5) * 0.9f;
            float twist = s * 3 + t * 2;
            float ox = (float) Math.cos(twist) * 0.22f, oy = (float) Math.sin(twist) * 0.22f;
            left[i] = Mini3D.rotate(new float[] {cx - ox, cy - oy, cz}, t * 0.3f, 0.3f, 0);
            right[i] = Mini3D.rotate(new float[] {cx + ox, cy + oy, cz}, t * 0.3f, 0.3f, 0);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int color = hue(i / (float) n + t * 0.05f);
            view.add(left[i], right[i], right[j], color);
            view.add(left[i], right[j], left[j], color);
        }
    }

    /** A tube tied in a trefoil knot. */
    private void knot() {
        int n = 120, m = 8;
        float[][][] ring = new float[n][m][];
        for (int i = 0; i < n; i++) {
            double s = i * Math.PI * 2 / n;
            float[] c0 = trefoil(s), c1 = trefoil(s + 0.01);
            float tx = c1[0] - c0[0], ty = c1[1] - c0[1], tz = c1[2] - c0[2];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            // Any vector not parallel to the tangent gives a frame around the tube.
            float ax = 0, ay = 0, az = 1;
            float bx = ty * az - tz * ay, by = tz * ax - tx * az, bz = tx * ay - ty * ax;
            float bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            bx /= bl;
            by /= bl;
            bz /= bl;
            float nx = by * tz - bz * ty, ny = bz * tx - bx * tz, nz = bx * ty - by * tx;
            for (int k = 0; k < m; k++) {
                double a = k * Math.PI * 2 / m;
                float r = 0.16f;
                float ca = (float) Math.cos(a) * r, sa = (float) Math.sin(a) * r;
                ring[i][k] = Mini3D.rotate(new float[] {c0[0] + bx * ca + nx * sa, c0[1] + by * ca + ny * sa,
                        c0[2] + bz * ca + nz * sa}, t * 0.5f, t * 0.3f, 0);
            }
        }
        for (int i = 0; i < n; i++) {
            int i2 = (i + 1) % n;
            int color = hue(i / (float) n);
            for (int k = 0; k < m; k++) {
                int k2 = (k + 1) % m;
                view.add(ring[i][k], ring[i2][k], ring[i2][k2], color);
                view.add(ring[i][k], ring[i2][k2], ring[i][k2], color);
            }
        }
    }

    private static float[] trefoil(double s) {
        return new float[] {(float) (Math.sin(s) + 2 * Math.sin(2 * s)) * 0.45f,
                (float) (Math.cos(s) - 2 * Math.cos(2 * s)) * 0.45f, (float) (-Math.sin(3 * s)) * 0.45f};
    }

    private static int hue(float h) {
        float h6 = (h % 1 + 1) % 1 * 6;
        int i = (int) h6;
        float f = h6 - i;
        float[][] t = {{1, f, 0}, {1 - f, 1, 0}, {0, 1, f}, {0, 1 - f, 1}, {f, 0, 1}, {1, 0, 1 - f}};
        float[] c = t[i % 6];
        return 0xFF000000 | (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255);
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, RetroView host) {
        d.add(new Win95.Group("Style")).at(10, top, 280, 120);
        for (int i = 0; i < STYLES.length; i++) {
            d.add(new Win95.Radio(STYLES[i], 0, style == i)).at(22, top + 26 + i * 30, 250, 28);
        }
        d.add(new Win95.Text("&Speed", false)).at(10, top + 128, 280, 22);
        Win95.Slider sp = d.add(new Win95.Slider(0, 10, speed));
        sp.leftLabel = "Slow";
        sp.rightLabel = "Fast";
        sp.at(10, top + 150, 280, 44);
        apply.add(() -> prefs.edit().putInt("style", Win95.Radio.selected(d, 0)).putInt("speed", sp.value).apply());
        return 200;
    }
}
