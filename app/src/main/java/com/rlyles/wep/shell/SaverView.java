package com.rlyles.wep.shell;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.rlyles.wep.savers.Saver;
import com.rlyles.wep.savers.Savers;

/** Runs the chosen screen saver. A long press asks the shell for the screen saver picker. */
@android.annotation.SuppressLint({"ViewConstructor", "ClickableViewAccessibility"})
public final class SaverView extends View {
    public interface Listener {
        void saverLongPressed();

        void saverTapped();
    }

    private final Listener listener;
    private Saver saver;
    private String saverId = "";
    private boolean running;
    private long last;
    private float downX, downY;
    private boolean longPressed;
    private final float slop;
    private final Runnable longPress = () -> {
        longPressed = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (getListener() != null) {
            getListener().saverLongPressed();
        }
    };

    public SaverView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private Listener getListener() {
        return listener;
    }

    public void setSaver(String id) {
        if (id.equals(saverId) && saver != null) {
            return;
        }
        if (saver != null) {
            saver.release();
        }
        saverId = id;
        saver = Savers.create(id);
        if (saver != null) {
            saver.attach(getContext().getSharedPreferences("saver_" + id, Context.MODE_PRIVATE),
                    getResources().getDisplayMetrics().density);
            saver.resize(getWidth(), getHeight());
        }
        last = 0;
        invalidate();
    }

    /** Re-reads the saver's settings after the Settings dialog changes them. */
    public void reloadSaver() {
        String id = saverId;
        saverId = "";
        setSaver(id);
    }

    public String saverId() {
        return saverId;
    }

    public Saver saver() {
        return saver;
    }

    public void setRunning(boolean on) {
        running = on;
        last = 0;
        if (on) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (saver != null) {
            saver.resize(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 1 / 30f : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        if (saver == null) {
            c.drawColor(0xFF000000);
        } else {
            saver.draw(c, dt);
        }
        if (running) {
            postInvalidateOnAnimation();
        }
    }

    /** Draws one frame after {@code seconds} of simulated time (tests and previews). */
    public void advance(Canvas c, float seconds) {
        if (saver == null) {
            return;
        }
        float step = 1 / 30f;
        for (float t = 0; t < seconds; t += step) {
            saver.draw(c, step);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longPressed = false;
                postDelayed(longPress, 550);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                    removeCallbacks(longPress);
                }
                break;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (!longPressed && listener != null) {
                    listener.saverTapped();
                }
                break;
            default:
                removeCallbacks(longPress);
                break;
        }
        return true;
    }
}
