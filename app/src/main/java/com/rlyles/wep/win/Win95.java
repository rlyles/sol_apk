package com.rlyles.wep.win;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny Windows 95 look-alike toolkit drawn straight onto a Canvas: 3D bevels, title bars,
 * push buttons, check boxes, radio buttons, group boxes, list boxes, sliders and modal dialogs.
 * Sizes are scaled up from the 96 dpi originals so everything is comfortable to touch.
 */
public final class Win95 {
    public static final int FACE = 0xFFC0C0C0, LIGHT = 0xFFDFDFDF, HILITE = 0xFFFFFFFF, SHADOW = 0xFF808080,
            DARK = 0xFF000000, TITLE = 0xFF000080, TITLE_TEXT = 0xFFFFFFFF, SELECT = 0xFF000080,
            INACTIVE_TITLE = 0xFF808080, DESKTOP = 0xFF008080;

    /** Device pixels per dp. */
    public final float dp;
    /** Width of one bevel line. */
    public final float px;
    public final Paint fill = new Paint();
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Typeface regular = Typeface.SANS_SERIF;
    public final Typeface bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private final Paint line = new Paint();
    private final Paint aa = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    public Win95(float density) {
        dp = density;
        px = Math.max(1f, Math.round(density * 0.75f));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(px);
        text.setTypeface(regular);
        text.setTextSize(15 * dp);
    }

    // ---------------------------------------------------------------- bevels

    private void edge(Canvas c, RectF r, int topLeft, int bottomRight, float inset) {
        float l = r.left + inset, t = r.top + inset, rr = r.right - inset, b = r.bottom - inset;
        fill.setColor(topLeft);
        c.drawRect(l, t, rr, t + px, fill);
        c.drawRect(l, t, l + px, b, fill);
        fill.setColor(bottomRight);
        c.drawRect(l, b - px, rr, b, fill);
        c.drawRect(rr - px, t, rr, b, fill);
    }

    /** Raised panel such as a window frame or an unpressed button. */
    public void raised(Canvas c, RectF r) {
        fill.setColor(FACE);
        c.drawRect(r, fill);
        edge(c, r, LIGHT, DARK, 0);
        edge(c, r, HILITE, SHADOW, px);
    }

    /** Sunken well such as an edit field or a list box. */
    public void sunken(Canvas c, RectF r, int faceColor) {
        fill.setColor(faceColor);
        c.drawRect(r, fill);
        edge(c, r, SHADOW, HILITE, 0);
        edge(c, r, DARK, LIGHT, px);
    }

    /** One-pixel sunken edge used by status bar panels. */
    public void shallow(Canvas c, RectF r) {
        edge(c, r, SHADOW, HILITE, 0);
    }

    /** One-pixel raised edge. */
    public void shallowRaised(Canvas c, RectF r) {
        edge(c, r, HILITE, SHADOW, 0);
    }

    /** Pressed-in button look (a taskbar button for the active window). */
    public void pressedPanel(Canvas c, RectF r, int faceColor) {
        fill.setColor(faceColor);
        c.drawRect(r, fill);
        edge(c, r, DARK, HILITE, 0);
        edge(c, r, SHADOW, LIGHT, px);
    }

    // ---------------------------------------------------------------- text

    /** Draws text where '&amp;' marks the next character as the underlined mnemonic. */
    public void label(Canvas c, String s, float x, float baseline, int color) {
        int amp = s.indexOf('&');
        String plain = amp >= 0 ? s.substring(0, amp) + s.substring(amp + 1) : s;
        text.setColor(color);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(plain, x, baseline, text);
        if (amp >= 0 && amp < plain.length()) {
            float x0 = x + text.measureText(plain, 0, amp);
            float x1 = x + text.measureText(plain, 0, amp + 1);
            fill.setColor(color);
            float y = baseline + 2 * px;
            c.drawRect(x0, y, x1, y + px, fill);
        }
    }

    /** Win95 disabled text: grey with a white emboss. */
    public void disabledLabel(Canvas c, String s, float x, float baseline) {
        label(c, s, x + px, baseline + px, HILITE);
        label(c, s, x, baseline, SHADOW);
    }

    public float textWidth(String s) {
        return text.measureText(s.replace("&", ""));
    }

