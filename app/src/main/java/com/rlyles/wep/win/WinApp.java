package com.rlyles.wep.win;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.rlyles.wep.cards.CardsDll;

import java.util.ArrayList;
import java.util.List;

/**
 * A maximized Windows 95 application window: title bar, menu bar, optional status bar and a
 * client area that the game draws. Subclasses supply the menus and the client behaviour.
 */
public abstract class WinApp extends RetroView {

    /** What an app needs from the desktop shell. */
    public interface Host {
        void closeApp(WinApp app);

        void minimizeApp(WinApp app);

        /** Lets the user pick a file; the bytes are delivered to {@code result} (or nothing on cancel). */
        void pickFile(FileResult result);

        /** The user's cards.dll, or null for the built-in cards. */
        CardsDll cardsDll();

        void setCardsDll(byte[] data) throws java.io.IOException;

        Sfx sfx();
    }

    public interface FileResult {
        void file(byte[] data);
    }

    public interface Action {
        void run();
    }

    /** A drop-down menu item. */
    public static final class Item {
        public final String label, accel, help;
        public final boolean enabled, checked, separator;
        public final Runnable action;

        public Item(String label, String accel, String help, boolean enabled, boolean checked, Runnable action) {
            this.label = label;
            this.accel = accel;
            this.help = help;
            this.enabled = enabled;
            this.checked = checked;
            this.action = action;
            this.separator = false;
        }

        private Item() {
            label = accel = help = "";
            enabled = checked = false;
            separator = true;
            action = null;
        }

        public static Item sep() {
            return new Item();
        }
    }

    public interface MenuBuilder {
        void build(List<Item> items);
    }

    public static final class Menu {
        final String title;
        final MenuBuilder builder;
        final RectF rect = new RectF();

        public Menu(String title, MenuBuilder builder) {
            this.title = title;
            this.builder = builder;
        }
    }

    protected final Host host;
    protected final SharedPreferences prefs;
    public final String appId;
    protected final float dp;
    private final float touchSlop;

    /** The area the game draws in. */
    protected final RectF client = new RectF();
    private final RectF titleBar = new RectF(), menuBar = new RectF(), statusBar = new RectF();
    private final RectF minBox = new RectF(), maxBox = new RectF(), closeBox = new RectF();
    private final List<Menu> menus = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int openMenu = -1, hotItem = -1;
    private final RectF dropdown = new RectF();
    private final List<Item> items = new ArrayList<>();
    private final List<RectF> itemRects = new ArrayList<>();

    private enum Owner { NONE, DIALOG, MENU, TITLE, CLIENT }

    private Owner owner = Owner.NONE;
    private int titlePressed = -1;
    private boolean active;
    private boolean animating;
    private long lastFrame;
    private float downX, downY;
    private boolean longPressed;
    private final Runnable longPress = () -> {
        longPressed = true;
        clientLongPress(downX, downY);
        invalidate();
    };
    private final Runnable second = new Runnable() {
        @Override
        public void run() {
            if (!active) {
                return;
            }
            if (!uiBlocking()) {
                tick();
            }
            postDelayed(this, 1000);
        }
    };

