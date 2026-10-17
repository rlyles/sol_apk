package com.rlyles.wep.win;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** A View drawn with the {@link Win95} toolkit that can host a stack of modal dialogs. */
public abstract class RetroView extends View {
    public interface TextResult {
        void done(String text);
    }

    public final Win95 ui;
    private final List<Win95.Dialog> dialogs = new ArrayList<>();

    protected RetroView(Context context) {
        super(context);
        ui = new Win95(context.getResources().getDisplayMetrics().density);
    }

    public void showDialog(Win95.Dialog d) {
        d.layout(ui, Math.max(1, getWidth()), Math.max(1, getHeight()));
        dialogs.add(d);
        onDialogsChanged();
        invalidate();
    }

    /** Closes the topmost dialog. */
    public void closeDialog() {
        if (!dialogs.isEmpty()) {
            dialogs.remove(dialogs.size() - 1);
            onDialogsChanged();
        }
        invalidate();
    }

    public void closeDialog(Win95.Dialog d) {
        dialogs.remove(d);
        onDialogsChanged();
        invalidate();
    }

    public boolean hasDialog() {
        return !dialogs.isEmpty();
    }

    public Win95.Dialog topDialog() {
        return dialogs.isEmpty() ? null : dialogs.get(dialogs.size() - 1);
    }

    protected void onDialogsChanged() {
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        for (Win95.Dialog d : dialogs) {
            d.layout(ui, w, h);
        }
    }

    protected void drawDialogs(Canvas c) {
        for (Win95.Dialog d : dialogs) {
            d.draw(c, ui);
        }
    }

    /** Routes a touch to the topmost dialog; returns false when no dialog is open. */
    protected boolean dialogTouch(MotionEvent e) {
        if (dialogs.isEmpty()) {
            return false;
        }
        topDialog().onTouch(e);
        invalidate();
        return true;
    }

    /** Back button: cancels the topmost dialog. */
    public boolean backPressed() {
        Win95.Dialog d = topDialog();
        if (d == null) {
            return false;
        }
        if (d.onCancel != null) {
            d.onCancel.run();
        } else {
            closeDialog();
        }
        return true;
    }

    // ---------------------------------------------------------------- stock dialogs

    public void messageBox(String title, String message, Runnable ok) {
        Win95.Dialog d = new Win95.Dialog(title, 300, 0 + textHeight(message, 276) + 70);
        d.add(new Win95.Text(message, false)).at(12, 12, 276, textHeight(message, 276));
        Runnable close = () -> {
            closeDialog(d);
            if (ok != null) {
                ok.run();
            }
        };
        d.add(new Win95.Button("OK", true, close)).at(102, textHeight(message, 276) + 24, 96, 36);
        d.onCancel = close;
        showDialog(d);
    }

    public void confirm(String title, String message, Runnable yes, Runnable no) {
        float th = textHeight(message, 276);
        Win95.Dialog d = new Win95.Dialog(title, 300, th + 70);
        d.add(new Win95.Text(message, false)).at(12, 12, 276, th);
        d.add(new Win95.Button("&Yes", true, () -> {
            closeDialog(d);
            if (yes != null) {
                yes.run();
            }
        })).at(44, th + 24, 96, 36);
        Runnable noAction = () -> {
            closeDialog(d);
            if (no != null) {
                no.run();
            }
        };
        d.add(new Win95.Button("&No", false, noAction)).at(160, th + 24, 96, 36);
        d.onCancel = noAction;
        showDialog(d);
    }

    /** Approximate height in dp that {@code message} needs when wrapped to {@code widthDp}. */
    public float textHeight(String message, float widthDp) {
        int lines = 0;
        for (String para : message.split("\n", -1)) {
            lines += Win95.Text.wrap(ui.text, para, widthDp * ui.dp).size();
        }
        return lines * ui.text.getFontSpacing() / ui.dp + 4;
    }

