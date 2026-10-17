package com.rlyles.wep.shell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.rlyles.wep.cards.CardsDll;
import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.Music;
import com.rlyles.wep.savers.Savers;
import com.rlyles.wep.win.Sfx;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole screen: a screen saver pane and a "monitor" pane split by a draggable divider (saver
 * above in portrait, to the left in landscape). The monitor shows the desktop, the program
 * windows and the taskbar.
 */
@SuppressLint("ViewConstructor")
public final class Shell extends ViewGroup implements WinApp.Host, SaverView.Listener, Desktop.Listener {

    /** What the shell needs from its Activity. */
    public interface Platform {
        void pickFile(WinApp.FileResult result);

        /** Lets the user pick one or more files; their names and bytes go to {@code result}. */
        void pickFiles(FilesResult result);

        void finishShell();
    }

    public interface FilesResult {
        void files(List<String> names, List<byte[]> data);
    }

    private final Platform platform;
    private final SharedPreferences prefs;
    final SaverView saver;
    final Desktop desktop;
    private final Divider divider;
    final Taskbar taskbar;
    private final List<WinApp> open = new ArrayList<>();
    private WinApp active;
    private final Sfx sfx = new Sfx();
    private CardsDll cards;

    private final RectF saverRect = new RectF(), dividerRect = new RectF(), gameRect = new RectF(),
            appRect = new RectF(), taskRect = new RectF();
    private float splitPortrait, splitLandscape;
    private boolean saverShown;
    private int idleMinutes;
    private boolean fullscreen;
    private long lastActivity = SystemClock.uptimeMillis();
    private boolean resumed;

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            if (idleMinutes > 0 && !fullscreen && resumed
                    && SystemClock.uptimeMillis() - lastActivity > idleMinutes * 60_000L
                    && !Savers.NONE.equals(saverId())) {
                setFullscreen(true);
            }
            postDelayed(this, 5000);
        }
    };

    public Shell(Context context, Platform platform) {
        super(context);
        this.platform = platform;
        prefs = context.getSharedPreferences("shell", Context.MODE_PRIVATE);
        splitPortrait = prefs.getFloat("splitPortrait", -1);
        splitLandscape = prefs.getFloat("splitLandscape", -1);
        saverShown = prefs.getBoolean("saverShown", true);
        idleMinutes = prefs.getInt("idle", 0);
        sfx.enabled = prefs.getBoolean("sound", true);
        Media.init(context);
        loadCards();

        saver = new SaverView(context, this);
        desktop = new Desktop(context, this);
        divider = new Divider(context);
        taskbar = new Taskbar(context, this);
        addView(saver);
        addView(desktop);
        addView(divider);
        addView(taskbar);
        saver.setSaver(prefs.getString("saver", "pipes3d"));

        String ids = prefs.getString("open", "");
        String activeId = prefs.getString("active", "");
        for (String id : ids.split(",")) {
            if (!id.isEmpty() && Apps.find(id) != null) {
                WinApp app = create(id);
                if (id.equals(activeId)) {
                    active = app;
                }
            }
        }
        if (active != null) {
            showActive();
        } else {
            for (WinApp app : open) {
                app.setVisibility(GONE);
            }
        }
        post(idleCheck);
    }

    // ---------------------------------------------------------------- lifecycle

    public void onResume() {
        resumed = true;
        lastActivity = SystemClock.uptimeMillis();
        saver.setRunning(true);
        if (active != null) {
            active.setActive(true);
        }
    }

    public void onPause() {
        resumed = false;
        saver.setRunning(false);
        if (active != null) {
            active.setActive(false);
        }
        for (WinApp app : open) {
            app.save();
        }
        saveShell();
    }

    public void onDestroy() {
        sfx.release();
    }

    private void saveShell() {
        StringBuilder ids = new StringBuilder();
        for (WinApp app : open) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(app.appId);
        }
        prefs.edit().putString("open", ids.toString()).putString("active", active == null ? "" : active.appId)
                .putFloat("splitPortrait", splitPortrait).putFloat("splitLandscape", splitLandscape)
                .putBoolean("saverShown", saverShown).putInt("idle", idleMinutes)
                .putBoolean("sound", sfx.enabled).putString("saver", saverId()).apply();
    }

    /** Back button. Returns false when there is nothing left to back out of. */
    public boolean onBack() {
        if (fullscreen) {
            setFullscreen(false);
            return true;
        }
        if (taskbar.backPressed()) {
            return true;
        }
        if (active != null) {
            if (!active.backPressed()) {
                minimizeApp(active);
            }
            return true;
        }
        return desktop.backPressed();
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        computeRects(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            RectF r = rectFor(v, w, h);
            v.measure(MeasureSpec.makeMeasureSpec((int) r.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) r.height(), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        computeRects(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            RectF rect = rectFor(v, w, h);
            v.layout((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
        }
    }

    private RectF rectFor(View v, int w, int h) {
        if (v == saver) {
            return fullscreen ? new RectF(0, 0, w, h) : saverRect;
        } else if (v == divider) {
            return dividerRect;
        } else if (v == taskbar) {
            return new RectF(0, 0, w, h);
        }
        return appRect;
    }

    private boolean landscape(int w, int h) {
        return w > h;
    }

    float dividerThickness() {
        return 12 * desktop.ui.dp;
    }

    /** The saver size (height in portrait, width in landscape) that leaves the game a 4:3 client area. */
    private float autoSaverSize(int w, int h) {
        float d = desktop.ui.dp;
        float chrome = (28 + 30 + 26) * d;
        float bar = 30 * d;
        float t = dividerThickness();
        if (landscape(w, h)) {
            float clientH = h - chrome - bar;
            return Math.max(0, w - clientH * 4f / 3f - t);
        }
        float gameH = chrome + bar + w * 3f / 4f;
        return Math.max(0, h - gameH - t);
    }

    private void computeRects(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean land = landscape(w, h);
        float t = saverShown ? dividerThickness() : 0;
        float total = land ? w : h;
        float split = land ? splitLandscape : splitPortrait;
        float size = !saverShown ? 0 : split < 0 ? autoSaverSize(w, h) : split * total;
        float minGame = 220 * desktop.ui.dp;
        size = Math.max(0, Math.min(size, total - t - minGame));
        if (land) {
            saverRect.set(0, 0, size, h);
            dividerRect.set(size, 0, size + t, h);
            gameRect.set(size + t, 0, w, h);
        } else {
            saverRect.set(0, 0, w, size);
            dividerRect.set(0, size, w, size + t);
            gameRect.set(0, size + t, w, h);
        }
        float bar = taskbar.barHeight();
        taskRect.set(gameRect.left, gameRect.bottom - bar, gameRect.right, gameRect.bottom);
        appRect.set(gameRect.left, gameRect.top, gameRect.right, gameRect.bottom - bar);
        divider.setVisibility(saverShown && !fullscreen ? VISIBLE : GONE);
    }

    public Taskbar taskbarForTest() {
        return taskbar;
    }

    RectF taskRect() {
        return taskRect;
    }

    private void dragDivider(float pos) {
        int w = getWidth(), h = getHeight();
        boolean land = landscape(w, h);
        float total = land ? w : h;
        float size = pos - dividerThickness() / 2;
        if (size < 48 * desktop.ui.dp) {
            size = 0;
        }
        float fraction = Math.max(0, size / total);
        if (land) {
            splitLandscape = fraction;
        } else {
            splitPortrait = fraction;
        }
        requestLayout();
    }

    void resetSplit() {
        if (landscape(getWidth(), getHeight())) {
            splitLandscape = -1;
        } else {
            splitPortrait = -1;
        }
        setSaverShown(true);
        requestLayout();
    }

    // ---------------------------------------------------------------- activity & idle saver

    void noteActivity() {
        lastActivity = SystemClock.uptimeMillis();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        noteActivity();
        if (fullscreen) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                setFullscreen(false);
            }
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    boolean fullscreenSaver() {
        return fullscreen;
    }

    private void setFullscreen(boolean on) {
        fullscreen = on;
        desktop.setVisibility(on ? INVISIBLE : VISIBLE);
        for (WinApp app : open) {
            app.setVisibility(!on && app == active ? VISIBLE : GONE);
        }
        if (active != null) {
            active.setActive(!on && resumed);
        }
        taskbar.invalidate();
        requestLayout();
    }

    void previewSaver(String id) {
        setSaver(id);
        setFullscreen(true);
    }

    @Override
    public void saverLongPressed() {
        taskbar.showDisplayProperties();
    }

    @Override
    public void saverTapped() {
        if (fullscreen) {
            setFullscreen(false);
        }
    }

    // ---------------------------------------------------------------- settings

    String saverId() {
        return saver.saverId();
    }

    void setSaver(String id) {
        saver.setSaver(id);
        saveShell();
    }

    void reloadSaver() {
        saver.reloadSaver();
    }

    boolean saverShown() {
        return saverShown;
    }

    void setSaverShown(boolean on) {
        saverShown = on;
        saveShell();
        requestLayout();
    }

    int idleMinutes() {
        return idleMinutes;
    }

    void setIdleMinutes(int m) {
        idleMinutes = m;
        saveShell();
    }

    boolean soundOn() {
        return sfx.enabled;
    }

    void setSound(boolean on) {
        sfx.enabled = on;
        saveShell();
        if (on && active != null) {
            active.mediaChanged();
        } else if (!on) {
            Music.stop();
        }
    }

    // ---------------------------------------------------------------- programs

    public List<WinApp> openApps() {
        return open;
    }

    public WinApp activeApp() {
        return active;
    }

    private WinApp create(String id) {
        WinApp app = Apps.find(id).factory.create(getContext(), this);
        open.add(app);
        addView(app, indexOfChild(divider));
        return app;
    }

    /** Opens a program, or brings it to the front if it is already running. */
    public WinApp launch(String id) {
        if (Apps.find(id) == null) {
            return null;
        }
        WinApp app = null;
        for (WinApp a : open) {
            if (a.appId.equals(id)) {
                app = a;
            }
        }
        if (app == null) {
            app = create(id);
        }
        if (active != null && active != app) {
            active.setActive(false);
        }
        active = app;
        showActive();
        saveShell();
        return app;
    }

    private void showActive() {
        for (WinApp a : open) {
            a.setVisibility(a == active ? VISIBLE : GONE);
        }
        if (active != null) {
            active.setActive(resumed && !fullscreen);
            active.requestFocus();
        }
        taskbar.invalidate();
    }

    void taskButton(WinApp app) {
        if (app == active) {
            minimizeApp(app);
        } else {
            launch(app.appId);
        }
    }

    @Override
    public void closeApp(WinApp app) {
        app.setActive(false);
        app.save();
        open.remove(app);
        removeView(app);
        if (active == app) {
            active = null;
        }
        desktop.clearSelection();
        taskbar.invalidate();
        saveShell();
    }

    @Override
    public void minimizeApp(WinApp app) {
        if (active == app) {
            app.setActive(false);
            app.setVisibility(GONE);
            active = null;
        }
        desktop.clearSelection();
        taskbar.invalidate();
        saveShell();
    }

    void closeAll() {
        for (WinApp app : new ArrayList<>(open)) {
            closeApp(app);
        }
    }

    void shutDown() {
        closeAll();
        View safe = new View(getContext()) {
            @Override
            protected void onDraw(Canvas c) {
                c.drawColor(0xFF000000);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0xFFFF8000);
                p.setTextSize(Math.min(getWidth() / 16f, 26 * desktop.ui.dp));
                p.setTextAlign(Paint.Align.CENTER);
                p.setFakeBoldText(true);
                c.drawText("It's now safe to turn off", getWidth() / 2f, getHeight() / 2f - p.getTextSize(), p);
                c.drawText("your computer.", getWidth() / 2f, getHeight() / 2f + p.getTextSize() * 0.5f, p);
            }
        };
        addView(safe);
        safe.layout(0, 0, getWidth(), getHeight());
        postDelayed(platform::finishShell, 1800);
    }

    @Override
    public void desktopOpen(String id) {
        switch (id) {
            case "display":
                taskbar.showDisplayProperties();
                break;
            case "recycle":
                taskbar.messageBox("Recycle Bin", "The Recycle Bin is empty.", null);
                break;
            case "mycomputer":
                Runtime rt = Runtime.getRuntime();
                taskbar.messageBox("My Computer", "Entertainment Pack for Android\n\n" + Apps.ALL.size()
                        + " programs, " + (Savers.ALL.size() - 1) + " screen savers\n"
                        + (rt.maxMemory() / (1024 * 1024)) + " MB memory available to programs\n"
                        + "Cards: " + (cards != null ? "your cards.dll" : "built-in"), null);
                break;
            default:
                launch(id);
                break;
        }
    }

    @Override
    public void desktopLongPress() {
        taskbar.showDisplayProperties();
    }

    void showShellHelp() {
        taskbar.helpBox("Entertainment Pack Help", "Tap a desktop icon, or use Start > Programs, to run a game. "
                + "Each game opens maximized above the taskbar; the taskbar buttons switch between running "
                + "games, and the title bar buttons minimize or close them.\n\n"
                + "The screen saver runs beside the game: above it when the phone is upright, to its left "
                + "when it is sideways. Drag the bar between them to change the split, or drag it all the way "
                + "to hide the screen saver. Double-tap the bar to go back to the size that gives the game a "
                + "4:3 screen.\n\n"
                + "Long-press the screen saver (or the desktop) to pick a different one. Display Properties "
                + "(Start > Settings > Screen Saver) also has each screen saver's own settings, a full screen "
                + "Preview, and a timer to go full screen when you stop playing.\n\n"
                + "The card games can use your own cards.dll for the original artwork: in any card game, "
                + "choose Deck... then Load cards.dll.");
    }

    // ---------------------------------------------------------------- WinApp.Host

    @Override
    public void pickFile(WinApp.FileResult result) {
        platform.pickFile(result);
    }

    void pickFiles(FilesResult result) {
        platform.pickFiles(result);
    }

    /** The imported game files or their settings changed: refresh the cards and every open game. */
    public void mediaChanged() {
        Media m = Media.get();
        if (cards == null && m.artOn() && m.has("cards.dll")) {
            try {
                setCardsDll(m.read("cards.dll"));
            } catch (IOException | RuntimeException e) {
                Log.w("Shell", "imported cards.dll unusable", e);
            }
        }
        for (WinApp app : open) {
            app.mediaChanged();
        }
    }

    @Override
    public CardsDll cardsDll() {
        return cards;
    }

    private File cardsFile() {
        return new File(getContext().getFilesDir(), "cards.dll");
    }

    private void loadCards() {
        File f = cardsFile();
        if (!f.exists()) {
            return;
        }
        try {
            cards = CardsDll.parse(readFile(f));
        } catch (IOException | RuntimeException e) {
            Log.w("Shell", "saved cards.dll unusable", e);
            cards = null;
        }
    }

    private static byte[] readFile(File f) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Override
    public void setCardsDll(byte[] data) throws IOException {
        if (data == null) {
            cards = null;
            //noinspection ResultOfMethodCallIgnored
            cardsFile().delete();
            return;
        }
        CardsDll parsed = CardsDll.parse(data);
        try (FileOutputStream out = new FileOutputStream(cardsFile())) {
            out.write(data);
        }
        cards = parsed;
    }

    @Override
    public Sfx sfx() {
        return sfx;
    }

    // ---------------------------------------------------------------- divider

    /** The Win95-style splitter bar between the screen saver and the monitor. */
    private final class Divider extends View {
        private final Win95 ui;
        private long lastDown;
        private boolean dragging;

        Divider(Context context) {
            super(context);
            ui = new Win95(context.getResources().getDisplayMetrics().density);
        }

        @Override
        protected void onDraw(Canvas c) {
            RectF r = new RectF(0, 0, getWidth(), getHeight());
            ui.raised(c, r);
            boolean vertical = getHeight() > getWidth();
            float cx = getWidth() / 2f, cy = getHeight() / 2f, d = ui.dp;
            for (int i = -1; i <= 1; i++) {
                RectF g = vertical ? new RectF(cx - 2.5f * d, cy + i * 6 * d - 1.5f * d, cx + 2.5f * d, cy + i * 6 * d + 1.5f * d)
                        : new RectF(cx + i * 6 * d - 1.5f * d, cy - 2.5f * d, cx + i * 6 * d + 1.5f * d, cy + 2.5f * d);
                ui.shallowRaised(c, g);
            }
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouchEvent(MotionEvent e) {
            boolean land = landscape(Shell.this.getWidth(), Shell.this.getHeight());
            float pos = land ? e.getX() + getLeft() : e.getY() + getTop();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    long now = SystemClock.uptimeMillis();
                    if (now - lastDown < 350) {
                        resetSplit();
                        lastDown = 0;
                        dragging = false;
                        return true;
                    }
                    lastDown = now;
                    dragging = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        dragDivider(pos);
                    }
                    break;
                default:
                    dragging = false;
                    saveShell();
                    break;
            }
            return true;
        }
    }
}
