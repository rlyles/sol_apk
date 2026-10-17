package com.rlyles.wep.savers;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.rlyles.wep.win.Win95;

import java.util.List;

/** Starfield Simulation: stars rushing past, with Warp Speed and Starfield Density settings. */
public final class Starfield extends Saver {
    private int warp = 5, density = 50;
    private float[] x, y, z;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public String name() {
        return "Starfield";
    }

    @Override
    protected void loadSettings() {
        warp = prefs.getInt("warp", 5);
        density = prefs.getInt("density", 50);
    }

    @Override
    protected void reset() {
        int n = 40 + density * 6;
        x = new float[n];
        y = new float[n];
        z = new float[n];
        for (int i = 0; i < n; i++) {
            spawn(i, rand(0.05f, 1f));
        }
    }

    private void spawn(int i, float depth) {
        x[i] = rand(-1f, 1f);
        y[i] = rand(-1f, 1f);
        z[i] = depth;
    }

    @Override
    public void draw(Canvas c, float dt) {
        c.drawColor(0xFF000000);
        float speed = 0.05f + warp * 0.09f;
        float cx = w / 2f, cy = h / 2f, scale = Math.max(w, h) * 0.5f;
        for (int i = 0; i < x.length; i++) {
            z[i] -= speed * dt;
            if (z[i] <= 0.02f) {
                spawn(i, 1f);
            }
            float sx = cx + x[i] / z[i] * scale, sy = cy + y[i] / z[i] * scale;
            if (sx < 0 || sx >= w || sy < 0 || sy >= h) {
                spawn(i, 1f);
                continue;
            }
            float b = Math.min(1f, (1f - z[i]) * 1.3f);
            int g = (int) (b * 255);
            paint.setColor(0xFF000000 | g << 16 | g << 8 | g);
            float r = Math.max(0.6f, (1f - z[i]) * 2.2f * dp);
            c.drawCircle(sx, sy, r, paint);
        }
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, com.rlyles.wep.win.RetroView host) {
        d.add(new Win95.Text("&Warp speed", false)).at(10, top, 280, 22);
        Win95.Slider speed = d.add(new Win95.Slider(0, 10, warp));
        speed.leftLabel = "Slow";
        speed.rightLabel = "Fast";
        speed.at(10, top + 24, 280, 44);
        d.add(new Win95.Text("Starfield &density", false)).at(10, top + 76, 280, 22);
        Win95.Slider dens = d.add(new Win95.Slider(10, 200, density));
        dens.leftLabel = "Low";
        dens.rightLabel = "High";
        dens.at(10, top + 100, 280, 44);
        apply.add(() -> prefs.edit().putInt("warp", speed.value).putInt("density", dens.value).apply());
        return 150;
    }
}
