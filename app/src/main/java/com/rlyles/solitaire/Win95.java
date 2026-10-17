package com.rlyles.solitaire;

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
 * push buttons, check boxes, radio buttons, group boxes and modal dialogs. Sizes are scaled up
 * from the 96 dpi originals so everything is comfortable to touch.
 */
final class Win95 {
    static final int FACE = 0xFFC0C0C0, LIGHT = 0xFFDFDFDF, HILITE = 0xFFFFFFFF, SHADOW = 0xFF808080,
            DARK = 0xFF000000, TITLE = 0xFF000080, TITLE_TEXT = 0xFFFFFFFF, SELECT = 0xFF000080;

    /** Device pixels per dp. */
    final float dp;
    /** Width of one bevel line. */
    final float px;
    final Paint fill = new Paint();
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint();
    private final RectF tmp = new RectF();

    Win95(float density) {
        dp = density;
        px = Math.max(1f, Math.round(density * 0.75f));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(px);
        text.setTypeface(Typeface.SANS_SERIF);
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
    void raised(Canvas c, RectF r) {
        fill.setColor(FACE);
        c.drawRect(r, fill);
        edge(c, r, LIGHT, DARK, 0);
        edge(c, r, HILITE, SHADOW, px);
    }

    /** Sunken well such as an edit field or a selected thumbnail frame. */
    void sunken(Canvas c, RectF r, int faceColor) {
        fill.setColor(faceColor);
        c.drawRect(r, fill);
        edge(c, r, SHADOW, HILITE, 0);
        edge(c, r, DARK, LIGHT, px);
    }

    /** One-pixel sunken edge used by status bar panels. */
    void shallow(Canvas c, RectF r) {
        edge(c, r, SHADOW, HILITE, 0);
    }

    /** One-pixel raised edge used by an open menu title. */
    void shallowRaised(Canvas c, RectF r) {
        edge(c, r, HILITE, SHADOW, 0);
    }

    // ---------------------------------------------------------------- text

    /** Draws text where '&amp;' marks the next character as the underlined mnemonic. */
    void label(Canvas c, String s, float x, float baseline, int color) {
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
    void disabledLabel(Canvas c, String s, float x, float baseline) {
        label(c, s, x + px, baseline + px, HILITE);
        label(c, s, x, baseline, SHADOW);
    }

    float textWidth(String s) {
        return text.measureText(s.replace("&", ""));
    }

    /** Baseline that vertically centres a line of text in {@code r}. */
    float baseline(RectF r) {
        Paint.FontMetrics fm = text.getFontMetrics();
        return r.centerY() - (fm.ascent + fm.descent) / 2;
    }

    // ---------------------------------------------------------------- controls

    void button(Canvas c, RectF r, String label, boolean pressed, boolean enabled, boolean isDefault) {
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
        float off = pressed ? px : 0;
        float x = tmp.centerX() - textWidth(label) / 2 + off;
        float y = baseline(tmp) + off;
        if (enabled) {
            label(c, label, x, y, DARK);
        } else {
            disabledLabel(c, label, x, y);
        }
    }

    void checkbox(Canvas c, RectF box, boolean checked, boolean enabled) {
        sunken(c, box, enabled ? HILITE : FACE);
        if (checked) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(box.width() * 0.14f);
            p.setStrokeCap(Paint.Cap.SQUARE);
            p.setColor(enabled ? DARK : SHADOW);
            Path tick = new Path();
            float w = box.width();
            tick.moveTo(box.left + w * 0.25f, box.top + w * 0.5f);
            tick.lineTo(box.left + w * 0.43f, box.top + w * 0.68f);
            tick.lineTo(box.left + w * 0.75f, box.top + w * 0.3f);
            c.drawPath(tick, p);
        }
    }

    void radio(Canvas c, float cx, float cy, float r, boolean checked, boolean enabled) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(px);
        RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
        p.setColor(SHADOW);
        c.drawArc(o, 135, 180, false, p);
        p.setColor(HILITE);
        c.drawArc(o, 315, 180, false, p);
        o.inset(px, px);
        p.setColor(DARK);
        c.drawArc(o, 135, 180, false, p);
        p.setColor(LIGHT);
        c.drawArc(o, 315, 180, false, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(enabled ? HILITE : FACE);
        c.drawCircle(cx, cy, r - 2 * px, p);
        if (checked) {
            p.setColor(enabled ? DARK : SHADOW);
            c.drawCircle(cx, cy, r * 0.38f, p);
        }
    }

    void groupBox(Canvas c, RectF r, String label) {
        float top = r.top + text.getTextSize() * 0.55f;
        line.setColor(SHADOW);
        c.drawRect(r.left, top, r.right - px, r.bottom - px, line);
        line.setColor(HILITE);
        c.drawRect(r.left + px, top + px, r.right, r.bottom, line);
        float tw = textWidth(label);
        fill.setColor(FACE);
        c.drawRect(r.left + 6 * dp, r.top, r.left + 14 * dp + tw, top + 2 * px, fill);
        label(c, label, r.left + 10 * dp, r.top + text.getTextSize() * 0.95f, DARK);
    }

    /** Title bar with caption and a close box; returns the close box rectangle via {@code closeOut}. */
    void titleBar(Canvas c, RectF r, String caption, RectF closeOut) {
        fill.setColor(TITLE);
        c.drawRect(r, fill);
        Typeface old = text.getTypeface();
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        label(c, caption, r.left + 6 * dp, baseline(r), TITLE_TEXT);
        text.setTypeface(old);
        if (closeOut != null) {
            float s = r.height() - 6 * dp;
            closeOut.set(r.right - 4 * dp - s * 1.15f, r.top + 3 * dp, r.right - 4 * dp, r.top + 3 * dp + s);
            raised(c, closeOut);
            drawGlyphX(c, closeOut, DARK);
        }
    }

    void drawGlyphX(Canvas c, RectF r, int color) {
        Paint p = new Paint();
        p.setColor(color);
        p.setStrokeWidth(Math.max(2f, r.height() * 0.12f));
        float s = r.height() * 0.22f, cx = r.centerX(), cy = r.centerY();
        c.drawLine(cx - s, cy - s, cx + s, cy + s, p);
        c.drawLine(cx - s, cy + s, cx + s, cy - s, p);
    }

    // ---------------------------------------------------------------- dialogs

    abstract static class Widget {
        /** Position in dialog client coordinates, in dp. */
        final RectF spec = new RectF();
        /** Position on screen, in pixels, after layout. */
        final RectF r = new RectF();
        boolean enabled = true;
        boolean pressed;

        Widget at(float x, float y, float w, float h) {
            spec.set(x, y, x + w, y + h);
            return this;
        }

        abstract void draw(Canvas c, Win95 ui);

        boolean tappable() {
            return enabled;
        }

        void tap(Dialog d) {
        }
    }

    static final class Button extends Widget {
        final String label;
        final boolean isDefault;
        final Runnable action;

        Button(String label, boolean isDefault, Runnable action) {
            this.label = label;
            this.isDefault = isDefault;
            this.action = action;
        }

        @Override
        void draw(Canvas c, Win95 ui) {
            ui.button(c, r, label, pressed, enabled, isDefault);
        }

        @Override
        void tap(Dialog d) {
            action.run();
        }
    }

    static final class Check extends Widget {
        final String label;
        boolean checked;
        Runnable onChange;

        Check(String label, boolean checked) {
            this.label = label;
            this.checked = checked;
        }

        @Override
        void draw(Canvas c, Win95 ui) {
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
        void tap(Dialog d) {
            checked = !checked;
            if (onChange != null) {
                onChange.run();
            }
        }
    }

    static final class Radio extends Widget {
        final String label;
        final int group;
        boolean checked;
        Runnable onChange;

        Radio(String label, int group, boolean checked) {
            this.label = label;
            this.group = group;
            this.checked = checked;
        }

        @Override
        void draw(Canvas c, Win95 ui) {
            float rad = 7.5f * ui.dp;
            ui.radio(c, r.left + rad, r.centerY(), rad, checked, enabled);
            ui.label(c, label, r.left + 2 * rad + 7 * ui.dp, ui.baseline(r), DARK);
        }

        @Override
        void tap(Dialog d) {
            for (Widget w : d.widgets) {
                if (w instanceof Radio && ((Radio) w).group == group) {
                    ((Radio) w).checked = w == this;
                }
            }
            if (onChange != null) {
                onChange.run();
            }
        }
    }

    static final class Group extends Widget {
        final String label;

        Group(String label) {
            this.label = label;
        }

        @Override
        void draw(Canvas c, Win95 ui) {
            ui.groupBox(c, r, label);
        }

        @Override
        boolean tappable() {
            return false;
        }
    }

    /** Word-wrapped static text. */
    static final class Text extends Widget {
        final String body;
        final boolean bold;

        Text(String body, boolean bold) {
            this.body = body;
            this.bold = bold;
        }

        @Override
        void draw(Canvas c, Win95 ui) {
            Typeface old = ui.text.getTypeface();
            if (bold) {
                ui.text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            }
            float lh = ui.text.getFontSpacing();
            float y = r.top - ui.text.getFontMetrics().ascent;
            for (String para : body.split("\n", -1)) {
                for (String ln : wrap(ui.text, para, r.width())) {
                    ui.label(c, ln, r.left, y, DARK);
                    y += lh;
                }
            }
            ui.text.setTypeface(old);
        }

        @Override
        boolean tappable() {
            return false;
        }

        static List<String> wrap(Paint p, String para, float width) {
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

    /** A modal Win95 dialog box, centred on the screen. */
    static final class Dialog {
        final String title;
        /** Client area size in dp. */
        final float width, height;
        final List<Widget> widgets = new ArrayList<>();
        Runnable onCancel;

        final RectF frame = new RectF();
        final RectF titleBar = new RectF();
        final RectF client = new RectF();
        private final RectF close = new RectF();
        private boolean closePressed;
        private Widget active;

        Dialog(String title, float width, float height) {
            this.title = title;
            this.width = width;
            this.height = height;
        }

        <T extends Widget> T add(T w) {
            widgets.add(w);
            return w;
        }

        void layout(Win95 ui, float viewW, float viewH) {
            float dp = ui.dp;
            float border = 4 * ui.px;
            float titleH = 26 * dp;
            float w = width * dp + 2 * border, h = height * dp + titleH + 3 * border;
            float left = Math.round((viewW - w) / 2), top = Math.round(Math.max(0, (viewH - h) / 2));
            frame.set(left, top, left + w, top + h);
            titleBar.set(left + border - ui.px, top + border - ui.px, left + w - border + ui.px, top + border + titleH);
            client.set(left + border, titleBar.bottom + border, left + w - border, top + h - border);
            for (Widget wd : widgets) {
                wd.r.set(client.left + wd.spec.left * dp, client.top + wd.spec.top * dp,
                        client.left + wd.spec.right * dp, client.top + wd.spec.bottom * dp);
            }
        }

        void draw(Canvas c, Win95 ui) {
            ui.raised(c, frame);
            ui.titleBar(c, titleBar, title, close);
            if (closePressed) {
                ui.button(c, close, "", true, true, false);
                ui.drawGlyphX(c, new RectF(close.left + ui.px, close.top + ui.px, close.right + ui.px,
                        close.bottom + ui.px), DARK);
            }
            for (Widget w : widgets) {
                w.draw(c, ui);
            }
        }

        /** Handles a touch; dialogs are modal so every event is consumed. */
        void onTouch(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    active = null;
                    closePressed = close.contains(x, y);
                    if (!closePressed) {
                        for (Widget w : widgets) {
                            if (w.tappable() && w.r.contains(x, y)) {
                                active = w;
                                w.pressed = true;
                                break;
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (active != null) {
                        active.pressed = active.r.contains(x, y);
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
                        if (w.r.contains(x, y)) {
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