    /** Baseline that vertically centres a line of text in {@code r}. */
    public float baseline(RectF r) {
        Paint.FontMetrics fm = text.getFontMetrics();
        return r.centerY() - (fm.ascent + fm.descent) / 2;
    }

    public void centered(Canvas c, String s, RectF r, int color) {
        label(c, s, r.centerX() - textWidth(s) / 2, baseline(r), color);
    }

    /** Runs {@code body} with a temporary text size (in dp) and typeface. */
    public void withFont(float sizeDp, boolean isBold, Runnable body) {
        float oldSize = text.getTextSize();
        Typeface oldFace = text.getTypeface();
        text.setTextSize(sizeDp * dp);
        text.setTypeface(isBold ? bold : regular);
        body.run();
        text.setTextSize(oldSize);
        text.setTypeface(oldFace);
    }

    // ---------------------------------------------------------------- controls

    public void button(Canvas c, RectF r, String label, boolean pressed, boolean enabled, boolean isDefault) {
        tmp.set(r);
        if (isDefault) {
            line.setColor(DARK);
            c.drawRect(r.left + px / 2, r.top + px / 2, r.right - px / 2, r.bottom - px / 2, line);
            tmp.inset(px, px);
        }
        if (pressed) {
            fill.setColor(FACE);
            c.drawRect(tmp, fill);
            edge(c, tmp, DARK, DARK, 0);
            edge(c, tmp, SHADOW, FACE, px);
        } else {
            raised(c, tmp);
        }
        if (label.isEmpty()) {
            return;
        }
        float off = pressed ? px : 0;
        float oldSize = text.getTextSize();
        float room = tmp.width() - 8 * dp;
        if (textWidth(label) > room && room > 0) {
            text.setTextSize(oldSize * room / textWidth(label));
        }
        float x = tmp.centerX() - textWidth(label) / 2 + off;
        float y = baseline(tmp) + off;
        if (enabled) {
            label(c, label, x, y, DARK);
        } else {
            disabledLabel(c, label, x, y);
        }
        text.setTextSize(oldSize);
    }

    public void checkbox(Canvas c, RectF box, boolean checked, boolean enabled) {
        sunken(c, box, enabled ? HILITE : FACE);
        if (checked) {
            aa.setStyle(Paint.Style.STROKE);
            aa.setStrokeWidth(box.width() * 0.14f);
            aa.setStrokeCap(Paint.Cap.SQUARE);
            aa.setColor(enabled ? DARK : SHADOW);
            Path tick = new Path();
            float w = box.width();
            tick.moveTo(box.left + w * 0.25f, box.top + w * 0.5f);
            tick.lineTo(box.left + w * 0.43f, box.top + w * 0.68f);
            tick.lineTo(box.left + w * 0.75f, box.top + w * 0.3f);
            c.drawPath(tick, aa);
            aa.setStyle(Paint.Style.FILL);
        }
    }

    public void radio(Canvas c, float cx, float cy, float r, boolean checked, boolean enabled) {
        aa.setStyle(Paint.Style.STROKE);
        aa.setStrokeWidth(px);
        RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
        aa.setColor(SHADOW);
        c.drawArc(o, 135, 180, false, aa);
        aa.setColor(HILITE);
        c.drawArc(o, 315, 180, false, aa);
        o.inset(px, px);
        aa.setColor(DARK);
        c.drawArc(o, 135, 180, false, aa);
        aa.setColor(LIGHT);
        c.drawArc(o, 315, 180, false, aa);
        aa.setStyle(Paint.Style.FILL);
        aa.setColor(enabled ? HILITE : FACE);
        c.drawCircle(cx, cy, r - 2 * px, aa);
        if (checked) {
            aa.setColor(enabled ? DARK : SHADOW);
            c.drawCircle(cx, cy, r * 0.38f, aa);
        }
    }

    public void groupBox(Canvas c, RectF r, String label) {
        float top = r.top + text.getTextSize() * 0.55f;
        line.setColor(SHADOW);
        c.drawRect(r.left, top, r.right - px, r.bottom - px, line);
        line.setColor(HILITE);
        c.drawRect(r.left + px, top + px, r.right, r.bottom, line);
        if (label.isEmpty()) {
            return;
        }
        float tw = textWidth(label);
        fill.setColor(FACE);
        c.drawRect(r.left + 6 * dp, r.top, r.left + 14 * dp + tw, top + 2 * px, fill);
        label(c, label, r.left + 10 * dp, r.top + text.getTextSize() * 0.95f, DARK);
    }

