package com.rlyles.wep.shell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.rlyles.wep.win.Icons;
import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.ArrayList;
import java.util.List;

/** The teal desktop with a shortcut icon for every program. */
@SuppressLint("ViewConstructor")
public final class Desktop extends RetroView {
    public interface Listener {
        void desktopOpen(String id);

        void desktopLongPress();
    }

    static final class Shortcut {
        final String id, icon, label;

        Shortcut(String id, String icon, String label) {
            this.id = id;
            this.icon = icon;
            this.label = label;
        }
    }

    private final Listener listener;
    private final List<Shortcut> shortcuts = new ArrayList<>();
    private final List<RectF> cells = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float slop;
    private int selected = -1;
    private float scroll, maxScroll, downY, lastY, downX;
    private boolean scrolling, longPressed;
    private int pressed = -1;
    private final Runnable longPress = () -> {
        longPressed = true;
        if (pressed < 0) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            getListener().desktopLongPress();
        }
    };

    public Desktop(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        shortcuts.add(new Shortcut("mycomputer", "mycomputer", "My Computer"));
        for (Apps.Info a : Apps.ALL) {
            shortcuts.add(new Shortcut(a.id, a.id, a.title));
        }
        shortcuts.add(new Shortcut("display", "display", "Display"));
        shortcuts.add(new Shortcut("recycle", "recycle", "Recycle Bin"));
    }

    private Listener getListener() {
        return listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutIcons();
    }

    private void layoutIcons() {
        cells.clear();
        float d = ui.dp;
        float cellW = 84 * d, cellH = 82 * d;
        int cols = Math.max(1, (int) ((getWidth() - 8 * d) / cellW));
        cellW = (getWidth() - 8 * d) / cols;
        for (int i = 0; i < shortcuts.size(); i++) {
            float x = 4 * d + (i % cols) * cellW, y = 6 * d + (i / cols) * cellH;
            cells.add(new RectF(x, y, x + cellW, y + cellH));
        }
        int rows = (shortcuts.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * cellH + 12 * d - getHeight());
        scroll = Math.min(scroll, maxScroll);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Win95.DESKTOP);
        float d = ui.dp;
        c.save();
        c.translate(0, -scroll);
        for (int i = 0; i < shortcuts.size(); i++) {
            Shortcut s = shortcuts.get(i);
            RectF cell = cells.get(i);
            float size = 36 * d;
            float ix = cell.centerX() - size / 2, iy = cell.top + 4 * d;
            Icons.draw(c, s.icon, ix, iy, size);
            boolean sel = i == selected || i == pressed;
            if (sel) {
                paint.setColor(0x80000080);
                c.drawRect(ix, iy, ix + size, iy + size, paint);
            }
            int finalI = i;
            ui.withFont(12.5f, false, () -> {
                List<String> lines = Win95.Text.wrap(ui.text, s.label, cell.width() - 6 * d);
                float lh = ui.text.getFontSpacing();
                float y = iy + size + 4 * d;
                for (String ln : lines) {
                    float tw = ui.text.measureText(ln);
                    if (finalI == selected || finalI == pressed) {
                        paint.setColor(Win95.SELECT);
                        c.drawRect(cell.centerX() - tw / 2 - 2 * d, y, cell.centerX() + tw / 2 + 2 * d, y + lh, paint);
                    }
                    ui.text.setColor(Win95.HILITE);
                    c.drawText(ln, cell.centerX() - tw / 2, y - ui.text.getFontMetrics().ascent, ui.text);
                    y += lh;
                }
            });
        }
        c.restore();
        drawDialogs(c);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        if (dialogTouch(e)) {
            return true;
        }
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = lastY = y;
                scrolling = false;
                longPressed = false;
                pressed = iconAt(x, y);
                postDelayed(longPress, 550);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(x - downX, y - downY) > slop) {
                    removeCallbacks(longPress);
                    scrolling = true;
                    pressed = -1;
                }
                if (scrolling) {
                    scroll = Math.max(0, Math.min(maxScroll, scroll + lastY - y));
                }
                lastY = y;
                break;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (!scrolling && !longPressed) {
                    int hit = iconAt(x, y);
                    selected = hit;
                    if (hit >= 0) {
                        listener.desktopOpen(shortcuts.get(hit).id);
                    }
                }
                pressed = -1;
                break;
            default:
                removeCallbacks(longPress);
                pressed = -1;
                break;
        }
        invalidate();
        return true;
    }

    private int iconAt(float x, float y) {
        for (int i = 0; i < cells.size(); i++) {
            RectF r = cells.get(i);
            if (r.contains(x, y + scroll)) {
                return i;
            }
        }
        return -1;
    }

    public void clearSelection() {
        selected = -1;
        invalidate();
    }
}
