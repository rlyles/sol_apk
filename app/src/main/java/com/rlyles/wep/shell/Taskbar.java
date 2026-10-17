package com.rlyles.wep.shell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.rlyles.wep.media.Media;
import com.rlyles.wep.savers.Saver;
import com.rlyles.wep.savers.Savers;
import com.rlyles.wep.win.Icons;
import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;
import com.rlyles.wep.win.WinApp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The topmost layer: the taskbar with its Start menu, and the shell's own dialogs (Display
 * Properties, Shut Down...). Touches that miss all of these fall through to the views below.
 */
@SuppressLint("ViewConstructor")
public final class Taskbar extends RetroView {
    private final Shell shell;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF(), start = new RectF(), tray = new RectF();
    private final List<RectF> buttons = new ArrayList<>();

    private boolean startOpen, startPressed;
    private int pressedButton = -1;
    private boolean owning;

    // Start menu
    private static final class Entry {
        final String icon, label;
        final int submenu;
        final Runnable action;
        final boolean checked;

        Entry(String icon, String label, int submenu, boolean checked, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.submenu = submenu;
            this.checked = checked;
            this.action = action;
        }
    }

    private final RectF menu = new RectF(), sub = new RectF();
    private final List<Entry> mainEntries = new ArrayList<>();
    private final List<RectF> mainRects = new ArrayList<>();
    private final List<Entry> subEntries = new ArrayList<>();
    private final List<RectF> subRects = new ArrayList<>();
    private int openSub = -1, hotMain = -1, hotSub = -1;

    // Display Properties live preview
    private Saver preview;
    private long previewLast;

