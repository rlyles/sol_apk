package com.rlyles.wep.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The user's own copies of the original game files (EXEs, DLLs, WAVs, MIDIs, CHIPS.DAT).
 *
 * <p>None of these ship with the app. The user imports them from their own Entertainment Pack
 * disks, as single files or as a zip of the folder, and they are kept in the app's private
 * storage. Games ask for a bitmap by file and resource name and fall back to their own drawing
 * when it isn't there.
 */
public final class Media {
    /** Builds something from the media files; may return null if the files aren't there. */
    public interface Factory<T> {
        T build(Media m) throws IOException;
    }

    /** The files that are worth keeping (anything else in a zip is skipped). */
    public static final List<String> KNOWN = Arrays.asList(
            "cards.dll", "chips.dat", "chips.exe", "freecell.exe", "jezzball.exe", "pipe.exe",
            "rodent.exe", "ski.exe", "snake.exe", "taipei.exe", "tetris.exe", "tictac.exe",
            "tp.exe", "tripeaks.exe", "tutstomb.exe", "winmine.exe");

    /** What each game uses, for the Original Game Files dialog. */
    public static final String[][] CATALOG = {
            {"Card games", "cards.dll"},
            {"Chip's Challenge", "chips.exe", "chips.dat", "chip01.mid", "click3.wav"},
            {"FreeCell", "freecell.exe"},
            {"JezzBall", "jezzball.exe", "bounce.wav"},
            {"Minesweeper", "winmine.exe"},
            {"Pipe Dream", "pipe.exe"},
            {"Rodent's Revenge", "rodent.exe"},
            {"SkiFree", "ski.exe"},
            {"Snake", "snake.exe"},
            {"Taipei", "tp.exe|taipei.exe"},
            {"Tetris", "tetris.exe"},
            {"TicTactics", "tictac.exe"},
            {"TriPeaks", "tripeaks.exe"},
            {"Tut's Tomb", "tutstomb.exe"},
    };

    private static final int MAX_FILE = 16 * 1024 * 1024;
    private static final Object MISSING = new Object();
    private static Media instance = new Media(null, null);

    private final File dir;
    private final SharedPreferences prefs;
    private boolean art = true, sounds = true;
    private int version;
    private final Map<String, Object> exes = new HashMap<>();
    private final Map<String, Object> cache = new HashMap<>();

    private Media(File dir, SharedPreferences prefs) {
        this.dir = dir;
        this.prefs = prefs;
        if (prefs != null) {
            art = prefs.getBoolean("art", true);
            sounds = prefs.getBoolean("sounds", true);
        }
    }

    public static Media get() {
        return instance;
    }

    public static void init(Context context) {
        File dir = new File(context.getFilesDir(), "media");
        if (instance.dir == null || !instance.dir.equals(dir)) {
            instance = new Media(dir, context.getSharedPreferences("media", Context.MODE_PRIVATE));
        }
    }

    /** Tests point this at a folder of original files. */
    public static void initForTest(File dir) {
        instance = new Media(dir, null);
    }

    /** Changes whenever the files or settings change, so games can rebuild their sprites. */
    public int version() {
        return version;
    }

    public boolean artOn() {
        return art;
    }

    public boolean soundsOn() {
        return sounds;
    }

    public void setArt(boolean on) {
        if (art != on) {
            art = on;
            changed();
        }
    }

    public void setSounds(boolean on) {
        if (sounds != on) {
            sounds = on;
            changed();
        }
    }

    private void changed() {
        version++;
        cache.clear();
        if (prefs != null) {
            prefs.edit().putBoolean("art", art).putBoolean("sounds", sounds).apply();
        }
    }

    // ---------------------------------------------------------------- reading

    public boolean has(String name) {
        return dir != null && new File(dir, name.toLowerCase(Locale.US)).isFile();
    }

    public File file(String name) {
        return dir == null ? null : new File(dir, name.toLowerCase(Locale.US));
    }

