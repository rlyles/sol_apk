package com.rlyles.wep.savers;

import android.content.SharedPreferences;
import android.graphics.Canvas;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.Random;

/**
 * One screen saver. {@link #draw} is called for every frame and should paint the whole area
 * (savers that build up a picture keep their own bitmap).
 */
public abstract class Saver {
    protected int w, h;
    protected final Random rnd = new Random();
    protected SharedPreferences prefs;
    /** Device pixels per dp, for savers that size things physically. */
    protected float dp = 1;

    public abstract String name();

    public void attach(SharedPreferences prefs, float dp) {
        this.prefs = prefs;
        this.dp = dp;
        loadSettings();
    }

    public final void resize(int w, int h) {
        this.w = w;
        this.h = h;
        if (w > 0 && h > 0) {
            reset();
        }
    }

    /** Start over at the current size. */
    protected abstract void reset();

    /** Paint a frame; {@code dt} is the seconds since the last one. */
    public abstract void draw(Canvas c, float dt);

    protected void loadSettings() {
    }

    /**
     * Adds this saver's options to a Settings dialog (client width 300dp, starting at {@code top}),
     * returning the height used. The {@code apply} list collects what OK should do.
     */
    public float settings(Win95.Dialog d, float top, java.util.List<Runnable> apply, RetroView host) {
        return 0;
    }

    public void release() {
    }

    protected float rand(float lo, float hi) {
        return lo + rnd.nextFloat() * (hi - lo);
    }
}
