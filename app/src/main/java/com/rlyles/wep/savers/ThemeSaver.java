package com.rlyles.wep.savers;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.List;
import java.util.Random;

/**
 * The Plus! 98 theme screen savers (Baseball, Jungle, Space, Underwater and the rest). The
 * originals showed each theme's pictures; here each theme is an original animated scene: a
 * painted background, themed decorations and drifting picture sprites.
 */
public final class ThemeSaver extends Saver {
    static final int DRIFT = 0, SWIM = 1, RISE = 2, FALL = 3, BOUNCE = 4, ORBIT = 5, ZOOM = 6, ARC = 7;

    interface Decor {
        void paint(Canvas c, int w, int h, float t, Paint p);
    }

    private final String title;
    private final int topColor, bottomColor;
    private final String[] glyphs;
    private final int motion;
    private final Decor decor;
    private final int titleColor;

    private int speed = 5;
    private boolean showTitle = true;
    private float t;
    private float[][] sprites; // x, y, vx, vy, size, glyph, phase, z
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emoji = new Paint(Paint.ANTI_ALIAS_FLAG);

    ThemeSaver(String title, int top, int bottom, int titleColor, int motion, Decor decor, String... glyphs) {
        this.title = title;
        this.topColor = top;
        this.bottomColor = bottom;
        this.titleColor = titleColor;
        this.motion = motion;
        this.decor = decor;
        this.glyphs = glyphs;
        emoji.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public String name() {
        return title;
    }

    @Override
    protected void loadSettings() {
        speed = prefs.getInt("speed", 5);
        showTitle = prefs.getBoolean("title", true);
    }

    @Override
    protected void reset() {
        int n = motion == ZOOM ? 10 : motion == ORBIT ? 7 : 9;
        sprites = new float[n][];
        for (int i = 0; i < n; i++) {
            sprites[i] = spawn(i, true);
        }
    }

    private float base() {
        return Math.min(w, h) * 0.11f;
    }

    private float[] spawn(int i, boolean anywhere) {
        float size = base() * rand(0.7f, 1.3f);
        float sp = (0.4f + speed * 0.1f) * Math.min(w, h) * 0.12f;
        float[] s = new float[8];
        s[4] = size;
        s[5] = rnd.nextInt(glyphs.length);
        s[6] = rand(0, 6.28f);
        switch (motion) {
            case SWIM: {
                boolean right = rnd.nextBoolean();
                s[0] = anywhere ? rand(0, w) : right ? -size : w + size;
                s[1] = rand(size, h - size);
                s[2] = (right ? 1 : -1) * sp * rand(0.6f, 1.2f);
                break;
            }
            case RISE:
                s[0] = rand(0, w);
                s[1] = anywhere ? rand(0, h) : h + size;
                s[3] = -sp * rand(0.5f, 1f);
                break;
            case FALL:
                s[0] = rand(0, w);
                s[1] = anywhere ? rand(0, h) : -size;
                s[3] = sp * rand(0.4f, 0.8f);
                break;
            case BOUNCE:
                s[0] = rand(size, Math.max(size + 1, w - size));
                s[1] = rand(size, Math.max(size + 1, h - size));
                s[2] = (rnd.nextBoolean() ? 1 : -1) * sp * rand(0.7f, 1.3f);
                s[3] = (rnd.nextBoolean() ? 1 : -1) * sp * rand(0.7f, 1.3f);
                break;
            case ORBIT:
                s[0] = rand(0.2f, 0.45f); // radius as a fraction of the short side
                s[1] = i * 6.28f / 7;
                s[2] = rand(0.3f, 0.7f) * (rnd.nextBoolean() ? 1 : -1);
                break;
            case ZOOM:
                s[0] = rand(-1, 1);
                s[1] = rand(-1, 1);
                s[7] = anywhere ? rand(0.2f, 1f) : 1f;
                break;
            case ARC: {
                boolean fromLeft = rnd.nextBoolean();
                s[0] = fromLeft ? -size : w + size;
                s[1] = h * rand(0.6f, 0.95f);
                s[2] = (fromLeft ? 1 : -1) * w * rand(0.25f, 0.45f) * (0.6f + speed * 0.08f);
                s[3] = -h * rand(0.7f, 1.1f) * (0.6f + speed * 0.08f);
                if (anywhere) {
                    s[0] = rand(0, w);
                }
                break;
            }
            default: // DRIFT
                s[0] = anywhere ? rand(0, w) : -size;
                s[1] = rand(size, h - size);
                s[2] = sp * rand(0.4f, 1f);
                break;
        }
        return s;
    }

    @Override
    public void draw(Canvas c, float dt) {
        t += dt * (0.5f + speed * 0.1f);
        paint.setShader(new LinearGradient(0, 0, 0, h, topColor, bottomColor, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        if (decor != null) {
            decor.paint(c, w, h, t, paint);
        }
        for (int i = 0; i < sprites.length; i++) {
            float[] s = sprites[i];
            float x, y, size = s[4];
            switch (motion) {
                case SWIM:
                    s[0] += s[2] * dt;
                    x = s[0];
                    y = s[1] + (float) Math.sin(t * 2 + s[6]) * size * 0.3f;
                    if (x < -size * 2 || x > w + size * 2) {
                        sprites[i] = spawn(i, false);
                    }
                    break;
                case RISE:
                case FALL:
                    s[1] += s[3] * dt;
                    x = s[0] + (float) Math.sin(t * 1.5 + s[6]) * size * 0.5f;
                    y = s[1];
                    if (y < -size * 2 || y > h + size * 2) {
                        sprites[i] = spawn(i, false);
                    }
                    break;
                case BOUNCE:
                    s[0] += s[2] * dt;
                    s[1] += s[3] * dt;
                    if (s[0] < size / 2 || s[0] > w - size / 2) {
                        s[2] = -s[2];
                        s[0] = Math.max(size / 2, Math.min(w - size / 2, s[0]));
                    }
                    if (s[1] < size / 2 || s[1] > h - size / 2) {
                        s[3] = -s[3];
                        s[1] = Math.max(size / 2, Math.min(h - size / 2, s[1]));
                    }
                    x = s[0];
                    y = s[1];
                    break;
                case ORBIT:
                    s[1] += s[2] * dt * (0.5f + speed * 0.1f);
                    x = w / 2f + (float) Math.cos(s[1]) * s[0] * Math.min(w, h);
                    y = h / 2f + (float) Math.sin(s[1]) * s[0] * Math.min(w, h) * 0.8f;
                    break;
                case ZOOM:
                    s[7] -= dt * (0.08f + speed * 0.03f);
                    if (s[7] <= 0.08f) {
                        sprites[i] = spawn(i, false);
                    }
                    x = w / 2f + s[0] / s[7] * w * 0.3f;
                    y = h / 2f + s[1] / s[7] * h * 0.3f;
                    size = s[4] * 0.35f / s[7];
                    break;
                case ARC:
                    s[3] += h * 1.2f * dt * (0.6f + speed * 0.08f);
                    s[0] += s[2] * dt;
                    s[1] += s[3] * dt;
                    x = s[0];
                    y = s[1];
                    if (y > h + size * 2 || x < -size * 3 || x > w + size * 3) {
                        sprites[i] = spawn(i, false);
                    }
                    break;
                default:
                    s[0] += s[2] * dt;
                    x = s[0];
                    y = s[1] + (float) Math.sin(t + s[6]) * size * 0.2f;
                    if (x > w + size * 2) {
                        sprites[i] = spawn(i, false);
                    }
                    break;
            }
            emoji.setTextSize(Math.max(4, size));
            String g = glyphs[(int) s[5]];
            c.save();
            if (motion == SWIM && s[2] > 0) {
                c.scale(-1, 1, x, y);
            }
            Paint.FontMetrics fm = emoji.getFontMetrics();
            c.drawText(g, x, y - (fm.ascent + fm.descent) / 2, emoji);
            c.restore();
        }
        if (showTitle) {
            float a = (float) (0.5 + 0.5 * Math.sin(t * 0.3));
            paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
            paint.setTextSize(Math.min(w, h) * 0.08f);
            paint.setColor(titleColor);
            paint.setAlpha((int) (80 + a * 150));
            paint.setTextAlign(Paint.Align.RIGHT);
            c.drawText(title, w - Math.min(w, h) * 0.05f, h - Math.min(w, h) * 0.05f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setAlpha(255);
            paint.setTypeface(Typeface.DEFAULT);
        }
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, RetroView host) {
        Win95.Check name = d.add(new Win95.Check("Show the theme &name", showTitle));
        name.at(10, top, 280, 30);
        d.add(new Win95.Text("&Speed", false)).at(10, top + 40, 280, 22);
        Win95.Slider sp = d.add(new Win95.Slider(0, 10, speed));
        sp.leftLabel = "Slow";
        sp.rightLabel = "Fast";
        sp.at(10, top + 62, 280, 44);
        apply.add(() -> prefs.edit().putBoolean("title", name.checked).putInt("speed", sp.value).apply());
        return 110;
    }

    // ---------------------------------------------------------------- the themes

    static Saver create(String id) {
        switch (id) {
            case "baseball":
                return new ThemeSaver("Baseball", 0xFF70B0FF, 0xFF208020, 0xFFFFFFFF, ARC, ThemeSaver::diamond,
                        "⚾", "🏆", "⚾", "🎫");
            case "creatures":
                return new ThemeSaver("Dangerous Creatures", 0xFF203010, 0xFF000000, 0xFFFFC040, SWIM, ThemeSaver::eyes,
                        "🐍", "🐊", "🐅", "🐝", "🐙", "🐺");
            case "computer":
                return new ThemeSaver("Inside your Computer", 0xFF0A4A20, 0xFF02200A, 0xFF80FF80, DRIFT, ThemeSaver::circuit,
                        "💾", "💻", "💿", "🖱", "📟", "🔌");
            case "jungle":
                return new ThemeSaver("Jungle", 0xFF60C040, 0xFF103010, 0xFFFFFF80, SWIM, ThemeSaver::vines,
                        "🐒", "🐦", "🐸", "🌺", "🐍", "🐆");
            case "leonardo":
                return new ThemeSaver("Leonardo da Vinci", 0xFFEAD8A8, 0xFFB89860, 0xFF603010, DRIFT, ThemeSaver::sketch,
                        "✒", "⚙", "🎨", "🖼", "📜");
            case "mystery":
                return new ThemeSaver("Mystery", 0xFF2A0A40, 0xFF000000, 0xFFC080FF, FALL, ThemeSaver::fog,
                        "🔍", "🔑", "🕯", "❓", "🎩", "👣");
            case "nature":
                return new ThemeSaver("Nature", 0xFF80C8FF, 0xFF40A030, 0xFFFFFFFF, FALL, ThemeSaver::hills,
                        "🌸", "🍁", "🐝", "🍃", "🌻", "🐞");
            case "science":
                return new ThemeSaver("Science", 0xFF0A1A50, 0xFF000010, 0xFF80E0FF, ORBIT, ThemeSaver::atom,
                        "🔬", "⚗", "🔭", "💡", "🔋", "📡");
            case "space":
                return new ThemeSaver("Space", 0xFF000010, 0xFF100020, 0xFFFFFFFF, ZOOM, ThemeSaver::stars,
                        "🚀", "🌍", "🌙", "☄", "🛰", "⭐");
            case "sports":
                return new ThemeSaver("Sports", 0xFF208030, 0xFF105020, 0xFFFFFFFF, BOUNCE, ThemeSaver::field,
                        "🏈", "🏀", "⚽", "🎾", "🏐", "⛳");
            case "sixties":
                return new ThemeSaver("The 60's USA", 0xFFFF60A0, 0xFFFFC020, 0xFFFFFFFF, BOUNCE, ThemeSaver::groovy,
                        "☮", "🌼", "🎸", "🌈", "✌", "🚐");
            case "golden":
                return new ThemeSaver("The Golden Era", 0xFF101010, 0xFF302008, 0xFFFFD060, DRIFT, ThemeSaver::deco,
                        "🎩", "🎷", "🎬", "🍸", "📻", "✈");
            case "travel":
                return new ThemeSaver("Travel", 0xFFE8DCB0, 0xFFC8B880, 0xFF404080, DRIFT, ThemeSaver::map,
                        "✈", "🎒", "🚢", "🗺", "🚂", "🗽");
            case "underwater":
                return new ThemeSaver("Underwater", 0xFF1070C0, 0xFF001040, 0xFFA0E0FF, SWIM, ThemeSaver::sea,
                        "🐠", "🐟", "🐡", "🐙", "🐢", "🐬");
            default:
                return new ThemeSaver("Windows 98", 0xFF2060C0, 0xFF90C8FF, 0xFFFFFFFF, DRIFT, ThemeSaver::clouds,
                        "💻", "📁", "🌐", "✉", "💾", "🖥");
        }
    }

    // ---------------------------------------------------------------- decorations

    private static void diamond(Canvas c, int w, int h, float t, Paint p) {
        float cx = w / 2f, cy = h * 0.72f, s = Math.min(w, h) * 0.32f;
        p.setColor(0xFFC08050);
        Path infield = new Path();
        infield.moveTo(cx, cy - s);
        infield.lineTo(cx + s, cy);
        infield.lineTo(cx, cy + s * 0.6f);
        infield.lineTo(cx - s, cy);
        infield.close();
        c.drawPath(infield, p);
        p.setColor(0xFF30A030);
        c.drawCircle(cx, cy - s * 0.2f, s * 0.35f, p);
        p.setColor(0xFFFFFFFF);
        float b = s * 0.07f;
        c.drawRect(cx - b, cy - s - b, cx + b, cy - s + b, p);
        c.drawRect(cx + s - b, cy - b, cx + s + b, cy + b, p);
        c.drawRect(cx - s - b, cy - b, cx - s + b, cy + b, p);
        c.drawRect(cx - b, cy + s * 0.6f - b, cx + b, cy + s * 0.6f + b, p);
    }

    private static void eyes(Canvas c, int w, int h, float t, Paint p) {
        Random r = new Random(5);
        for (int i = 0; i < 9; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = Math.min(w, h) * 0.012f;
            float blink = (float) Math.sin(t * 0.7 + i * 1.7);
            if (blink < 0.2f) {
                continue;
            }
            p.setColor(0xC0FFE040);
            c.drawOval(x - s * 2, y - s * blink, x, y + s * blink, p);
            c.drawOval(x + s, y - s * blink, x + s * 3, y + s * blink, p);
        }
    }

    private static void circuit(Canvas c, int w, int h, float t, Paint p) {
        Random r = new Random(9);
        p.setStrokeWidth(Math.max(1.5f, w / 300f));
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 30; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, len = r.nextFloat() * w * 0.3f;
            p.setColor(0x80C0A040);
            c.drawLine(x, y, x + len, y, p);
            c.drawLine(x + len, y, x + len + len * 0.3f, y + len * 0.3f, p);
            float pulse = (t * 0.5f + i * 0.13f) % 1;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF80FF80);
            c.drawCircle(x + len * pulse, y, p.getStrokeWidth() * 1.6f, p);
            p.setStyle(Paint.Style.STROKE);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 6; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = Math.min(w, h) * 0.06f;
            p.setColor(0xFF202020);
            c.drawRect(x, y, x + s * 1.6f, y + s, p);
        }
    }

    private static void vines(Canvas c, int w, int h, float t, Paint p) {
        Random r = new Random(3);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, w / 150f));
        for (int i = 0; i < 8; i++) {
            float x = r.nextFloat() * w, len = h * (0.3f + r.nextFloat() * 0.5f);
            float sway = (float) Math.sin(t * 0.8 + i) * w * 0.02f;
            p.setColor(0xFF205010);
            Path v = new Path();
            v.moveTo(x, 0);
            v.quadTo(x + sway * 2, len / 2, x + sway, len);
            c.drawPath(v, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void sketch(Canvas c, int w, int h, float t, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, w / 300f));
        p.setColor(0x80603010);
        float cx = w / 2f, cy = h / 2f, s = Math.min(w, h) * 0.38f;
        c.drawCircle(cx, cy, s, p);
        c.drawRect(cx - s * 0.88f, cy - s * 0.7f, cx + s * 0.88f, cy + s, p);
        c.save();
        c.rotate(t * 10, w * 0.2f, h * 0.25f);
        for (int i = 0; i < 12; i++) {
            c.save();
            c.rotate(i * 30, w * 0.2f, h * 0.25f);
            c.drawRect(w * 0.2f - s * 0.04f, h * 0.25f - s * 0.3f, w * 0.2f + s * 0.04f, h * 0.25f - s * 0.22f, p);
            c.restore();
        }
        c.drawCircle(w * 0.2f, h * 0.25f, s * 0.22f, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private static void fog(Canvas c, int w, int h, float t, Paint p) {
        for (int i = 0; i < 6; i++) {
            float x = (float) ((Math.sin(t * 0.13 + i * 2.1) * 0.5 + 0.5) * w);
            float y = (float) ((Math.cos(t * 0.11 + i * 1.3) * 0.5 + 0.5) * h);
            p.setColor(0x20C0A0FF);
            c.drawCircle(x, y, Math.min(w, h) * 0.3f, p);
        }
    }

    private static void hills(Canvas c, int w, int h, float t, Paint p) {
        p.setColor(0xFFFFF060);
        c.drawCircle(w * 0.8f, h * 0.2f, Math.min(w, h) * 0.1f, p);
        p.setColor(0xFF308020);
        c.drawOval(new RectF(-w * 0.3f, h * 0.6f, w * 0.7f, h * 1.4f), p);
        p.setColor(0xFF40A030);
        c.drawOval(new RectF(w * 0.3f, h * 0.65f, w * 1.3f, h * 1.5f), p);
    }

    private static void atom(Canvas c, int w, int h, float t, Paint p) {
        float cx = w / 2f, cy = h / 2f, s = Math.min(w, h) * 0.18f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, w / 300f));
        p.setColor(0x8080E0FF);
        for (int i = 0; i < 3; i++) {
            c.save();
            c.rotate(i * 60, cx, cy);
            c.drawOval(new RectF(cx - s, cy - s * 0.35f, cx + s, cy + s * 0.35f), p);
            float a = t * 2 + i * 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx + (float) Math.cos(a) * s, cy + (float) Math.sin(a) * s * 0.35f, s * 0.06f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0x8080E0FF);
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF6040);
        c.drawCircle(cx, cy, s * 0.12f, p);
    }