    /** The bytes of an imported file, or null. */
    public byte[] read(String name) {
        if (!has(name)) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file(name))) {
            return readAll(in, MAX_FILE);
        } catch (IOException e) {
            Log.w("Media", "read " + name, e);
            return null;
        }
    }

    /** The resources of an imported EXE or DLL, or null. */
    public ResFile exe(String name) {
        String key = name.toLowerCase(Locale.US);
        Object o = exes.get(key);
        if (o == null) {
            byte[] data = read(key);
            try {
                o = data == null ? MISSING : ResFile.parse(data);
            } catch (IOException e) {
                Log.w("Media", "parse " + name, e);
                o = MISSING;
            }
            exes.put(key, o);
        }
        return o == MISSING ? null : (ResFile) o;
    }

    /**
     * A bitmap resource from one of the original programs, or null if the file hasn't been
     * imported (or original pictures are switched off). {@code file} may list alternatives
     * separated by "|".
     */
    public Bitmap bitmap(String file, String id) {
        if (!art) {
            return null;
        }
        return cached("bmp:" + file + ":" + id, m -> {
            for (String f : file.split("\\|")) {
                ResFile r = m.exe(f);
                byte[] dib = r == null ? null : r.get(ResFile.BITMAP, id);
                if (dib != null) {
                    return Dib.decode(dib);
                }
            }
            return null;
        });
    }

    /**
     * The {@code index}th picture embedded in a Visual Basic program's forms (see
     * {@link ResFile#embeddedBitmaps()}), or null.
     */
    public Bitmap embeddedBitmap(String file, int index) {
        if (!art) {
            return null;
        }
        return cached("vb:" + file + ":" + index, m -> {
            ResFile r = m.exe(file);
            if (r == null) {
                return null;
            }
            java.util.List<byte[]> all = r.embeddedBitmaps();
            return index < all.size() ? Dib.decode(all.get(index)) : null;
        });
    }

    /** True if pictures from this file can be used. */
    public boolean hasArt(String file) {
        if (!art) {
            return false;
        }
        for (String f : file.split("\\|")) {
            if (exe(f) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Something built from the files, such as a game's sprite set, kept until the files or
     * settings change. Failures and nulls are remembered too, so each is built only once.
     */
    @SuppressWarnings("unchecked")
    public <T> T cached(String key, Factory<T> factory) {
        Object o = cache.get(key);
        if (o == null) {
            try {
                o = factory.build(this);
            } catch (IOException | RuntimeException e) {
                Log.w("Media", "build " + key, e);
                o = null;
            }
            cache.put(key, o == null ? MISSING : o);
        }
        return o == MISSING ? null : (T) o;
    }

    public List<String> files() {
        List<String> out = new ArrayList<>();
        String[] names = dir == null ? null : dir.list();
        if (names != null) {
            out.addAll(Arrays.asList(names));
            java.util.Collections.sort(out);
        }
        return out;
    }

    // ---------------------------------------------------------------- importing

    /** What an import did. */
    public static final class Result {
        public final List<String> added = new ArrayList<>();
        public int skipped;
    }

    /** Imports one picked file: a zip of a folder, or a single EXE, DLL, WAV, MID or CHIPS.DAT. */
    public Result importFile(String name, byte[] data, Result into) throws IOException {
        Result r = into != null ? into : new Result();
        if (dir == null) {
            throw new IOException("No storage.");
        }
        if (data.length > 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4) {
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (e.isDirectory()) {
                        continue;
                    }
                    String base = baseName(e.getName());
                    if (!wanted(base)) {
                        r.skipped++;
                        continue;
                    }
                    store(base, readAll(zip, MAX_FILE), r);
                }
            }
        } else {
            store(baseName(name == null ? "" : name), data, r);
        }
        exes.clear();
        changed();
        return r;
    }

    private static String baseName(String path) {
        String s = path.replace('\\', '/');
        return s.substring(s.lastIndexOf('/') + 1).toLowerCase(Locale.US);
    }

    private static boolean wanted(String base) {
        return KNOWN.contains(base) || base.endsWith(".wav") || base.endsWith(".mid");
    }

    private void store(String base, byte[] data, Result r) throws IOException {
        if (!wanted(base)) {
            r.skipped++;
            return;
        }
        if (base.endsWith(".exe") || base.endsWith(".dll")) {
            if (!ResFile.looksLikeExe(data)) {
                r.skipped++;
                return;
            }
            // The 16-bit originals are the Windows 95-era art; don't let a later 32-bit build of
            // the same program (say freecell.exe or cards.dll) replace one.
            byte[] old = read(base);
            if (old != null && sixteenBit(old) && !sixteenBit(data)) {
                r.skipped++;
                return;
            }
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Can't create " + dir);
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, base))) {
            out.write(data);
        }
        if (!r.added.contains(base)) {
            r.added.add(base);
        }
    }

    private static boolean sixteenBit(byte[] data) {
        try {
            return ResFile.parse(data).sixteenBit;
        } catch (IOException e) {
            return false;
        }
    }

    public void removeAll() {
        for (String f : files()) {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, f).delete();
        }
        exes.clear();
        changed();
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > max) {
                throw new IOException("A file is too big.");
            }
        }
        return out.toByteArray();
    }
}