    protected WinApp(Context context, Host host, String appId) {
        super(context);
        this.host = host;
        this.appId = appId;
        this.prefs = context.getSharedPreferences("app_" + appId, Context.MODE_PRIVATE);
        this.dp = ui.dp;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    // ---------------------------------------------------------------- for subclasses

    public abstract String title();

    /** Title bar text; defaults to {@link #title()}. */
    protected String caption() {
        return title();
    }

    /** Called once, lazily, to collect the menu bar. */
    protected abstract void buildMenus(List<Menu> menus);

    protected boolean hasStatusBar() {
        return false;
    }

    /** Right-hand status bar panels, e.g. "Score: 10". */
    protected void statusPanels(List<String> panels) {
    }

    /** Text shown at the right-hand end of the menu bar (FreeCell's "Cards Left"). */
    protected String menuBarText() {
        return "";
    }

    /** Left status bar text when no menu item is highlighted. */
    protected String statusText() {
        return "";
    }

    protected abstract void layoutClient(RectF client);

    protected abstract void drawClient(Canvas c);

    protected void clientTouch(MotionEvent e) {
    }

    protected void clientLongPress(float x, float y) {
    }

    /** Whether a long press in the client should be detected (it delays nothing else). */
    protected boolean wantsLongPress() {
        return false;
    }

    /** Called every frame while {@link #setAnimating} is on and no menu or dialog is open. */
    protected void frame(float dt) {
    }

    /** Called once a second while the app is in front and no menu or dialog is open. */
    protected void tick() {
    }

    /** Persist whatever should survive the app being closed. */
    public void save() {
    }

    protected void onShown() {
    }

    protected void onHidden() {
    }

    protected boolean key(int keyCode, KeyEvent event) {
        return false;
    }

    protected void setAnimating(boolean on) {
        if (on && !animating) {
            lastFrame = 0;
            postInvalidateOnAnimation();
        }
        animating = on;
    }

    /** True while a menu or dialog is up; real-time games should hold still. */
    protected boolean uiBlocking() {
        return openMenu >= 0 || hasDialog();
    }

    public boolean isActive() {
        return active;
    }

    protected void sound(int s) {
        host.sfx().play(s);
    }

    /** Standard Help menu: help topics and About. */
    protected void addHelpMenu(List<Menu> menus, String helpBody, String aboutBody) {
        menus.add(new Menu("&Help", items -> {
            items.add(new Item("&Contents", "F1", "Help topics for " + title(), true, false,
                    () -> helpBox(title() + " Help", helpBody)));
            items.add(Item.sep());
            items.add(new Item("&About " + title() + "...", "", "About " + title(), true, false,
                    () -> about(aboutBody)));
        }));
    }

    protected void about(String body) {
        float th = textHeight(body, 236);
        Win95.Dialog d = new Win95.Dialog("About " + title(), 300, Math.max(130, th + 80));
        d.add(new Win95.Custom((c, r, u) -> Icons.draw(c, appId, r.left, r.top, r.width()), null)).at(14, 14, 40, 40);
        Win95.Text name = d.add(new Win95.Text(title(), true));
        name.at(64, 12, 224, 24);
        d.add(new Win95.Text(body, false)).at(64, 40, 228, th);
        d.add(new Win95.Button("OK", true, () -> closeDialog(d))).at(102, Math.max(130, th + 80) - 46, 96, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    // ---------------------------------------------------------------- activation

    public void setActive(boolean on) {
        if (on == active) {
            return;
        }
        active = on;
        removeCallbacks(second);
        if (on) {
            postDelayed(second, 1000);
            lastFrame = 0;
            onShown();
            invalidate();
        } else {
            closeMenu();
            onHidden();
            save();
        }
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        closeMenu();
        float d = dp;
        titleBar.set(0, 0, w, 28 * d);
        float bs = titleBar.height() - 8 * d;
        closeBox.set(w - 4 * d - bs * 1.15f, 4 * d, w - 4 * d, 4 * d + bs);
        maxBox.set(closeBox.left - 3 * d - bs * 1.15f, closeBox.top, closeBox.left - 3 * d, closeBox.bottom);
        minBox.set(maxBox.left - bs * 1.15f, closeBox.top, maxBox.left, closeBox.bottom);
        menuBar.set(0, titleBar.bottom, w, titleBar.bottom + 30 * d);
        ensureMenus();
        float x = 4 * d;
        for (Menu m : menus) {
            float mw = ui.textWidth(m.title) + 18 * d;
            m.rect.set(x, menuBar.top + 2 * d, x + mw, menuBar.bottom - 2 * d);
            x += mw;
        }
        float statusH = hasStatusBar() ? 26 * d : 0;
        statusBar.set(0, h - statusH, w, h);
        client.set(0, menuBar.bottom, w, h - statusH);
        layoutClient(client);
    }

    /** Re-run layout, e.g. after the status bar option changes. */
    protected void relayout() {
        onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        invalidate();
    }

    private void ensureMenus() {
        if (menus.isEmpty()) {
            buildMenus(menus);
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        if (animating && active) {
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
            lastFrame = now;
            if (!uiBlocking()) {
                frame(dt);
            }
        }
        c.save();
        c.clipRect(client);
        drawClient(c);
        c.restore();
        drawChrome(c);
        if (openMenu >= 0) {
            drawMenu(c);
        }
        drawDialogs(c);
        if (animating && active) {
            postInvalidateOnAnimation();
        }
    }

    private void drawChrome(Canvas c) {
        paint.setColor(Win95.TITLE);
        c.drawRect(titleBar, paint);
        Icons.draw(c, appId, 5 * dp, titleBar.top + 4 * dp, titleBar.height() - 8 * dp);
        ui.withFont(15, true, () -> ui.label(c, caption(), 8 * dp + titleBar.height(), ui.baseline(titleBar),
                Win95.TITLE_TEXT));
        captionButton(c, minBox, 0, titlePressed == 0);
        captionButton(c, maxBox, 1, false);
        captionButton(c, closeBox, 2, titlePressed == 2);

        paint.setColor(Win95.FACE);
        c.drawRect(menuBar, paint);
        for (int i = 0; i < menus.size(); i++) {
            Menu m = menus.get(i);
            boolean open = openMenu == i;
            if (open) {
                ui.shallow(c, m.rect);
            }
            float off = open ? ui.px : 0;
            ui.label(c, m.title, m.rect.left + 9 * dp + off, ui.baseline(m.rect) + off, Win95.DARK);
        }
        String extra = menuBarText();
        if (!extra.isEmpty()) {
            ui.label(c, extra, getWidth() - ui.textWidth(extra) - 10 * dp, ui.baseline(menuBar), Win95.DARK);
        }
        paint.setColor(Win95.SHADOW);
        c.drawRect(0, menuBar.bottom - ui.px, getWidth(), menuBar.bottom, paint);
        if (hasStatusBar()) {
            drawStatusBar(c);
        }
    }

    private void captionButton(Canvas c, RectF r, int kind, boolean pressed) {
        ui.button(c, r, "", pressed, true, false);
        float off = pressed ? ui.px : 0;
        paint.setColor(Win95.DARK);
        float s = r.height() * 0.22f, cx = r.centerX() + off, cy = r.centerY() + off;
        float lw = Math.max(2f, r.height() * 0.1f);
        if (kind == 0) {
            c.drawRect(cx - s, cy + s - lw, cx + s * 0.6f, cy + s, paint);
        } else if (kind == 1) {
            // Maximized windows show the "restore" glyph: two overlapping boxes.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, lw / 2));
            float o = s * 0.45f;
            c.drawRect(cx - s + o, cy - s, cx + s, cy + s - o, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Win95.FACE);
            c.drawRect(cx - s, cy - s + o, cx + s - o, cy + s, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Win95.DARK);
            c.drawRect(cx - s, cy - s + o, cx + s - o, cy + s, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            ui.glyphX(c, new RectF(r.left + off, r.top + off, r.right + off, r.bottom + off), Win95.DARK);
        }
    }

    private void drawStatusBar(Canvas c) {
        paint.setColor(Win95.FACE);
        c.drawRect(statusBar, paint);
        paint.setColor(Win95.HILITE);
        c.drawRect(0, statusBar.top, getWidth(), statusBar.top + ui.px, paint);
        float top = statusBar.top + 3 * dp, bottom = statusBar.bottom - 3 * dp;
        List<String> panels = new ArrayList<>();
        statusPanels(panels);
        float x = getWidth() - 3 * dp;
        RectF r = new RectF();
        for (int i = panels.size() - 1; i >= 0; i--) {
            String p = panels.get(i);
            float w = Math.max(ui.textWidth(p) + 14 * dp, 64 * dp);
            r.set(x - w, top, x, bottom);
            ui.shallow(c, r);
            c.save();
            c.clipRect(r);
            ui.label(c, p, r.left + 7 * dp, ui.baseline(r), Win95.DARK);
            c.restore();
            x = r.left - 3 * dp;
        }
        r.set(3 * dp, top, Math.max(3 * dp, x), bottom);
        ui.shallow(c, r);
        String help = openMenu >= 0 && hotItem >= 0 && hotItem < items.size() ? items.get(hotItem).help : statusText();
        if (help != null && !help.isEmpty()) {
            c.save();
            c.clipRect(r);
            ui.label(c, help, r.left + 6 * dp, ui.baseline(r), Win95.DARK);
            c.restore();
        }
    }

    // ---------------------------------------------------------------- menus

    private void openMenu(int which) {
        openMenu = which;
        hotItem = -1;
        items.clear();
        menus.get(which).builder.build(items);
        float d = dp;
        float labelW = 0, accelW = 0;
        int real = 0, seps = 0;
        for (Item m : items) {
            labelW = Math.max(labelW, ui.textWidth(m.label));
            accelW = Math.max(accelW, ui.textWidth(m.accel));
            if (m.separator) {
                seps++;
            } else {
                real++;
            }
        }
        float avail = getHeight() - menuBar.bottom - 4 * d;
        float itemH = 38 * d, sepH = 9 * d;
        float need = real * itemH + seps * sepH + 6 * ui.px;
        if (need > avail) {
            float k = avail / need;
            itemH *= k;
            sepH *= k;
        }
        float w = Math.max(170 * d, labelW + accelW + 70 * d);
        float h = 6 * ui.px + real * itemH + seps * sepH;
        RectF t = menus.get(which).rect;
        float left = Math.min(t.left, getWidth() - w - 2 * d);
        dropdown.set(left, menuBar.bottom - ui.px, left + w, menuBar.bottom - ui.px + h);
        itemRects.clear();
        float y = dropdown.top + 3 * ui.px;
        for (Item m : items) {
            float ih = m.separator ? sepH : itemH;
            itemRects.add(new RectF(dropdown.left + 3 * ui.px, y, dropdown.right - 3 * ui.px, y + ih));
            y += ih;
        }
        invalidate();
    }

    protected void closeMenu() {
        openMenu = -1;
        hotItem = -1;
        invalidate();
    }

    private void drawMenu(Canvas c) {
        ui.raised(c, dropdown);
        for (int i = 0; i < items.size(); i++) {
            Item m = items.get(i);
            RectF r = itemRects.get(i);
            if (m.separator) {
                float y = r.centerY();
                paint.setColor(Win95.SHADOW);
                c.drawRect(r.left + ui.px, y - ui.px, r.right - ui.px, y, paint);
                paint.setColor(Win95.HILITE);
                c.drawRect(r.left + ui.px, y, r.right - ui.px, y + ui.px, paint);
                continue;
            }
            boolean hot = i == hotItem;
            if (hot) {
                paint.setColor(Win95.SELECT);
                c.drawRect(r, paint);
            }
            int color = hot ? Win95.HILITE : Win95.DARK;
            float base = ui.baseline(r);
            float x = r.left + 22 * dp;
            if (m.checked) {
                paint.setColor(m.enabled ? color : Win95.SHADOW);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * dp);
                float cx = r.left + 11 * dp, cy = r.centerY();
                c.drawLine(cx - 4 * dp, cy, cx - 1 * dp, cy + 3.5f * dp, paint);
                c.drawLine(cx - 1 * dp, cy + 3.5f * dp, cx + 5 * dp, cy - 4 * dp, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (!m.enabled) {
                ui.disabledLabel(c, m.label, x, base);
            } else {
                ui.label(c, m.label, x, base, color);
            }
            if (!m.accel.isEmpty()) {
                float ax = r.right - 12 * dp - ui.textWidth(m.accel);
                if (m.enabled) {
                    ui.label(c, m.accel, ax, base, color);
                } else {
                    ui.disabledLabel(c, m.accel, ax, base);
                }
            }
        }
    }

    private int itemAt(float x, float y) {
        for (int i = 0; i < itemRects.size(); i++) {
            if (itemRects.get(i).contains(x, y)) {
                Item m = items.get(i);
                return m.separator || !m.enabled ? -1 : i;
            }
        }
        return -1;
    }

    private int menuTitleAt(float x, float y) {
        if (y < menuBar.top || y >= menuBar.bottom) {
            return -1;
        }
        for (int i = 0; i < menus.size(); i++) {
            if (x >= menus.get(i).rect.left && x < menus.get(i).rect.right) {
                return i;
            }
        }
        return -1;
    }

    private void menuTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int title = menuTitleAt(x, y);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (title >= 0) {
                    if (title == openMenu) {
                        closeMenu();
                        owner = Owner.NONE;
                    } else {
                        openMenu(title);
                    }
                } else if (openMenu >= 0 && dropdown.contains(x, y)) {
                    hotItem = itemAt(x, y);
                } else {
                    closeMenu();
                    owner = Owner.NONE;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (openMenu < 0) {
                    break;
                }
                if (title >= 0 && title != openMenu) {
                    openMenu(title);
                } else if (dropdown.contains(x, y)) {
                    hotItem = itemAt(x, y);
                } else {
                    hotItem = -1;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (openMenu >= 0 && dropdown.contains(x, y)) {
                    int item = itemAt(x, y);
                    if (item >= 0) {
                        Runnable action = items.get(item).action;
                        closeMenu();
                        if (action != null) {
                            action.run();
                        }
                    }
                }
                owner = Owner.NONE;
                break;
            default:
                owner = Owner.NONE;
                break;
        }
        invalidate();
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (hasDialog()) {
                owner = Owner.DIALOG;
            } else if (openMenu >= 0 || menuTitleAt(x, y) >= 0) {
                owner = Owner.MENU;
            } else if (titleBar.contains(x, y)) {
                owner = Owner.TITLE;
            } else if (client.contains(x, y)) {
                owner = Owner.CLIENT;
            } else {
                owner = Owner.NONE;
            }
        }
        switch (owner) {
            case DIALOG:
                dialogTouch(e);
                break;
            case MENU:
                menuTouch(e);
                break;
            case TITLE:
                titleTouch(e);
                break;
            case CLIENT:
                routeClient(e);
                break;
            default:
                break;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            owner = Owner.NONE;
        }
        return true;
    }

    private void routeClient(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longPressed = false;
                if (wantsLongPress()) {
                    postDelayed(longPress, 450);
                }
                clientTouch(e);
                break;
            case MotionEvent.ACTION_MOVE:
                if (longPressed) {
                    return;
                }
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > touchSlop) {
                    removeCallbacks(longPress);
                }
                clientTouch(e);
                break;
            default:
                removeCallbacks(longPress);
                if (longPressed) {
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    clientTouch(cancel);
                    cancel.recycle();
                } else {
                    clientTouch(e);
                }
                break;
        }
        invalidate();
    }

    private void titleTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int hit = minBox.contains(x, y) ? 0 : closeBox.contains(x, y) ? 2 : -1;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                titlePressed = hit;
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                int pressed = titlePressed;
                titlePressed = -1;
                if (pressed >= 0 && pressed == hit) {
                    if (hit == 0) {
                        host.minimizeApp(this);
                    } else {
                        host.closeApp(this);
                    }
                }
                break;
            default:
                titlePressed = -1;
                break;
        }
        invalidate();
    }

    @Override
    public boolean backPressed() {
        if (openMenu >= 0) {
            closeMenu();
            return true;
        }
        return super.backPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!hasDialog() && key(keyCode, event)) {
            invalidate();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- test hooks

    public void openMenuForTest(int which, int hot) {
        ensureMenus();
        openMenu(which);
        hotItem = hot;
        invalidate();
    }

    /** Runs the menu item whose label (without '&amp;') matches; returns false if not found or disabled. */
    public boolean runMenuItem(String label) {
        ensureMenus();
        for (Menu m : menus) {
            List<Item> list = new ArrayList<>();
            m.builder.build(list);
            for (Item it : list) {
                if (!it.separator && it.label.replace("&", "").equals(label) && it.enabled) {
                    it.action.run();
                    invalidate();
                    return true;
                }
            }
        }
        return false;
    }

    /** Advances the animation by {@code seconds} in fixed steps (tests). */
    public void stepForTest(float seconds) {
        float step = 1 / 60f;
        for (float t = 0; t < seconds; t += step) {
            frame(step);
        }
    }

    public RectF clientRect() {
        return client;
    }
}
