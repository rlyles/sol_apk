package com.rlyles.wep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;

import com.rlyles.wep.media.Dib;
import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.ResFile;
import com.rlyles.wep.media.Wav;
import com.rlyles.wep.shell.Apps;
import com.rlyles.wep.shell.Shell;
import com.rlyles.wep.win.WinApp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The original-files importer. The synthetic tests always run; the ones that use real
 * Entertainment Pack files run when {@code -DmediaDir} points at a folder of them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaTest {

    /** A tiny 16-bit Windows DLL with one RLE4-compressed bitmap resource, id 1. */
    static byte[] fakeNe() {
        ByteBuffer b = ByteBuffer.allocate(0x200).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 'M').put(1, (byte) 'Z').putInt(0x3C, 0x40);
        b.put(0x40, (byte) 'N').put(0x41, (byte) 'E').putShort(0x40 + 0x24, (short) 0x40);
        int rt = 0x80;
        b.putShort(rt, (short) 4); // alignment shift
        b.putShort(rt + 2, (short) 0x8002).putShort(rt + 4, (short) 1); // RT_BITMAP, one resource
        b.putShort(rt + 10, (short) (0x100 >> 4)).putShort(rt + 12, (short) (0x100 >> 4));
        b.putShort(rt + 16, (short) 0x8001); // id 1
        b.putShort(rt + 22, (short) 0); // end of types
        // BITMAPINFOHEADER: 4x2, 4bpp, BI_RLE4
        int d = 0x100;
        b.putInt(d, 40).putInt(d + 4, 4).putInt(d + 8, 2).putShort(d + 12, (short) 1).putShort(d + 14, (short) 4)
                .putInt(d + 16, 2);
        for (int i = 0; i < 16; i++) {
            b.putInt(d + 40 + i * 4, i * 0x111111); // palette: grey ramp-ish, distinct per index
        }
        int p = d + 40 + 64;
        byte[] rle = {4, 0x12, 0, 0, 0, 4, 0x34, 0x56, 0, 0, 0, 1};
        for (int i = 0; i < rle.length; i++) {
            b.put(p + i, rle[i]);
        }
        return b.array();
    }

    static byte[] fakeWav() {
        ByteBuffer b = ByteBuffer.allocate(44 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(40).putInt(0x45564157);
        b.putInt(0x20746D66).putInt(16).putShort((short) 1).putShort((short) 1).putInt(11025).putInt(11025)
                .putShort((short) 1).putShort((short) 8);
        b.putInt(0x61746164).putInt(4).put((byte) 128).put((byte) 255).put((byte) 0).put((byte) 128);
        return b.array();
    }

    @Test
    public void readsSixteenBitResourcesAndRle() throws IOException {
        ResFile r = ResFile.parse(fakeNe());
        assertTrue(r.sixteenBit);
        byte[] dib = r.get(ResFile.BITMAP, "1");
        assertNotNull(dib);
        int[] size = new int[2];
        int[] px = Dib.pixels(dib, size);
        assertEquals(4, size[0]);
        assertEquals(2, size[1]);
        // Bottom-up: the first RLE row is the bottom one.
        assertEquals(0xFF000000 | 0x111111, px[4]);
        assertEquals(0xFF000000 | 0x222222, px[5]);
        assertEquals(0xFF000000 | 0x333333, px[0]);
        assertEquals(0xFF000000 | 0x666666, px[3]);
    }

    @Test
    public void decodesWav() throws IOException {
        int[] rate = new int[1];
        short[] pcm = Wav.decode(fakeWav(), rate);
        assertEquals(11025, rate[0]);
        assertEquals(4, pcm.length);
        assertEquals(0, pcm[0]);
        assertTrue(pcm[1] > 30000);
        assertTrue(pcm[2] < -30000);
    }

    @Test
    public void importsFromZip() throws IOException {
        Harness.shell(Harness.W, Harness.H);
        Media m = Media.get();
        m.removeAll();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("BOWEP/SKI.EXE"));
            zip.write(fakeNe());
            zip.putNextEntry(new ZipEntry("BOWEP/BOUNCE.WAV"));
            zip.write(fakeWav());
            zip.putNextEntry(new ZipEntry("BOWEP/README.TXT"));
            zip.write("hello".getBytes("US-ASCII"));
        }
        Media.Result r = m.importFile("bowep.zip", bytes.toByteArray(), null);
        assertEquals(2, r.added.size());
        assertEquals(1, r.skipped);
        assertTrue(m.has("ski.exe"));
        assertTrue(m.has("bounce.wav"));
        Bitmap b = m.bitmap("ski.exe", "1");
        assertNotNull(b);
        assertEquals(4, b.getWidth());
        m.setArt(false);
        assertEquals(null, m.bitmap("ski.exe", "1"));
        m.setArt(true);
        m.removeAll();
        assertTrue(m.files().isEmpty());
    }

    // ---------------------------------------------------------------- with real files

    private static File mediaDir() {
        String dir = System.getProperty("mediaDir", "");
        assumeTrue("no -DmediaDir", !dir.isEmpty() && new File(dir).isDirectory());
        return new File(dir);
    }

    private static byte[] read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Test
    public void decodesEveryOriginalBitmap() throws IOException {
        File dir = mediaDir();
        int count = 0;
        File[] files = dir.listFiles();
        assertNotNull(files);
        for (File f : files) {
            String n = f.getName().toLowerCase(java.util.Locale.US);
            if (!n.endsWith(".exe") && !n.endsWith(".dll")) {
                continue;
            }
            byte[] data = read(f);
            if (!ResFile.looksLikeExe(data)) {
                continue;
            }
            ResFile r = ResFile.parse(data);
            for (Map.Entry<String, byte[]> e : r.type(ResFile.BITMAP).entrySet()) {
                Bitmap b = Dib.decode(e.getValue());
                assertTrue(n + " " + e.getKey(), b.getWidth() > 0);
                count++;
            }
            if (n.equals("rodent.exe")) {
                assertEquals(5, r.embeddedBitmaps().size());
            }
        }
        assertTrue(count > 300);
    }

    @Test
    public void gamesUseOriginalArt() throws IOException {
        File dir = mediaDir();
        Shell s = Harness.shell(Harness.W, Harness.H);
        Media m = Media.get();
        m.removeAll();
        Media.Result r = new Media.Result();
        File[] files = dir.listFiles();
        assertNotNull(files);
        java.util.Arrays.sort(files);
        for (File f : files) {
            m.importFile(f.getName(), read(f), r);
        }
        assertTrue(r.added.contains("chips.exe"));
        s.mediaChanged();
        s.taskbarForTest().showOriginalFiles();
        Harness.settle(200);
        Harness.shot(s, "orig-00-dialog");
        s.taskbarForTest().backPressed();
        for (Apps.Info info : Apps.ALL) {
            WinApp app = Harness.open(s, info.id);
            Harness.settle(200);
            app.stepForTest(1.5f);
            Harness.shot(s, "orig-" + info.id);
            s.closeApp(app);
        }
        m.removeAll();
    }
}