    /** Title bar with caption; the close box rectangle is returned through {@code closeOut}. */
    public void titleBar(Canvas c, RectF r, String caption, RectF closeOut, boolean active) {
        fill.setColor(active ? TITLE : INACTIVE_TITLE);
        c.drawRect(r, fill);
        Typeface old = text.getTypeface();
        text.setTypeface(bold);
        label(c, caption, r.left + 6 * dp, baseline(r), active ? TITLE_TEXT : LIGHT);
        text.setTypeface(old);
        if (closeOut != null) {
            float s = r.height() - 6 * dp;
            closeOut.set(r.right - 4 * dp - s * 1.15f, r.top + 3 * dp, r.right - 4 * dp, r.top + 3 * dp + s);
            raised(c, closeOut);
            glyphX(c, closeOut, DARK);
        }
    }

    public void glyphX(Canvas c, RectF r, int color) {
        aa.setColor(color);
        aa.setStrokeWidth(Math.max(2f, r.height() * 0.12f));
        float s = r.height() * 0.22f, cx = r.centerX(), cy = r.centerY();
        c.drawLine(cx - s, cy - s, cx + s, cy + s, aa);
        c.drawLine(cx - s, cy + s, cx + s, cy - s, aa);
    }

    /** Small solid triangle used by scroll bars, spinners and cascading menus. */
    public void arrow(Canvas c, float cx, float cy, float size, int dir, int color) {
        Path p = new Path();
        float s = size / 2;
        switch (dir) {
            case 0: // up
                p.moveTo(cx - s, cy + s / 2);
                p.lineTo(cx + s, cy + s / 2);
                p.lineTo(cx, cy - s / 2);
                break;
            case 1: // right
                p.moveTo(cx - s / 2, cy - s);
                p.lineTo(cx - s / 2, cy + s);
                p.lineTo(cx + s / 2, cy);
                break;
            case 2: // down
                p.moveTo(cx - s, cy - s / 2);
                p.lineTo(cx + s, cy - s / 2);
                p.lineTo(cx, cy + s / 2);
                break;
            default: // left
                p.moveTo(cx + s / 2, cy - s);
                p.lineTo(cx + s / 2, cy + s);
                p.lineTo(cx - s / 2, cy);
                break;
        }
        p.close();
        fill.setColor(color);
        c.drawPath(p, fill);
    }

    /** A Windows-style seven-segment LED readout (Minesweeper counters, etc.). */
    public void led(Canvas c, RectF r, int value, int digits) {
        fill.setColor(0xFF000000);
        c.drawRect(r, fill);
        String s = String.valueOf(Math.max(-99, Math.min(value, (int) Math.pow(10, digits) - 1)));
        while (s.length() < digits) {
            s = (s.startsWith("-") ? "-0" + s.substring(1) : "0" + s);
        }
        float dw = r.width() / digits;
        for (int i = 0; i < digits; i++) {
            segment(c, new RectF(r.left + i * dw, r.top, r.left + (i + 1) * dw, r.bottom), s.charAt(i));
        }
    }

    private static final int[] SEGS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

    private void segment(Canvas c, RectF r, char ch) {
        int bits = ch == '-' ? 0x40 : SEGS[ch - '0'];
        float w = r.width(), h = r.height();
        float t = Math.max(1.5f, w * 0.14f);
        float l = r.left + w * 0.15f, rr = r.right - w * 0.15f, top = r.top + h * 0.08f, bot = r.bottom - h * 0.08f;
        float mid = (top + bot) / 2;
        float[][] seg = {
                {l + t, top, rr - t, top + t}, {rr - t, top + t, rr, mid}, {rr - t, mid, rr, bot - t},
                {l + t, bot - t, rr - t, bot}, {l, mid, l + t, bot - t}, {l, top + t, l + t, mid},
                {l + t, mid - t / 2, rr - t, mid + t / 2}};
        for (int i = 0; i < 7; i++) {
            fill.setColor((bits & (1 << i)) != 0 ? 0xFFFF0000 : 0xFF400000);
            c.drawRect(seg[i][0], seg[i][1], seg[i][2], seg[i][3], fill);
        }
    }