    public Taskbar(Context context, Shell shell) {
        super(context);
        this.shell = shell;
        postDelayed(clock, 30000);
    }

    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            invalidate(tray.left > 0 ? (int) tray.left : 0, (int) tray.top, (int) tray.right, (int) tray.bottom);
            postDelayed(this, 30000);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(clock);
    }

    float barHeight() {
        return 30 * ui.dp;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        if (!shell.fullscreenSaver()) {
            drawBar(c);
            if (startOpen) {
                drawStartMenu(c);
            }
        }
        drawDialogs(c);
        if (preview != null && hasDialog()) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBar(Canvas c) {
        RectF g = shell.taskRect();
        bar.set(g);
        float d = ui.dp;
        paint.setColor(Win95.FACE);
        c.drawRect(bar, paint);
        paint.setColor(Win95.LIGHT);
        c.drawRect(bar.left, bar.top, bar.right, bar.top + ui.px, paint);
        paint.setColor(Win95.HILITE);
        c.drawRect(bar.left, bar.top + ui.px, bar.right, bar.top + 2 * ui.px, paint);

        float h = bar.height() - 6 * d;
        ui.withFont(14, true, () -> {
            float w = h + ui.textWidth("Start") + 14 * d;
            start.set(bar.left + 3 * d, bar.top + 4 * d, bar.left + 3 * d + w, bar.top + 4 * d + h);
            if (startOpen || startPressed) {
                ui.pressedPanel(c, start, Win95.FACE);
            } else {
                ui.raised(c, start);
            }
            float off = startOpen || startPressed ? ui.px : 0;
            Icons.draw(c, "sol", start.left + 4 * d + off, start.top + 3 * d + off, h - 6 * d);
            ui.label(c, "Start", start.left + h + 2 * d + off, ui.baseline(start) + off, Win95.DARK);
        });

        String time = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
        float tw = ui.textWidth(time) + 16 * d;
        tray.set(bar.right - 3 * d - tw, start.top, bar.right - 3 * d, start.bottom);
        ui.shallow(c, tray);
        ui.withFont(13, false, () -> ui.label(c, time, tray.left + 8 * d, ui.baseline(tray), Win95.DARK));

        List<WinApp> apps = shell.openApps();
        buttons.clear();
        float left = start.right + 6 * d, right = tray.left - 6 * d;
        if (apps.isEmpty() || right <= left) {
            return;
        }
        float bw = Math.min(150 * d, (right - left) / apps.size() - 3 * d);
        for (int i = 0; i < apps.size(); i++) {
            WinApp app = apps.get(i);
            RectF r = new RectF(left + i * (bw + 3 * d), start.top, left + i * (bw + 3 * d) + bw, start.bottom);
            buttons.add(r);
            boolean down = app == shell.activeApp() || i == pressedButton;
            if (down) {
                ui.pressedPanel(c, r, app == shell.activeApp() ? Win95.LIGHT : Win95.FACE);
            } else {
                ui.raised(c, r);
            }
            float off = down ? ui.px : 0;
            c.save();
            c.clipRect(r.left + 2 * ui.px, r.top, r.right - 3 * ui.px, r.bottom);
            Icons.draw(c, app.appId, r.left + 4 * d + off, r.top + 3 * d + off, h - 6 * d);
            ui.withFont(13, app == shell.activeApp(), () ->
                    ui.label(c, app.title(), r.left + h + 2 * d + off, ui.baseline(r) + off, Win95.DARK));
            c.restore();
        }
    }

    // ---------------------------------------------------------------- Start menu

    private void buildMain() {
        mainEntries.clear();
        mainEntries.add(new Entry("games", "&Programs", 0, false, null));
        mainEntries.add(new Entry("settings", "&Settings", 1, false, null));
        mainEntries.add(new Entry("help", "&Help", -1, false, shell::showShellHelp));
        mainEntries.add(new Entry("shutdown", "Sh&ut Down...", -1, false, this::showShutDown));
    }

    private void buildSub(int which) {
        subEntries.clear();
        if (which == 0) {
            for (Apps.Info a : Apps.ALL) {
                subEntries.add(new Entry(a.id, a.title, -1, false, () -> shell.launch(a.id)));
            }
        } else {
            subEntries.add(new Entry("display", "Screen Saver...", -1, false, this::showDisplayProperties));
            subEntries.add(new Entry("display", "Split Screen", -1, shell.saverShown(),
                    () -> shell.setSaverShown(!shell.saverShown())));
            subEntries.add(new Entry("display", "Reset Split to 4:3", -1, false, shell::resetSplit));
            subEntries.add(new Entry("help", "Sound Effects", -1, shell.soundOn(),
                    () -> shell.setSound(!shell.soundOn())));
            subEntries.add(new Entry("folder", "Original Game Files...", -1, false, this::showOriginalFiles));
        }
    }

    private void openStart() {
        startOpen = true;
        openSub = -1;
        hotMain = hotSub = -1;
        buildMain();
        float d = ui.dp;
        float itemH = 44 * d, banner = 26 * d;
        float w = banner + 190 * d;
        float h = mainEntries.size() * itemH + 6 * ui.px;
        float bottom = bar.top + ui.px;
        float top = Math.max(0, bottom - h);
        menu.set(bar.left, top, bar.left + w, bottom);
        mainRects.clear();
        float y = menu.top + 3 * ui.px;
        float ih = (menu.height() - 6 * ui.px) / mainEntries.size();
        for (int i = 0; i < mainEntries.size(); i++) {
            mainRects.add(new RectF(menu.left + banner + 3 * ui.px, y, menu.right - 3 * ui.px, y + ih));
            y += ih;
        }
        invalidate();
    }

    private void openSubmenu(int which, RectF from) {
        openSub = which;
        hotSub = -1;
        buildSub(which);
        float d = ui.dp;
        float itemH = 36 * d;
        float availH = getHeight() - 4 * d;
        int rowsMax = Math.max(1, (int) (availH / itemH));
        int cols = (subEntries.size() + rowsMax - 1) / rowsMax;
        int rows = (subEntries.size() + cols - 1) / cols;
        float colW = 0;
        for (Entry e : subEntries) {
            colW = Math.max(colW, ui.textWidth(e.label));
        }
        colW += 54 * d;
        float w = cols * colW + 6 * ui.px;
        float h = rows * itemH + 6 * ui.px;
        float left = from.right;
        if (left + w > getWidth()) {
            left = Math.max(0, getWidth() - w);
        }
        float top = Math.min(from.top - 3 * ui.px, bar.top + ui.px - h);
        top = Math.max(0, top);
        sub.set(left, top, left + w, top + h);
        subRects.clear();
        for (int i = 0; i < subEntries.size(); i++) {
            int col = i / rows, row = i % rows;
            float x = sub.left + 3 * ui.px + col * colW, yy = sub.top + 3 * ui.px + row * itemH;
            subRects.add(new RectF(x, yy, x + colW, yy + itemH));
        }
        invalidate();
    }

    private void closeStart() {
        startOpen = false;
        openSub = -1;
        invalidate();
    }

    private void drawStartMenu(Canvas c) {
        float d = ui.dp;
        ui.raised(c, menu);
        RectF banner = new RectF(menu.left + 3 * ui.px, menu.top + 3 * ui.px, menu.left + 3 * ui.px + 24 * d,
                menu.bottom - 3 * ui.px);
        paint.setShader(new LinearGradient(0, banner.bottom, 0, banner.top, 0xFF000080, 0xFF1084D0,
                Shader.TileMode.CLAMP));
        c.drawRect(banner, paint);
        paint.setShader(null);
        c.save();
        c.rotate(-90, banner.left, banner.bottom);
        ui.withFont(16, true, () -> {
            float x = banner.left + 8 * d, y = banner.bottom + 19 * d;
            ui.text.setColor(Win95.HILITE);
            c.drawText("Entertainment", x, y, ui.text);
            float tw = ui.text.measureText("Entertainment ");
            ui.text.setTypeface(ui.regular);
            ui.text.setColor(Win95.LIGHT);
            c.drawText("Pack", x + tw, y, ui.text);
        });
        c.restore();
        for (int i = 0; i < mainEntries.size(); i++) {
            drawEntry(c, mainEntries.get(i), mainRects.get(i), i == hotMain || mainEntries.get(i).submenu == openSub
                    && openSub >= 0, 30 * d);
        }
        if (openSub >= 0) {
            ui.raised(c, sub);
            for (int i = 0; i < subEntries.size(); i++) {
                drawEntry(c, subEntries.get(i), subRects.get(i), i == hotSub, 22 * d);
            }
        }
    }

    private void drawEntry(Canvas c, Entry e, RectF r, boolean hot, float iconSize) {
        float d = ui.dp;
        if (hot) {
            paint.setColor(Win95.SELECT);
            c.drawRect(r, paint);
        }
        int color = hot ? Win95.HILITE : Win95.DARK;
        Icons.draw(c, e.icon, r.left + 5 * d, r.centerY() - iconSize / 2, iconSize);
        if (e.checked) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * d);
            float cx = r.right - 14 * d, cy = r.centerY();
            c.drawLine(cx - 4 * d, cy, cx - 1 * d, cy + 3.5f * d, paint);
            c.drawLine(cx - 1 * d, cy + 3.5f * d, cx + 5 * d, cy - 4 * d, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        ui.label(c, e.label, r.left + iconSize + 12 * d, ui.baseline(r), color);
        if (e.submenu >= 0) {
            ui.arrow(c, r.right - 10 * d, r.centerY(), 8 * d, 1, color);
        }
    }

    // ---------------------------------------------------------------- touch

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            shell.noteActivity();
            owning = hasDialog() || startOpen || (!shell.fullscreenSaver() && bar.contains(x, y));
        }
        if (!owning) {
            return false;
        }
        if (dialogTouch(e)) {
            return true;
        }
        if (startOpen) {
            startMenuTouch(e, x, y);
        } else {
            barTouch(e, x, y);
        }
        invalidate();
        return true;
    }

    private void startMenuTouch(MotionEvent e, float x, float y) {
        int action = e.getActionMasked();
        int m = indexIn(mainRects, x, y);
        int s = openSub >= 0 ? indexIn(subRects, x, y) : -1;
        if (action == MotionEvent.ACTION_DOWN && m < 0 && s < 0 && !(openSub >= 0 && sub.contains(x, y))
                && !menu.contains(x, y)) {
            closeStart();
            owning = false;
            return;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (s >= 0) {
                hotSub = s;
            } else if (m >= 0) {
                hotMain = m;
                hotSub = -1;
                Entry en = mainEntries.get(m);
                if (en.submenu >= 0 && en.submenu != openSub) {
                    openSubmenu(en.submenu, mainRects.get(m));
                } else if (en.submenu < 0) {
                    openSub = -1;
                }
            } else {
                hotSub = -1;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            Runnable run = null;
            if (s >= 0) {
                run = subEntries.get(s).action;
            } else if (m >= 0 && mainEntries.get(m).submenu < 0) {
                run = mainEntries.get(m).action;
            }
            if (run != null) {
                closeStart();
                run.run();
            }
        }
    }

    private static int indexIn(List<RectF> rects, float x, float y) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void barTouch(MotionEvent e, float x, float y) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startPressed = start.contains(x, y);
            pressedButton = indexIn(buttons, x, y);
            if (startPressed) {
                startPressed = false;
                openStart();
            }
        } else if (action == MotionEvent.ACTION_UP) {
            int hit = indexIn(buttons, x, y);
            if (hit >= 0 && hit == pressedButton && hit < shell.openApps().size()) {
                shell.taskButton(shell.openApps().get(hit));
            }
            pressedButton = -1;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            pressedButton = -1;
        }
    }

    @Override
    public boolean backPressed() {
        if (startOpen) {
            closeStart();
            return true;
        }
        return super.backPressed();
    }

    public boolean startMenuOpen() {
        return startOpen;
    }

    public void openStartForTest(int submenu) {
        RectF g = shell.taskRect();
        bar.set(g);
        float d = ui.dp;
        start.set(bar.left + 3 * d, bar.top + 4 * d, bar.left + 80 * d, bar.bottom - 2 * d);
        openStart();
        if (submenu >= 0) {
            hotMain = submenu;
            openSubmenu(submenu, mainRects.get(submenu));
        }
    }

    // ---------------------------------------------------------------- dialogs

    @Override
    protected void onDialogsChanged() {
        if (!hasDialog() && preview != null) {
            preview.release();
            preview = null;
        }
    }

    private void setPreview(String id) {
        if (preview != null) {
            preview.release();
        }
        preview = Savers.create(id);
        if (preview != null) {
            preview.attach(getContext().getSharedPreferences("saver_" + id, Context.MODE_PRIVATE), ui.dp * 0.45f);
        }
        previewLast = 0;
        invalidate();
    }

    /** Display Properties, Screen Saver page, with a live preview on the little monitor. */
    public void showDisplayProperties() {
        Win95.Dialog d = new Win95.Dialog("Display Properties", 320, 508);
        List<String> names = new ArrayList<>();
        for (Savers.Info i : Savers.ALL) {
            names.add(i.title);
        }
        Win95.ListBox list = new Win95.ListBox(names, Savers.indexOf(shell.saverId()));
        setPreview(Savers.ALL.get(list.selected).id);
        int[] previewSize = {0, 0};
        d.add(new Win95.Custom((c, r, u) -> {
            float mw = 170 * u.dp, mh = 124 * u.dp;
            RectF mon = new RectF(r.centerX() - mw / 2, r.top, r.centerX() + mw / 2, r.top + mh);
            u.raised(c, mon);
            RectF screen = new RectF(mon.left + 10 * u.dp, mon.top + 10 * u.dp, mon.right - 10 * u.dp,
                    mon.bottom - 16 * u.dp);
            u.sunken(c, new RectF(screen.left - 2 * u.px, screen.top - 2 * u.px, screen.right + 2 * u.px,
                    screen.bottom + 2 * u.px), 0xFF000000);
            paint.setColor(0xFF00C000);
            c.drawCircle(mon.right - 16 * u.dp, mon.bottom - 8 * u.dp, 2.5f * u.dp, paint);
            RectF stand = new RectF(r.centerX() - 34 * u.dp, mon.bottom, r.centerX() + 34 * u.dp, mon.bottom + 12 * u.dp);
            u.raised(c, stand);
            c.save();
            c.clipRect(screen);
            c.translate(screen.left, screen.top);
            if (preview != null) {
                int pw = (int) screen.width(), ph = (int) screen.height();
                if (pw != previewSize[0] || ph != previewSize[1]) {
                    previewSize[0] = pw;
                    previewSize[1] = ph;
                    preview.resize(pw, ph);
                }
                long now = SystemClock.uptimeMillis();
                float dt = previewLast == 0 ? 1 / 30f : Math.min(0.1f, (now - previewLast) / 1000f);
                previewLast = now;
                preview.draw(c, dt);
            } else {
                c.drawColor(Win95.DESKTOP);
            }
            c.restore();
        }, null)).at(10, 4, 300, 140);
        d.add(new Win95.Group("&Screen Saver")).at(6, 148, 308, 222);
        list.onChange = () -> {
            setPreview(Savers.ALL.get(list.selected).id);
            previewSize[0] = 0;
        };
        d.add(list).at(16, 174, 186, 186);
        list.rowDp = 30;
        d.add(new Win95.Button("Se&ttings...", false, () -> showSaverSettings(Savers.ALL.get(list.selected).id, () -> {
            setPreview(Savers.ALL.get(list.selected).id);
            previewSize[0] = 0;
        }))).at(210, 174, 96, 34);
        d.add(new Win95.Button("Pre&view", false, () -> {
            String id = Savers.ALL.get(list.selected).id;
            closeDialog(d);
            shell.previewSaver(id);
        })).at(210, 216, 96, 34);
        Win95.Check beside = d.add(new Win95.Check("Show beside &games", shell.saverShown()));
        beside.at(16, 376, 290, 28);
        Win95.Text waitLabel = d.add(new Win95.Text("", false));
        waitLabel.at(16, 408, 290, 22);
        Win95.Slider wait = d.add(new Win95.Slider(0, 30, shell.idleMinutes()));
        Runnable waitText = () -> waitLabel.body = "Full screen after: " + (wait.value == 0 ? "Never"
                : wait.value + (wait.value == 1 ? " minute" : " minutes") + " idle");
        waitText.run();
        wait.onChange = waitText;
        wait.leftLabel = "Never";
        wait.rightLabel = "30 min";
        wait.at(10, 428, 300, 40);
        Runnable apply = () -> {
            shell.setSaver(Savers.ALL.get(list.selected).id);
            shell.setSaverShown(beside.checked);
            shell.setIdleMinutes(wait.value);
        };
        d.add(new Win95.Button("OK", true, () -> {
            apply.run();
            closeDialog(d);
        })).at(10, 472, 96, 30);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(112, 472, 96, 30);
        d.add(new Win95.Button("&Apply", false, apply)).at(214, 472, 96, 30);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
        list.reveal();
    }

    /** The selected screen saver's own Setup dialog. */
    public void showSaverSettings(String id, Runnable changed) {
        Saver s = Savers.create(id);
        String title = Savers.ALL.get(Savers.indexOf(id)).title;
        if (s == null) {
            messageBox(title, "This screen saver has no options that you can set.", null);
            return;
        }
        s.attach(getContext().getSharedPreferences("saver_" + id, Context.MODE_PRIVATE), ui.dp);
        Win95.Dialog probe = new Win95.Dialog("", 300, 100);
        float used = s.settings(probe, 10, new ArrayList<>(), this);
        if (used <= 0) {
            messageBox(title + " Setup", "This screen saver has no options that you can set.", null);
            return;
        }
        Win95.Dialog d = new Win95.Dialog(title + " Setup", 300, used + 66);
        List<Runnable> apply = new ArrayList<>();
        s.settings(d, 10, apply, this);
        d.add(new Win95.Button("OK", true, () -> {
            for (Runnable r : apply) {
                r.run();
            }
            closeDialog(d);
            shell.reloadSaver();
            if (changed != null) {
                changed.run();
            }
        })).at(44, used + 20, 96, 36);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(160, used + 20, 96, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    /** Start > Settings > Original Game Files: import the user's own EXEs, DLLs and sounds. */
    public void showOriginalFiles() {
        Media m = Media.get();
        Win95.Dialog d = new Win95.Dialog("Original Game Files", 330, 520);
        Win95.Text intro = d.add(new Win95.Text("Copy in the files from your own Entertainment Pack disks and the games "
                + "use their original pictures and sounds. Pick a .zip of the game folder, or select "
                + "the .EXE, .DLL, .WAV and .MID files (and CHIPS.DAT). Only pictures and sounds are "
                + "read from them; no Windows code is run.", false));
        intro.sizeDp = 13;
        intro.at(10, 6, 310, 118);
        List<String> rows = new ArrayList<>();
        for (String[] game : Media.CATALOG) {
            rows.add(game[0] + ": " + mediaStatus(m, game));
        }
        Win95.ListBox list = d.add(new Win95.ListBox(rows, -1));
        list.rowDp = 26;
        list.at(10, 128, 310, 236);
        Win95.Check art = d.add(new Win95.Check("Use original &pictures", m.artOn()));
        art.at(10, 370, 310, 30);
        Win95.Check snd = d.add(new Win95.Check("Use original &sounds and music", m.soundsOn()));
        snd.at(10, 402, 310, 30);
        d.add(new Win95.Button("&Import Files...", false, () -> shell.pickFiles((names, data) -> {
            Media.Result r = new Media.Result();
            try {
                for (int i = 0; i < names.size(); i++) {
                    m.importFile(names.get(i), data.get(i), r);
                }
            } catch (java.io.IOException e) {
                messageBox("Original Game Files", "Unable to import those files.\n\n" + e.getMessage(), null);
                return;
            }
            shell.mediaChanged();
            closeDialog(d);
            showOriginalFiles();
            String msg = r.added.isEmpty()
                    ? "None of those are Entertainment Pack game files."
                    : "Imported " + r.added.size() + (r.added.size() == 1 ? " file" : " files") + ":\n"
                    + android.text.TextUtils.join(", ", r.added).toUpperCase(java.util.Locale.US);
            messageBox("Original Game Files", msg, null);
        }))).at(10, 440, 150, 34);
        d.add(new Win95.Button("&Remove All", false, () -> confirm("Original Game Files",
                "Remove all the imported game files from this device?", () -> {
                    m.removeAll();
                    shell.mediaChanged();
                    closeDialog(d);
                    showOriginalFiles();
                }, null))).at(170, 440, 150, 34);
        d.add(new Win95.Button("OK", true, () -> {
            m.setArt(art.checked);
            m.setSounds(snd.checked);
            shell.mediaChanged();
            closeDialog(d);
        })).at(66, 482, 96, 32);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(170, 482, 96, 32);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    private static String mediaStatus(Media m, String[] game) {
        StringBuilder found = new StringBuilder();
        boolean missing = false;
        for (int i = 1; i < game.length; i++) {
            String hit = null;
            for (String f : game[i].split("\\|")) {
                if (m.has(f)) {
                    hit = f;
                    break;
                }
            }
            if (hit == null) {
                missing |= i == 1;
                continue;
            }
            String what = hit.endsWith(".wav") || hit.endsWith(".mid") ? "sound" : hit.endsWith(".dat") ? "levels"
                    : "pictures";
            if (found.indexOf(what) >= 0) {
                continue;
            }
            if (found.length() > 0) {
                found.append(", ");
            }
            found.append(what);
        }
        if (found.length() == 0 || missing) {
            return "needs " + game[1].split("\\|")[0].toUpperCase(java.util.Locale.US);
        }
        return found.toString();
    }

    private void showShutDown() {
        Win95.Dialog d = new Win95.Dialog("Shut Down Windows", 330, 220);
        d.add(new Win95.Custom((c, r, u) -> Icons.draw(c, "shutdown", r.left, r.top, r.width()), null))
                .at(12, 12, 40, 40);
        d.add(new Win95.Text("Are you sure you want to:", false)).at(64, 12, 256, 24);
        d.add(new Win95.Radio("&Shut down the computer?", 0, true)).at(64, 40, 256, 30);
        d.add(new Win95.Radio("&Restart the computer?", 0, false)).at(64, 72, 256, 30);
        d.add(new Win95.Radio("&Close all programs and log on?", 0, false)).at(64, 104, 256, 30);
        d.add(new Win95.Button("&Yes", true, () -> {
            int choice = Win95.Radio.selected(d, 0);
            closeDialog(d);
            if (choice == 0) {
                shell.shutDown();
            } else {
                shell.closeAll();
            }
        })).at(24, 170, 86, 36);
        d.add(new Win95.Button("&No", false, () -> closeDialog(d))).at(122, 170, 86, 36);
        d.add(new Win95.Button("&Help", false, shell::showShellHelp)).at(220, 170, 86, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }
}
