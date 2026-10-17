package com.rlyles.wep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.rlyles.wep.shell.Apps;
import com.rlyles.wep.shell.Shell;
import com.rlyles.wep.win.WinApp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShellTest {

    @Test
    public void desktopAndStartMenu() {
        Shell s = Harness.shell(Harness.W, Harness.H);
        Harness.shot(s, "shell-01-desktop");
        s.taskbarForTest().openStartForTest(0);
        Harness.shot(s, "shell-02-start-programs");
        assertTrue(s.taskbarForTest().backPressed());
        s.taskbarForTest().openStartForTest(1);
        Harness.shot(s, "shell-03-start-settings");
        s.taskbarForTest().backPressed();
        s.taskbarForTest().showDisplayProperties();
        Harness.settle(200);
        Harness.shot(s, "shell-04-display-properties");
    }

    @Test
    public void launchMinimizeClose() {
        Shell s = Harness.shell(Harness.W, Harness.H);
        WinApp app = Harness.open(s, "sol");
        assertEquals(app, s.activeApp());
        Harness.shot(s, "shell-05-solitaire-split");
        s.minimizeApp(app);
        assertNull(s.activeApp());
        assertEquals(1, s.openApps().size());
        s.launch("sol");
        s.closeApp(app);
        assertEquals(0, s.openApps().size());
    }

    @Test
    public void landscapeSplit() {
        Shell s = Harness.shell(Harness.H, Harness.W);
        Harness.open(s, "sol");
        Harness.shot(s, "shell-06-landscape");
    }

    @Test
    public void everyAppOpens() {
        Shell s = Harness.shell(Harness.W, Harness.H);
        for (Apps.Info info : Apps.ALL) {
            WinApp app = Harness.open(s, info.id);
            assertNotNull(info.id, app);
            Harness.settle(200);
            Harness.shot(s, "app-" + info.id);
            app.stepForTest(2f);
            Harness.render(s);
            s.closeApp(app);
        }
    }
}
