package com.rlyles.wep.savers;

import android.graphics.Canvas;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D Flower Box: a solid that blooms into spikes and folds back again while it tumbles, its faces
 * cycling through colours.
 */
public final class FlowerBox extends Saver {
    private static final String[] SHAPES = {"&Cube", "&Tetrahedron", "&Octahedron"};
    private int shape;
    private int speed = 5;
    private float t, yaw, pitch, roll;
    private final Mini3D view = new Mini3D();
    /** Faces as lists of corner points. */
    private final List<float[][]> faces = new ArrayList<>();

    @Override
    public String name() {
        return "3D Flower Box";
    }

    @Override
    protected void loadSettings() {
        shape = prefs.getInt("shape", 0);
        speed = prefs.getInt("speed", 5);
    }

    @Override
    protected void reset() {
        view.viewport(w, h);
        view.camera = 4.2f;
        faces.clear();
        if (shape == 1) {
            float[][] v = {{1, 1, 1}, {-1, -1, 1}, {-1, 1, -1}, {1, -1, -1}};
            int[][] f = {{0, 1, 2}, {0, 3, 1}, {0, 2, 3}, {1, 3, 2}};
            for (int[] face : f) {
                faces.add(new float[][] {v[face[0]], v[face[1]], v[face[2]]});
            }
        } else if (shape == 2) {
            float[][] v = {{1.3f, 0, 0}, {-1.3f, 0, 0}, {0, 1.3f, 0}, {0, -1.3f, 0}, {0, 0, 1.3f}, {0, 0, -1.3f}};
            int[][] f = {{0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}};
            for (int[] face : f) {
                faces.add(new float[][] {v[face[0]], v[face[1]], v[face[2]]});
            }
        } else {
            float s = 0.85f;
            float[][] v = {{-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s}, {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
            int[][] f = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {4, 5, 1, 0}, {3, 2, 6, 7}};
            for (int[] face : f) {
                faces.add(new float[][] {v[face[0]], v[face[1]], v[face[2]], v[face[3]]});
            }
        }
    }

    private static int hsv(float hue, float sat, float val) {
        float h6 = (hue % 1 + 1) % 1 * 6;
        int i = (int) h6;
        float f = h6 - i, p = val * (1 - sat), q = val * (1 - sat * f), r = val * (1 - sat * (1 - f));
        float[][] table = {{val, r, p}, {q, val, p}, {p, val, r}, {p, q, val}, {r, p, val}, {val, p, q}};
        float[] c = table[i % 6];
        return 0xFF000000 | (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255);
    }

    @Override
    public void draw(Canvas c, float dt) {
        float sp = 0.3f + speed * 0.12f;
        t += dt * sp;
        yaw += dt * 0.7f * sp;
        pitch += dt * 0.45f * sp;
        roll += dt * 0.25f * sp;
        c.drawColor(0xFF000000);
        // Bloom amount swings from folded in, through flat, to spiky.
        float bloom = (float) Math.sin(t * 1.3) * 0.9f + 0.25f;
        for (int fi = 0; fi < faces.size(); fi++) {
            float[][] f = faces.get(fi);
            float[] centre = new float[3];
            for (float[] p : f) {
                for (int k = 0; k < 3; k++) {
                    centre[k] += p[k] / f.length;
                }
            }
            float len = (float) Math.sqrt(centre[0] * centre[0] + centre[1] * centre[1] + centre[2] * centre[2]);
            float[] tip = new float[3];
            for (int k = 0; k < 3; k++) {
                tip[k] = centre[k] + centre[k] / len * bloom;
            }
            int color = hsv(t * 0.1f + fi / (float) faces.size(), 0.85f, 1f);
            // Each face becomes a fan of triangles meeting at the moving tip, each split again at its edge midpoint.
            for (int e = 0; e < f.length; e++) {
                float[] a = f[e], b = f[(e + 1) % f.length];
                float[] mid = {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
                float ml = (float) Math.sqrt(mid[0] * mid[0] + mid[1] * mid[1] + mid[2] * mid[2]);
                float[] midOut = {mid[0] + mid[0] / ml * bloom * -0.3f, mid[1] + mid[1] / ml * bloom * -0.3f,
                        mid[2] + mid[2] / ml * bloom * -0.3f};
                add(a, midOut, tip, color);
                add(midOut, b, tip, hsv(t * 0.1f + fi / (float) faces.size() + 0.08f, 0.85f, 1f));
            }
        }
        view.flush(c, false);
    }

    private void add(float[] a, float[] b, float[] c, int color) {
        view.add(Mini3D.rotate(a, yaw, pitch, roll), Mini3D.rotate(b, yaw, pitch, roll), Mini3D.rotate(c, yaw, pitch, roll),
                color);
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, RetroView host) {
        d.add(new Win95.Group("Shape")).at(10, top, 280, 120);
        for (int i = 0; i < SHAPES.length; i++) {
            d.add(new Win95.Radio(SHAPES[i], 0, shape == i)).at(22, top + 26 + i * 30, 250, 28);
        }
        d.add(new Win95.Text("&Speed", false)).at(10, top + 128, 280, 22);
        Win95.Slider sp = d.add(new Win95.Slider(0, 10, speed));
        sp.leftLabel = "Slow";
        sp.rightLabel = "Fast";
        sp.at(10, top + 150, 280, 44);
        apply.add(() -> prefs.edit().putInt("shape", Win95.Radio.selected(d, 0)).putInt("speed", sp.value).apply());
        return 200;
    }
}