    // ---------------------------------------------------------------- widgets

    public abstract static class Widget {
        /** Position in dialog client coordinates, in dp. */
        public final RectF spec = new RectF();
        /** Position in dialog space, in pixels, after layout. */
        public final RectF r = new RectF();
        public boolean enabled = true;
        public boolean pressed;

        @SuppressWarnings("unchecked")
        public <T extends Widget> T at(float x, float y, float w, float h) {
            spec.set(x, y, x + w, y + h);
            return (T) this;
        }

        public abstract void draw(Canvas c, Win95 ui);

        public boolean tappable() {
            return enabled;
        }

        /** Whether this widget wants the whole drag (lists and sliders), rather than a press/release. */
        public boolean tracks() {
            return false;
        }

        public void tap(Dialog d) {
        }

        public void down(float x, float y) {
        }

        public void move(float x, float y) {
        }

        public void up(float x, float y) {
        }
    }

    public static final class Button extends Widget {
        public final String label;
        public final boolean isDefault;
        public final Runnable action;

        public Button(String label, boolean isDefault, Runnable action) {
            this.label = label;
            this.isDefault = isDefault;
            this.action = action;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            ui.button(c, r, label, pressed, enabled, isDefault);
        }

        @Override
        public void tap(Dialog d) {
            action.run();
        }
    }

    public static final class Check extends Widget {
        public final String label;
        public boolean checked;
        public Runnable onChange;

        public Check(String label, boolean checked) {
            this.label = label;
            this.checked = checked;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            float s = 15 * ui.dp;
            RectF box = new RectF(r.left, r.centerY() - s / 2, r.left + s, r.centerY() + s / 2);
            ui.checkbox(c, box, checked, enabled);
            float x = box.right + 7 * ui.dp;
            if (enabled) {
                ui.label(c, label, x, ui.baseline(r), DARK);
            } else {
                ui.disabledLabel(c, label, x, ui.baseline(r));
            }
        }

        @Override
        public void tap(Dialog d) {
            checked = !checked;
            if (onChange != null) {
                onChange.run();
            }
        }
    }

    public static final class Radio extends Widget {
        public final String label;
        public final int group;
        public boolean checked;
        public Runnable onChange;

        public Radio(String label, int group, boolean checked) {
            this.label = label;
            this.group = group;
            this.checked = checked;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            float rad = 7.5f * ui.dp;
            ui.radio(c, r.left + rad, r.centerY(), rad, checked, enabled);
            float x = r.left + 2 * rad + 7 * ui.dp;
            if (enabled) {
                ui.label(c, label, x, ui.baseline(r), DARK);
            } else {
                ui.disabledLabel(c, label, x, ui.baseline(r));
            }
        }

        @Override
        public void tap(Dialog d) {
            for (Widget w : d.widgets) {
                if (w instanceof Radio && ((Radio) w).group == group) {
                    ((Radio) w).checked = w == this;
                }
            }
            if (onChange != null) {
                onChange.run();
            }
        }

        /** Index of the checked radio among those of {@code group} in {@code d}. */
        public static int selected(Dialog d, int group) {
            int i = 0;
            for (Widget w : d.widgets) {
                if (w instanceof Radio && ((Radio) w).group == group) {
                    if (((Radio) w).checked) {
                        return i;
                    }
                    i++;
                }
            }
            return -1;
        }
    }

    public static final class Group extends Widget {
        public final String label;

        public Group(String label) {
            this.label = label;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            ui.groupBox(c, r, label);
        }

        @Override
        public boolean tappable() {
            return false;
        }
    }

    /** Word-wrapped static text. */
    public static final class Text extends Widget {
        public String body;
        public final boolean bold;
        public float sizeDp = 15;
        public boolean center;

        public Text(String body, boolean bold) {
            this.body = body;
            this.bold = bold;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            ui.withFont(sizeDp, bold, () -> {
                float lh = ui.text.getFontSpacing();
                float y = r.top - ui.text.getFontMetrics().ascent;
                for (String para : body.split("\n", -1)) {
                    for (String ln : wrap(ui.text, para, r.width())) {
                        float x = center ? r.centerX() - ui.text.measureText(ln) / 2 : r.left;
                        ui.text.setColor(DARK);
                        ui.text.setTextAlign(Paint.Align.LEFT);
                        c.drawText(ln, x, y, ui.text);
                        y += lh;
                    }
                }
            });
        }