    private static void stars(Canvas c, int w, int h, float t, Paint p) {
        Random r = new Random(11);
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 120; i++) {
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 2 + i));
            p.setAlpha((int) (120 + tw * 135));
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, Math.max(1, Math.min(w, h) / 400f) * (1 + r.nextFloat()), p);
        }
        p.setAlpha(255);
        float px = w * 0.2f, py = h * 0.75f, s = Math.min(w, h) * 0.12f;
        p.setColor(0xFFC08040);
        c.drawCircle(px, py, s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.12f);
        p.setColor(0xFFE0C080);
        c.drawOval(new RectF(px - s * 1.8f, py - s * 0.35f, px + s * 1.8f, py + s * 0.35f), p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void field(Canvas c, int w, int h, float t, Paint p) {
        p.setColor(0x40FFFFFF);
        for (int i = 1; i < 10; i++) {
            c.drawRect(w * i / 10f - 1.5f, 0, w * i / 10f + 1.5f, h, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        c.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.18f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void groovy(Canvas c, int w, int h, float t, Paint p) {
        int[] cols = {0x60FF0080, 0x60FF8000, 0x60FFFF00, 0x6000FF80, 0x600080FF, 0x608000FF};
        float r = Math.max(w, h);
        for (int i = 12; i > 0; i--) {
            p.setColor(cols[(i + (int) (t * 2)) % cols.length]);
            c.drawCircle(w / 2f, h / 2f, r * i / 12f, p);
        }
    }

    private static void deco(Canvas c, int w, int h, float t, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, w / 250f));
        float cx = w / 2f, cy = h;
        for (int i = 0; i < 18; i++) {
            double a = Math.PI + i * Math.PI / 17;
            float shimmer = (float) (0.5 + 0.5 * Math.sin(t * 1.5 + i));
            p.setColor(0x00D4A040 | ((int) (60 + shimmer * 120) << 24));
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * w, cy + (float) Math.sin(a) * w, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void map(Canvas c, int w, int h, float t, Paint p) {
        Random r = new Random(4);
        p.setColor(0x40806040);
        for (int i = 0; i < 5; i++) {
            c.drawOval(new RectF(r.nextFloat() * w - w * 0.1f, r.nextFloat() * h - h * 0.1f,
                    r.nextFloat() * w + w * 0.2f, r.nextFloat() * h + h * 0.2f), p);
        }
        p.setColor(0xA0A02020);
        float dash = Math.min(w, h) * 0.02f;
        for (float x = 0; x < w; x += dash * 2) {
            float y = h * 0.5f + (float) Math.sin(x / w * 6) * h * 0.2f;
            if (((int) (x / dash / 2 + t * 2)) % 2 == 0) {
                c.drawCircle(x, y, dash * 0.3f, p);
            }
        }
        float s = Math.min(w, h) * 0.08f, cx = w * 0.12f, cy = h * 0.18f;
        p.setColor(0xFF404080);
        Path rose = new Path();
        rose.moveTo(cx, cy - s);
        rose.lineTo(cx + s * 0.2f, cy);
        rose.lineTo(cx, cy + s);
        rose.lineTo(cx - s * 0.2f, cy);
        rose.close();
        c.drawPath(rose, p);
    }

    private static void sea(Canvas c, int w, int h, float t, Paint p) {
        for (int i = 0; i < 5; i++) {
            float x = (float) ((i + 0.5) * w / 5 + Math.sin(t * 0.3 + i) * w * 0.05);
            p.setColor(0x18FFFFFF);
            Path ray = new Path();
            ray.moveTo(x - w * 0.03f, 0);
            ray.lineTo(x + w * 0.03f, 0);
            ray.lineTo(x + w * 0.12f, h);
            ray.lineTo(x - w * 0.02f, h);
            ray.close();
            c.drawPath(ray, p);
        }
        Random r = new Random(8);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, w / 300f));
        for (int i = 0; i < 25; i++) {
            float x = r.nextFloat() * w, speed = 0.5f + r.nextFloat();
            float y = h - ((t * speed * h * 0.15f + r.nextFloat() * h) % h);
            p.setColor(0x90FFFFFF);
            c.drawCircle(x + (float) Math.sin(t * 2 + i) * 5, y, Math.min(w, h) * 0.01f * (1 + r.nextFloat()), p);
        }
        p.setStrokeWidth(Math.max(3f, w / 120f));
        for (int i = 0; i < 7; i++) {
            float x = r.nextFloat() * w;
            p.setColor(0xFF208040);
            Path weed = new Path();
            weed.moveTo(x, h);
            weed.quadTo(x + (float) Math.sin(t + i) * w * 0.04f, h * 0.85f, x + (float) Math.sin(t * 1.3 + i) * w * 0.03f,
                    h * (0.65f + r.nextFloat() * 0.15f));
            c.drawPath(weed, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void clouds(Canvas c, int w, int h, float t, Paint p) {
        Random r = new Random(2);
        p.setColor(0xD0FFFFFF);
        for (int i = 0; i < 6; i++) {
            float speed = 0.02f + r.nextFloat() * 0.03f;
            float x = ((r.nextFloat() + t * speed) % 1.3f - 0.15f) * w, y = r.nextFloat() * h * 0.8f;
            float s = Math.min(w, h) * (0.08f + r.nextFloat() * 0.06f);
            c.drawCircle(x, y, s, p);
            c.drawCircle(x + s * 0.9f, y + s * 0.2f, s * 0.8f, p);
            c.drawCircle(x - s * 0.9f, y + s * 0.25f, s * 0.7f, p);
            c.drawRect(x - s * 0.9f, y + s * 0.2f, x + s * 0.9f, y + s * 0.8f, p);
        }
    }
}