    /** Help topics in a scrolling box. */
    public void helpBox(String title, String body) {
        Win95.Dialog d = new Win95.Dialog(title, 340, 420);
        d.add(new Win95.Scroller(body)).at(8, 8, 324, 352);
        d.add(new Win95.Button("OK", true, () -> closeDialog(d))).at(120, 372, 100, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }

    /**
     * Text entry through an on-screen Windows-style keyboard, so no system keyboard is needed.
     */
    public void prompt(String title, String label, String initial, boolean numeric, int maxLen, TextResult result) {
        StringBuilder value = new StringBuilder(initial == null ? "" : initial);
        boolean[] shift = {false};
        float w = numeric ? 236 : 344;
        float keyH = 40;
        int rows = numeric ? 4 : 5;
        float top = 64;
        float h = top + rows * (keyH + 4) + 54;
        Win95.Dialog d = new Win95.Dialog(title, w, h);
        d.add(new Win95.Text(label, false)).at(10, 6, w - 20, 22);
        Win95.Edit field = d.add(new Win95.Edit(value + "_"));
        field.at(10, 28, w - 20, 30);
        Runnable refresh = () -> field.value = value + "_";
        Runnable onKeyAdded = () -> {
            if (value.length() > maxLen) {
                value.setLength(maxLen);
            }
            refresh.run();
        };
        if (numeric) {
            String[] keys = {"7", "8", "9", "4", "5", "6", "1", "2", "3", "Clr", "0", "Del"};
            float kw = (w - 20 - 8) / 3f;
            for (int i = 0; i < keys.length; i++) {
                String k = keys[i];
                float x = 10 + (i % 3) * (kw + 4), y = top + (i / 3) * (keyH + 4);
                d.add(new Win95.Button(k, false, () -> {
                    if (k.equals("Del")) {
                        if (value.length() > 0) {
                            value.setLength(value.length() - 1);
                        }
                    } else if (k.equals("Clr")) {
                        value.setLength(0);
                    } else {
                        value.append(k);
                    }
                    onKeyAdded.run();
                })).at(x, y, kw, keyH);
            }
        } else {
            String[] lines = {"1234567890", "QWERTYUIOP", "ASDFGHJKL'", "ZXCVBNM,.-"};
            float kw = (w - 20 - 9 * 3) / 10f;
            List<Win95.Button> letters = new ArrayList<>();
            for (int row = 0; row < lines.length; row++) {
                String line = lines[row];
                for (int i = 0; i < line.length(); i++) {
                    char ch = line.charAt(i);
                    float x = 10 + i * (kw + 3), y = top + row * (keyH + 4);
                    Win95.Button b = d.add(new Win95.Button(String.valueOf(ch), false, () -> {
                        value.append(shift[0] ? ch : Character.toLowerCase(ch));
                        onKeyAdded.run();
                    }));
                    b.at(x, y, kw, keyH);
                    letters.add(b);
                }
            }
            float y = top + 4 * (keyH + 4);
            d.add(new Win95.Button("Shift", false, () -> shift[0] = !shift[0])).at(10, y, 64, keyH);
            d.add(new Win95.Button("Space", false, () -> {
                value.append(' ');
                onKeyAdded.run();
            })).at(80, y, w - 80 - 84, keyH);
            d.add(new Win95.Button("Del", false, () -> {
                if (value.length() > 0) {
                    value.setLength(value.length() - 1);
                }
                refresh.run();
            })).at(w - 78, y, 68, keyH);
        }
        float by = top + rows * (keyH + 4) + 8;
        d.add(new Win95.Button("OK", true, () -> {
            closeDialog(d);
            result.done(value.toString().trim());
        })).at(w / 2 - 104, by, 96, 36);
        d.add(new Win95.Button("Cancel", false, () -> closeDialog(d))).at(w / 2 + 8, by, 96, 36);
        d.onCancel = () -> closeDialog(d);
        showDialog(d);
    }
}
