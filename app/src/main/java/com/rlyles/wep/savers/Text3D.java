package com.rlyles.wep.savers;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 3D Text: solid extruded letters that spin, see-saw, wobble or tumble. Can show the time instead. */
public final class Text3D extends Saver {
    private static final String[] STYLES = {"S&pin", "S&ee-saw", "&Wobble", "T&umble"};
    private String text = "Windows 98";
    private boolean clock;
    private int style, speed = 5;
    private float t;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glyphs = new Path(), drawn = new Path();
    private String built = "";
    private float textW, textH;

    @Override
    public String name() {
        return "3D Text";
    }

    @Override
    protected void loadSettings() {
        text = prefs.getString("text", "Windows 98");
        clock = prefs.getBoolean("clock", false);
        style = prefs.getInt("style", 0);
        speed = prefs.getInt("speed", 5);
    }

    @Override
    protected void reset() {
        built = "";
    }

    private void build(String s) {
        if (s.equals(built)) {
            return;
        }
        built = s;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        p.setTextSize(100);
        glyphs.reset();
        p.getTextPath(s, 0, s.length(), 0, 0, glyphs);
        RectF b = new RectF();
        glyphs.computeBounds(b, true);
        textW = Math.max(1, b.width());
        textH = Math.max(1, b.height());
        glyphs.offset(-b.centerX(), -b.centerY());
    }

    @Override
    public void draw(Canvas c, float dt) {
        t += dt * (0.3f + speed * 0.12f);
        c.drawColor(0xFF000000);
        String s = clock ? new SimpleDateFormat("h:mm:ss", Locale.US).format(new Date()) : (text.isEmpty() ? " " : text);
        build(s);
        float ax, ay, az;
        switch (style) {
            case 1: ax = 0; ay = (float) Math.sin(t) * 50; az = 0; break;
            case 2: ax = (float) Math.sin(t * 0.9) * 30; ay = (float) Math.sin(t * 1.3) * 35; az = 0; break;
            case 3: ax = t * 40; ay = t * 55; az = t * 25; break;
            default: ax = 0; ay = t * 60; az = 0; break;
        }
        float scale = Math.min(w * 0.75f / textW, h * 0.55f / textH);
        int layers = 14;
        float depth = textH * 0.35f;
        float yaw = (float) Math.toRadians(ay), pitch = (float) Math.toRadians(ax), roll = (float) Math.toRadians(az);
        float hx = textW / 2, hy = textH / 2;
        float[] src = {-hx, -hy, hx, -hy, hx, hy, -hx, hy};
        float eye = textW * 1.6f;
        // Which end of the extrusion is nearer decides the drawing order.
        float zFront = Mini3D.rotate(new float[] {0, 0, -depth / 2}, yaw, pitch, roll)[2];
        float zBack = Mini3D.rotate(new float[] {0, 0, depth / 2}, yaw, pitch, roll)[2];
        boolean frontNearer = zFront < zBack;
        float hue = t * 0.05f;
        int face = hueColor(hue, 1f), side = hueColor(hue, 0.45f);
        for (int i = 0; i <= layers; i++) {
            int k = frontNearer ? layers - i : i;
            float z = -depth / 2 + depth * k / layers;
            float[] dst = new float[8];
            for (int corner = 0; corner < 4; corner++) {
                float[] p = Mini3D.rotate(new float[] {src[corner * 2], src[corner * 2 + 1], z}, yaw, pitch, roll);
                float f = eye / (p[2] + eye);
                dst[corner * 2] = w / 2f + p[0] * f * scale;
                dst[corner * 2 + 1] = h / 2f + p[1] * f * scale;
            }
            matrix.setPolyToPoly(src, 0, dst, 0, 4);
            glyphs.transform(matrix, drawn);
            boolean cap = i == layers;
            if (cap) {
                RectF b = new RectF();
                drawn.computeBounds(b, true);
                paint.setShader(new LinearGradient(0, b.top, 0, Math.max(b.top + 1, b.bottom), 0xFFFFFFFF, face,
                        Shader.TileMode.CLAMP));
            } else {
                paint.setShader(null);
                paint.setColor(side);
            }
            c.drawPath(drawn, paint);
        }
        paint.setShader(null);
    }

    private static int hueColor(float h, float v) {
        float h6 = (h % 1 + 1) % 1 * 6;
        int i = (int) h6;
        float f = h6 - i;
        float[][] tab = {{1, f, 0}, {1 - f, 1, 0}, {0, 1, f}, {0, 1 - f, 1}, {f, 0, 1}, {1, 0, 1 - f}};
        float[] c = tab[i % 6];
        return 0xFF000000 | (int) (c[0] * 255 * v) << 16 | (int) (c[1] * 255 * v) << 8 | (int) (c[2] * 255 * v);
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, RetroView host) {
        d.add(new Win95.Text("&Text:", false)).at(10, top + 6, 60, 26);
        Win95.Edit edit = d.add(new Win95.Edit(text));
        edit.at(70, top, 220, 32);
        edit.onEdit = () -> host.prompt("3D Text", "Text to display:", edit.value, false, 20, v -> edit.value = v);
        Win95.Check time = d.add(new Win95.Check("&Display the time instead", clock));
        time.at(10, top + 40, 280, 30);
        d.add(new Win95.Group("Rotation")).at(10, top + 76, 280, 150);
        for (int i = 0; i < STYLES.length; i++) {
            d.add(new Win95.Radio(STYLES[i], 0, style == i)).at(22, top + 102 + i * 30, 250, 28);
        }
        d.add(new Win95.Text("&Speed", false)).at(10, top + 234, 280, 22);
        Win95.Slider sp = d.add(new Win95.Slider(0, 10, speed));
        sp.leftLabel = "Slow";
        sp.rightLabel = "Fast";
        sp.at(10, top + 256, 280, 44);
        apply.add(() -> prefs.edit().putString("text", edit.value).putBoolean("clock", time.checked)
                .putInt("style", Win95.Radio.selected(d, 0)).putInt("speed", sp.value).apply());
        return 306;
    }
}