        @Override
        public boolean tappable() {
            return false;
        }

        public static List<String> wrap(Paint p, String para, float width) {
            List<String> out = new ArrayList<>();
            if (para.isEmpty()) {
                out.add("");
                return out;
            }
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (p.measureText(trial) > width && cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    cur.append(word);
                } else {
                    cur.setLength(0);
                    cur.append(trial);
                }
            }
            out.add(cur.toString());
            return out;
        }
    }

    /** Scrolling text in a sunken white box, for help topics. */
    public static final class Scroller extends Widget {
        public final String body;
        private float scroll, lastY, contentH;

        public Scroller(String body) {
            this.body = body;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            ui.sunken(c, r, HILITE);
            c.save();
            float pad = 6 * ui.dp;
            c.clipRect(r.left + 2 * ui.px, r.top + 2 * ui.px, r.right - 2 * ui.px, r.bottom - 2 * ui.px);
            float lh = ui.text.getFontSpacing();
            float y0 = r.top + pad - ui.text.getFontMetrics().ascent;
            float y = y0 - scroll;
            ui.text.setColor(DARK);
            for (String para : body.split("\n", -1)) {
                for (String ln : Text.wrap(ui.text, para, r.width() - 2 * pad - 8 * ui.dp)) {
                    c.drawText(ln, r.left + pad, y, ui.text);
                    y += lh;
                }
            }
            contentH = y + scroll - y0 + pad * 2 + lh;
            c.restore();
            if (contentH > r.height()) {
                float bar = r.height() * r.height() / contentH;
                float pos = (r.height() - bar) * scroll / (contentH - r.height());
                ui.fill.setColor(SHADOW);
                c.drawRect(r.right - 6 * ui.dp, r.top + pos, r.right - 2 * ui.px, r.top + pos + bar, ui.fill);
            }
        }

        @Override
        public boolean tracks() {
            return true;
        }

        @Override
        public void down(float x, float y) {
            lastY = y;
        }

        @Override
        public void move(float x, float y) {
            scroll = Math.max(0, Math.min(scroll + lastY - y, Math.max(0, contentH - r.height())));
            lastY = y;
        }
    }

    /** A single-selection list box that scrolls by dragging. */
    public static final class ListBox extends Widget {
        public final List<String> items;
        public int selected;
        public float rowDp = 34;
        public Runnable onChange;
        public Runnable onDoubleTap;
        private float scroll, lastY, downY;
        private long lastTap;
        private boolean moved;
        private float rowPx;

        public ListBox(List<String> items, int selected) {
            this.items = items;
            this.selected = selected;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            rowPx = rowDp * ui.dp;
            ui.sunken(c, r, HILITE);
            c.save();
            c.clipRect(r.left + 2 * ui.px, r.top + 2 * ui.px, r.right - 2 * ui.px, r.bottom - 2 * ui.px);
            for (int i = 0; i < items.size(); i++) {
                float top = r.top + 2 * ui.px + i * rowPx - scroll;
                if (top > r.bottom || top + rowPx < r.top) {
                    continue;
                }
                RectF row = new RectF(r.left + 2 * ui.px, top, r.right - 2 * ui.px, top + rowPx);
                if (i == selected) {
                    ui.fill.setColor(SELECT);
                    c.drawRect(row, ui.fill);
                }
                ui.label(c, items.get(i), row.left + 6 * ui.dp, ui.baseline(row), i == selected ? HILITE : DARK);
            }
            c.restore();
            float content = items.size() * rowPx;
            if (content > r.height()) {
                float bar = r.height() * r.height() / content;
                float pos = (r.height() - bar) * scroll / (content - r.height());
                ui.fill.setColor(SHADOW);
                c.drawRect(r.right - 6 * ui.dp, r.top + pos, r.right - 2 * ui.px, r.top + pos + bar, ui.fill);
            }
        }

        /** Scrolls so the selection is visible (call after layout). */
        public void reveal() {
            if (rowPx == 0) {
                return;
            }
            float top = selected * rowPx;
            if (top < scroll) {
                scroll = top;
            } else if (top + rowPx > scroll + r.height() - 4) {
                scroll = top + rowPx - r.height() + 4;
            }
        }

        @Override
        public boolean tracks() {
            return true;
        }

        @Override
        public void down(float x, float y) {
            lastY = downY = y;
            moved = false;
        }

        @Override
        public void move(float x, float y) {
            if (Math.abs(y - downY) > rowPx / 3) {
                moved = true;
            }
            if (moved) {
                float max = Math.max(0, items.size() * rowPx - r.height() + 4);
                scroll = Math.max(0, Math.min(scroll + lastY - y, max));
            }
            lastY = y;
        }

        @Override
        public void up(float x, float y) {
            if (moved || rowPx == 0) {
                return;
            }
            int i = (int) ((y - r.top + scroll) / rowPx);
            if (i < 0 || i >= items.size()) {
                return;
            }
            long now = android.os.SystemClock.uptimeMillis();
            boolean twice = i == selected && now - lastTap < 400;
            selected = i;
            lastTap = now;
            if (onChange != null) {
                onChange.run();
            }
            if (twice && onDoubleTap != null) {
                onDoubleTap.run();
            }
        }
    }

    /** A trackbar with a value from min to max. */
    public static final class Slider extends Widget {
        public final int min, max;
        public int value;
        public String leftLabel = "", rightLabel = "";
        public Runnable onChange;

        public Slider(int min, int max, int value) {
            this.min = min;
            this.max = max;
            this.value = value;
        }

        private float trackLeft(Win95 ui) {
            return r.left + 12 * ui.dp;
        }

        private float trackRight(Win95 ui) {
            return r.right - 12 * ui.dp;
        }

        private Win95 last;

        @Override
        public void draw(Canvas c, Win95 ui) {
            last = ui;
            float cy = r.top + 14 * ui.dp;
            RectF track = new RectF(trackLeft(ui), cy - 2 * ui.dp, trackRight(ui), cy + 2 * ui.dp);
            ui.sunken(c, track, HILITE);
            float t = max == min ? 0 : (value - min) / (float) (max - min);
            float x = trackLeft(ui) + t * (trackRight(ui) - trackLeft(ui));
            RectF thumb = new RectF(x - 6 * ui.dp, cy - 11 * ui.dp, x + 6 * ui.dp, cy + 11 * ui.dp);
            ui.raised(c, thumb);
            ui.withFont(12, false, () -> {
                float by = r.bottom - 2 * ui.dp;
                ui.label(c, leftLabel, r.left, by, DARK);
                ui.label(c, rightLabel, r.right - ui.textWidth(rightLabel), by, DARK);
            });
        }

        @Override
        public boolean tracks() {
            return true;
        }

        private void set(float x) {
            if (last == null) {
                return;
            }
            float t = (x - trackLeft(last)) / (trackRight(last) - trackLeft(last));
            int v = Math.round(min + Math.max(0, Math.min(1, t)) * (max - min));
            if (v != value) {
                value = v;
                if (onChange != null) {
                    onChange.run();
                }
            }
        }

        @Override
        public void down(float x, float y) {
            set(x);
        }

        @Override
        public void move(float x, float y) {
            set(x);
        }
    }

    /** A one-line text field; tapping it calls {@code onEdit} (which opens the on-screen keyboard). */
    public static final class Edit extends Widget {
        public String value;
        public Runnable onEdit;

        public Edit(String value) {
            this.value = value;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            ui.sunken(c, r, enabled ? HILITE : FACE);
            c.save();
            c.clipRect(r);
            ui.text.setColor(DARK);
            ui.text.setTextAlign(Paint.Align.LEFT);
            c.drawText(value, r.left + 6 * ui.dp, ui.baseline(r), ui.text);
            c.restore();
        }

        @Override
        public void tap(Dialog d) {
            if (onEdit != null) {
                onEdit.run();
            }
        }
    }

    /** A widget whose drawing and tapping are supplied by lambdas. */
    public static final class Custom extends Widget {
        public interface Painter {
            void paint(Canvas c, RectF r, Win95 ui);
        }

        public interface Tapper {
            void tap(float x, float y);
        }

        private final Painter painter;
        private final Tapper tapper;
        private float downX, downY;

        public Custom(Painter painter, Tapper tapper) {
            this.painter = painter;
            this.tapper = tapper;
        }

        @Override
        public void draw(Canvas c, Win95 ui) {
            painter.paint(c, r, ui);
        }

        @Override
        public boolean tappable() {
            return tapper != null && enabled;
        }

        @Override
        public boolean tracks() {
            return true;
        }

        @Override
        public void down(float x, float y) {
            downX = x;
            downY = y;
        }

        @Override
        public void up(float x, float y) {
            if (r.contains(x, y) && Math.hypot(x - downX, y - downY) < r.width()) {
                tapper.tap(x, y);
            }
        }
    }

    // ---------------------------------------------------------------- dialogs

    /** A modal Win95 dialog box, centred in its host and shrunk if it would not fit. */
    public static final class Dialog {
        public final String title;
        /** Client area size in dp. */
        public final float width, height;
        public final List<Widget> widgets = new ArrayList<>();
        public Runnable onCancel;

        /** Layout in dialog space (unscaled pixels, origin at the dialog's top-left). */
        public final RectF frame = new RectF();
        public final RectF titleBar = new RectF();
        public final RectF client = new RectF();
        private final RectF close = new RectF();
        private boolean closePressed;
        private Widget active;
        private float scale = 1f, originX, originY;

        public Dialog(String title, float width, float height) {
            this.title = title;
            this.width = width;
            this.height = height;
        }

        public <T extends Widget> T add(T w) {
            widgets.add(w);
            return w;
        }

        public void layout(Win95 ui, float viewW, float viewH) {
            float dp = ui.dp;
            float border = 4 * ui.px;
            float titleH = 26 * dp;
            float w = width * dp + 2 * border, h = height * dp + titleH + 3 * border;
            frame.set(0, 0, w, h);
            titleBar.set(border - ui.px, border - ui.px, w - border + ui.px, border + titleH);
            client.set(border, titleBar.bottom + border, w - border, h - border);
            for (Widget wd : widgets) {
                wd.r.set(client.left + wd.spec.left * dp, client.top + wd.spec.top * dp,
                        client.left + wd.spec.right * dp, client.top + wd.spec.bottom * dp);
            }
            float margin = 4 * dp;
            scale = Math.min(1f, Math.min((viewW - margin) / w, (viewH - margin) / h));
            originX = Math.round((viewW - w * scale) / 2);
            originY = Math.round(Math.max(0, (viewH - h * scale) / 2));
        }

        /** Where the dialog sits on screen. */
        public RectF screenFrame() {
            return new RectF(originX, originY, originX + frame.width() * scale, originY + frame.height() * scale);
        }

        public void draw(Canvas c, Win95 ui) {
            c.save();
            c.translate(originX, originY);
            c.scale(scale, scale);
            ui.raised(c, frame);
            ui.titleBar(c, titleBar, title, onCancel != null ? close : null, true);
            if (closePressed) {
                ui.button(c, close, "", true, true, false);
                ui.glyphX(c, new RectF(close.left + ui.px, close.top + ui.px, close.right + ui.px,
                        close.bottom + ui.px), DARK);
            }
            for (Widget w : widgets) {
                w.draw(c, ui);
            }
            c.restore();
        }

        /** Handles a touch (screen coordinates); dialogs are modal so every event is consumed. */
        public void onTouch(MotionEvent e) {
            float x = (e.getX() - originX) / scale, y = (e.getY() - originY) / scale;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    active = null;
                    closePressed = onCancel != null && close.contains(x, y);
                    if (!closePressed) {
                        for (int i = widgets.size() - 1; i >= 0; i--) {
                            Widget w = widgets.get(i);
                            if (w.tappable() && w.r.contains(x, y)) {
                                active = w;
                                w.pressed = !w.tracks();
                                if (w.tracks()) {
                                    w.down(x, y);
                                }
                                break;
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (active != null) {
                        if (active.tracks()) {
                            active.move(x, y);
                        } else {
                            active.pressed = active.r.contains(x, y);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (closePressed && close.contains(x, y) && onCancel != null) {
                        closePressed = false;
                        onCancel.run();
                    }
                    closePressed = false;
                    if (active != null) {
                        Widget w = active;
                        active = null;
                        w.pressed = false;
                        if (w.tracks()) {
                            w.up(x, y);
                        } else if (w.r.contains(x, y)) {
                            w.tap(this);
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    closePressed = false;
                    if (active != null) {
                        active.pressed = false;
                        active = null;
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
