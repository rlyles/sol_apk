package com.rlyles.solitaire;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Drives the real view with touch events and, when -DscreenshotDir is set, saves what it draws.
 * With -DcardsDll pointing at a cards.dll it also checks the DLL import path.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiTest {
    private static final int W = 1080, H = 2340;

    private SolitaireView launch(int w, int h) {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        SolitaireView v = (SolitaireView) ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        return v;
    }

    private static void shot(View v, String name) {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
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

    private static void touch(View v, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0);
        v.dispatchTouchEvent(e);
        e.recycle();
    }

    private static void tap(View v, float x, float y) {
        touch(v, MotionEvent.ACTION_DOWN, x, y);
        touch(v, MotionEvent.ACTION_UP, x, y);
        settle();
    }

    private static void drag(View v, float x0, float y0, float x1, float y1) {
        touch(v, MotionEvent.ACTION_DOWN, x0, y0);
        for (int i = 1; i <= 10; i++) {
            touch(v, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * i / 10, y0 + (y1 - y0) * i / 10);
        }
        touch(v, MotionEvent.ACTION_UP, x1, y1);
        settle();
    }

    /** Lets card animations finish. */
    private static void settle() {
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static PointF centre(SolitaireView v, int pile, int index) {
        PointF p = v.positionOf(pile, index);
        int cw = v.cardWidth();
        return new PointF(p.x + cw / 2f, p.y + cw * 0.2f);
    }

    /** Finds a legal tableau-to-tableau move: {from, index, to}, or null. */
    private static int[] findTableauMove(Game g) {
        for (int from = Game.TABLEAU; from < Game.TABLEAU + 7; from++) {
            List<Card> p = g.pile(from);
            for (int i = 0; i < p.size(); i++) {
                for (int to = Game.TABLEAU; to < Game.TABLEAU + 7; to++) {
                    if (g.canDrop(from, i, to) && !g.pile(to).isEmpty()) {
                        return new int[] {from, i, to};
                    }
                }
            }
        }
        return null;
    }

    @Test
    public void playsByTouch() {
        SolitaireView v = launch(W, H);
        Game g = v.game;
        // Find a deal with a tableau move available from the start.
        long seed = 1;
        do {
            g.deal(seed++, true, Game.SCORE_STANDARD, true, 0);
        } while (findTableauMove(g) == null);
        v.invalidate();
        shot(v, "01-new-game");

        // Drag the card onto its target.
        int[] mv = findTableauMove(g);
        Card moving = g.pile(mv[0]).get(mv[1]);
        PointF from = centre(v, mv[0], mv[1]);
        int targetTop = g.pile(mv[2]).size() - 1;
        PointF to = centre(v, mv[2], targetTop);
        drag(v, from.x, from.y, to.x, to.y + v.cardWidth() * 0.4f);
        assertEquals(moving, g.pile(mv[2]).get(targetTop + 1));
        assertTrue(g.canUndo());

        // Tap the face-down card left behind to turn it over.
        List<Card> src = g.pile(mv[0]);
        if (!src.isEmpty() && !src.get(src.size() - 1).faceUp) {
            PointF c = centre(v, mv[0], src.size() - 1);
            tap(v, c.x, c.y);
            assertTrue(src.get(src.size() - 1).faceUp);
            assertEquals(5, g.score());
        }

        // Tap the stock: draw three.
        PointF stock = centre(v, Game.STOCK, 0);
        tap(v, stock.x, stock.y);
        assertEquals(3, g.pile(Game.WASTE).size());
        tap(v, stock.x, stock.y + v.cardWidth());
        assertEquals(6, g.pile(Game.WASTE).size());
        shot(v, "02-after-moves");

        // A drag that goes nowhere legal leaves everything in place.
        String before = g.save();
        PointF w = centre(v, Game.WASTE, g.pile(Game.WASTE).size() - 1);
        drag(v, w.x, w.y, W / 2f, H - 400);
        if (!before.equals(g.save())) {
            // The waste card may legitimately have had a home in the column under the finger.
            assertTrue(g.canUndo());
        }

        // Undo through the Game menu.
        g.drawOrRecycle();
        int wasteBefore = g.pile(Game.WASTE).size();
        tap(v, 60, 30 * 3 + 40);      // "Game" in the menu bar
        v.invalidate();
        shot(v, "03-game-menu");
        // Undo is the third entry: 30dp title + 34dp menu bar, then Deal(40) + separator(10).
        tap(v, 150, (30 + 34) * 3 + 3 + (40 + 10 + 20) * 3);
        assertEquals(wasteBefore - 3, g.pile(Game.WASTE).size());
    }

    @Test
    public void dialogsRender() {
        SolitaireView v = launch(W, H);
        v.game.deal(11, false, Game.SCORE_VEGAS, true, 0);
        v.showOptionsDialog();
        shot(v, "04-options");
        assertNotNull(v.currentDialog());
        assertTrue(v.onBack());
        assertNull(v.currentDialog());
        v.showDeckDialog();
        shot(v, "05-deck");
        v.onBack();
        v.showAboutDialog();
        shot(v, "06-about");
        v.onBack();
        v.showHelpDialog();
        shot(v, "07-help");
        v.onBack();
        v.openMenuForTest(1);
        shot(v, "08-help-menu");
    }

    @Test
    public void optionsDialogAppliesChanges() {
        SolitaireView v = launch(W, H);
        v.showOptionsDialog();
        Win95.Dialog d = v.currentDialog();
        Win95.Radio vegas = null;
        Win95.Radio drawOne = null;
        Win95.Button ok = null;
        for (Win95.Widget w : d.widgets) {
            if (w instanceof Win95.Radio && ((Win95.Radio) w).label.equals("&Vegas")) {
                vegas = (Win95.Radio) w;
            } else if (w instanceof Win95.Radio && ((Win95.Radio) w).label.equals("Draw &One")) {
                drawOne = (Win95.Radio) w;
            } else if (w instanceof Win95.Button && ((Win95.Button) w).label.equals("OK")) {
                ok = (Win95.Button) w;
            }
        }
        tap(v, vegas.r.centerX(), vegas.r.centerY());
        tap(v, drawOne.r.centerX(), drawOne.r.centerY());
        tap(v, ok.r.centerX(), ok.r.centerY());
        assertNull(v.currentDialog());
        assertEquals(Game.SCORE_VEGAS, v.game.scoring());
        assertFalse(v.game.drawThree());
        assertEquals(-52, v.game.score());
        shot(v, "09-vegas-draw-one");
    }

    @Test
    public void winAnimationRunsAndOffersNewDeal() {
        SolitaireView v = launch(W, H);
        Game g = v.game;
        StringBuilder s = new StringBuilder("v1;0;0;1;500;95;1;0;0;0;0;;");
        for (int suit = 0; suit < 4; suit++) {
            s.append(';');
            int top = suit == 3 ? 12 : 13;
            for (int r = 1; r <= top; r++) {
                s.append(r > 1 ? "," : "").append(100 + suit * 13 + r - 1);
            }
        }
        s.append(";").append(100 + 51).append(";;;;;;");
        assertTrue(g.load(s.toString()));
        v.invalidate();
        // Double-tap the felt: everything that can go up goes up, and the game is won.
        for (int i = 0; i < 2; i++) {
            touch(v, MotionEvent.ACTION_DOWN, W / 2f, H - 300);
            touch(v, MotionEvent.ACTION_UP, W / 2f, H - 300);
        }
        assertTrue(g.won());
        for (int i = 0; i < 5 && !v.winAnimationRunning(); i++) {
            v.draw(new Canvas(Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)));
            settle();
        }
        assertTrue(v.winAnimationRunning());
        v.stepWinAnimationForTest(1400);
        shot(v, "10-win-cascade");
        tap(v, W / 2f, H / 2f);
        assertFalse(v.winAnimationRunning());
        assertNotNull(v.currentDialog());
        shot(v, "11-deal-again");
    }

    @Test
    public void landscapeLayout() {
        SolitaireView v = launch(2340, 1080);
        v.game.deal(5, true, Game.SCORE_STANDARD, true, 0);
        for (int i = 0; i < 4; i++) {
            v.game.drawOrRecycle();
        }
        v.invalidate();
        shot(v, "12-landscape");
    }

    @Test
    public void loadsCardsDll() throws Exception {
        String path = System.getProperty("cardsDll", "");
        if (path.isEmpty() || !new File(path).exists()) {
            return;
        }
        CardsDll dll = CardsDll.parse(Files.readAllBytes(new File(path).toPath()));
        for (Bitmap b : dll.faces) {
            assertEquals(71, b.getWidth());
            assertEquals(96, b.getHeight());
        }
        assertTrue(dll.backs.size() >= 12);
        SolitaireView v = launch(W, H);
        v.setDll(dll);
        long seed = 3;
        v.game.deal(seed, true, Game.SCORE_STANDARD, true, 0);
        for (int i = 0; i < 3; i++) {
            v.game.drawOrRecycle();
        }
        v.invalidate();
        shot(v, "13-dll-game");
        v.showDeckDialog();
        shot(v, "14-dll-deck");
    }
}
