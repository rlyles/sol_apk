package com.rlyles.wep;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.rlyles.wep.savers.Saver;
import com.rlyles.wep.savers.Savers;
import com.rlyles.wep.shell.Shell;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SaverTest {

    @Test
    public void everySaverAnimates() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        for (Savers.Info info : Savers.ALL) {
            if (info.id.equals(Savers.NONE)) {
                continue;
            }
            Saver s = Savers.create(info.id);
            assertNotNull(info.id, s);
            s.attach(ctx.getSharedPreferences("saver_" + info.id, Context.MODE_PRIVATE), 3f);
            s.resize(1080, 1100);
            Bitmap b = Bitmap.createBitmap(1080, 1100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            for (int i = 0; i < 120; i++) {
                s.draw(c, 1 / 30f);
            }
            assertTrue(info.id + " drew something", hasVariety(b));
            String dir = System.getProperty("screenshotDir", "");
            if (!dir.isEmpty()) {
                new File(dir).mkdirs();
                try (FileOutputStream out = new FileOutputStream(new File(dir, "saver-" + info.id + ".png"))) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            }
            s.release();
        }
    }

    private static boolean hasVariety(Bitmap b) {
        int first = b.getPixel(0, 0);
        for (int y = 0; y < b.getHeight(); y += 17) {
            for (int x = 0; x < b.getWidth(); x += 17) {
                if (b.getPixel(x, y) != first) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void settingsDialogsOpen() {
        Shell s = Harness.shell(Harness.W, Harness.H);
        for (Savers.Info info : Savers.ALL) {
            s.taskbarForTest().showSaverSettings(info.id, null);
            Harness.settle(50);
            if (info.id.equals("text3d") || info.id.equals("pipes3d")) {
                Harness.shot(s, "saver-settings-" + info.id);
            }
            s.taskbarForTest().backPressed();
        }
    }

    @Test
    public void splitScreenWithSaver() {
        Shell s = Harness.shell(Harness.W, Harness.H);
        Harness.open(s, "sol");
        Harness.settle(500);
        Harness.lay(s, Harness.W, Harness.H);
        Harness.shot(s, "split-portrait-pipes");
        Shell l = Harness.shell(Harness.H, Harness.W);
        Harness.open(l, "winmine");
        Harness.settle(500);
        Harness.lay(l, Harness.H, Harness.W);
        Harness.shot(l, "split-landscape-pipes");
    }
}
