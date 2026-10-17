package com.rlyles.wep.savers;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.List;

/** Flying Windows: waving four-colour window flags rushing out of the middle of the screen. */
public final class FlyingWindows extends Saver {
    private static final int[] QUARTERS = {0xFFFF2010, 0xFF10C020, 0xFF1050FF, 0xFFFFD010};
    private int warp = 5, density = 25;
    private float[] x, y, z, phase;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float t;

    @Override
    public String name() {
        return "Flying Windows";
    }

    @Override
    protected void loadSettings() {
        warp = prefs.getInt("warp", 5);
        density = prefs.getInt("density", 25);
    }

    @Override
    protected void reset() {
        int n = 5 + density;
        x = new float[n];
        y = new float[n];
        z = new float[n];
        phase = new float[n];
        for (int i = 0; i < n; i++) {
            spawn(i, rand(0.1f, 1f));
        }
    }

    private void spawn(int i, float depth) {
        x[i] = rand(-1f, 1f);
        y[i] = rand(-1f, 1f);
        z[i] = depth;
        phase[i] = rand(0, 6.28f);
    }

    @Override
    public void draw(Canvas c, float dt) {
        t += dt;
        c.drawColor(0xFF000000);
        float speed = 0.06f + warp * 0.05f;
        float cx = w / 2f, cy = h / 2f, scale = Math.max(w, h) * 0.5f;
        Integer[] order = new Integer[x.length];
        for (int i = 0; i < x.length; i++) {
            z[i] -= speed * dt;
            if (z[i] <= 0.03f) {
                spawn(i, 1f);
            }
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(z[b], z[a]));
        for (int i : order) {
            float sx = cx + x[i] / z[i] * scale, sy = cy + y[i] / z[i] * scale;
            float size = 0.06f / z[i] * scale * 0.25f;
            if (sx < -size * 2 || sx > w + size * 2 || sy < -size * 2 || sy > h + size * 2) {
                spawn(i, 1f);
                continue;
            }
            int alpha = (int) (Math.min(1f, (1f - z[i]) * 2f) * 255);
            drawFlag(c, sx, sy, size, t * 4 + phase[i], alpha);
        }
    }

    private void drawFlag(Canvas c, float cx, float cy, float s, float ph, int alpha) {
        float gap = s * 0.12f;
        for (int q = 0; q < 4; q++) {
            int col = q % 2, row = q / 2;
            float x0 = cx - s + col * (s + gap), y0 = cy - s + row * (s + gap);
            path.reset();
            int steps = 6;
            for (int k = 0; k <= steps; k++) {
                float u = k / (float) steps;
                float px = x0 + u * s, py = y0 + (float) Math.sin(ph + (col + u) * 2.2f) * s * 0.18f;
                if (k == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            for (int k = steps; k >= 0; k--) {
                float u = k / (float) steps;
                float px = x0 + u * s, py = y0 + s + (float) Math.sin(ph + (col + u) * 2.2f) * s * 0.18f;
                path.lineTo(px, py);
            }
            path.close();
            paint.setColor(QUARTERS[q]);
            paint.setAlpha(alpha);
            c.drawPath(path, paint);
        }
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, RetroView host) {
        d.add(new Win95.Text("&Warp speed", false)).at(10, top, 280, 22);
        Win95.Slider speed = d.add(new Win95.Slider(0, 10, warp));
        speed.leftLabel = "Slow";
        speed.rightLabel = "Fast";
        speed.at(10, top + 24, 280, 44);
        d.add(new Win95.Text("&Density", false)).at(10, top + 76, 280, 22);
        Win95.Slider dens = d.add(new Win95.Slider(5, 80, density));
        dens.leftLabel = "Low";
        dens.rightLabel = "High";
        dens.at(10, top + 100, 280, 44);
        apply.add(() -> prefs.edit().putInt("warp", speed.value).putInt("density", dens.value).apply());
        return 150;
    }
}
