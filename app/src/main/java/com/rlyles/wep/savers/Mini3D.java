package com.rlyles.wep.savers;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Just enough 3D for the OpenGL-style screen savers: rotation, perspective projection and
 * flat-shaded triangles drawn back to front.
 */
final class Mini3D {
    /** A triangle in view space with its base colour. */
    static final class Tri {
        final float[] a, b, c;
        final int color;
        float depth;

        Tri(float[] a, float[] b, float[] c, int color) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.color = color;
            depth = (a[2] + b[2] + c[2]) / 3;
        }
    }

    final List<Tri> tris = new ArrayList<>();
    float cx, cy, focal, camera = 4f;
    /** Light direction (normalised), pointing from the scene toward the light. */
    float lx = -0.4f, ly = -0.6f, lz = -0.7f;
    float ambient = 0.35f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    void viewport(int w, int h) {
        cx = w / 2f;
        cy = h / 2f;
        focal = Math.min(w, h) * 0.9f;
        float n = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= n;
        ly /= n;
        lz /= n;
    }

    /** Rotates a point by yaw (about Y), pitch (about X) and roll (about Z). */
    static float[] rotate(float[] p, float yaw, float pitch, float roll) {
        float x = p[0], y = p[1], z = p[2];
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float x1 = x * c + z * s, z1 = -x * s + z * c;
        c = (float) Math.cos(pitch);
        s = (float) Math.sin(pitch);
        float y1 = y * c - z1 * s, z2 = y * s + z1 * c;
        c = (float) Math.cos(roll);
        s = (float) Math.sin(roll);
        return new float[] {x1 * c - y1 * s, x1 * s + y1 * c, z2};
    }

    void add(float[] a, float[] b, float[] c, int color) {
        tris.add(new Tri(a, b, c, color));
    }

    float sx(float[] p) {
        return cx + p[0] * focal / (p[2] + camera);
    }

    float sy(float[] p) {
        return cy + p[1] * focal / (p[2] + camera);
    }

    /** Draws and clears the queued triangles, furthest first, shaded by their facing. */
    void flush(Canvas canvas, boolean cullBack) {
        Collections.sort(tris, (t1, t2) -> Float.compare(t2.depth, t1.depth));
        for (Tri t : tris) {
            float ux = t.b[0] - t.a[0], uy = t.b[1] - t.a[1], uz = t.b[2] - t.a[2];
            float vx = t.c[0] - t.a[0], vy = t.c[1] - t.a[1], vz = t.c[2] - t.a[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len == 0) {
                continue;
            }
            nx /= len;
            ny /= len;
            nz /= len;
            // Facing the camera means the normal points toward negative z (view direction is +z).
            float toCam = -(nx * t.a[0] + ny * t.a[1] + nz * (t.a[2] + camera));
            if (toCam < 0) {
                if (cullBack) {
                    continue;
                }
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float lambert = Math.max(0, nx * lx + ny * ly + nz * lz);
            float k = Math.min(1f, ambient + (1 - ambient) * lambert);
            int r = (int) (((t.color >> 16) & 0xFF) * k), g = (int) (((t.color >> 8) & 0xFF) * k),
                    b = (int) ((t.color & 0xFF) * k);
            paint.setColor(0xFF000000 | r << 16 | g << 8 | b);
            path.reset();
            path.moveTo(sx(t.a), sy(t.a));
            path.lineTo(sx(t.b), sy(t.b));
            path.lineTo(sx(t.c), sy(t.c));
            path.close();
            canvas.drawPath(path, paint);
        }
        tris.clear();
    }
}
