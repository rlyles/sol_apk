package com.rlyles.wep;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.rlyles.wep.shell.MainActivity;
import com.rlyles.wep.shell.Shell;
import com.rlyles.wep.win.WinApp;

import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Helpers for driving the real views in Robolectric and saving what they draw. */
final class Harness {
    static final int W = 1080, H = 2340;

    private Harness() {
    }

    static Shell shell(int w, int h) {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Shell s = a.shell();
        lay(s, w, h);
        return s;
    }

    static void lay(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
    }

    /** Launches an app in the shell and lays everything out. */
    static WinApp open(Shell s, String id) {
        WinApp app = s.launch(id);
        lay(s, s.getWidth(), s.getHeight());
        return app;
    }

    static Bitmap render(View v) {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        return b;
    }

    static void shot(View v, String name) {
        Bitmap b = render(v);
        String dir = System.getProperty("screenshotDir", "");
        if (dir.isEmpty()) {
            return;
        }
        new File(dir).mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Touch events in the coordinates of {@code v} (dispatch through the shell for realism). */
    static void touch(View v, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0);
        v.dispatchTouchEvent(e);
        e.recycle();
    }

    static void tap(View v, float x, float y) {
        touch(v, MotionEvent.ACTION_DOWN, x, y);
        touch(v, MotionEvent.ACTION_UP, x, y);
        settle(300);
    }

    static void drag(View v, float x0, float y0, float x1, float y1) {
        touch(v, MotionEvent.ACTION_DOWN, x0, y0);
        for (int i = 1; i <= 10; i++) {
            touch(v, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * i / 10, y0 + (y1 - y0) * i / 10);
        }
        touch(v, MotionEvent.ACTION_UP, x1, y1);
        settle(300);
    }

    static void longPress(View v, float x, float y) {
        touch(v, MotionEvent.ACTION_DOWN, x, y);
        settle(700);
        touch(v, MotionEvent.ACTION_UP, x, y);
        settle(100);
    }

    static void settle(long ms) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS);
    }
}
