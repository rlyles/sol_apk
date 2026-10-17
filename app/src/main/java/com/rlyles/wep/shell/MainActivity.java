package com.rlyles.wep.shell;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.rlyles.wep.win.WinApp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity implements Shell.Platform {
    private static final int REQUEST_FILE = 1;
    /** Card DLLs and level files are small; refuse anything absurd. */
    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    private Shell shell;
    private WinApp.FileResult pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shell = new Shell(this, this);
        setContentView(shell);
    }

    public Shell shell() {
        return shell;
    }

    @Override
    protected void onResume() {
        super.onResume();
        shell.onResume();
    }

    @Override
    protected void onPause() {
        shell.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        shell.onDestroy();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!shell.onBack()) {
            super.onBackPressed();
        }
    }

    @Override
    public void finishShell() {
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void pickFile(WinApp.FileResult result) {
        pending = result;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_FILE);
        } catch (android.content.ActivityNotFoundException e) {
            pending = null;
            shell.taskbar.messageBox("Open", "No file picker is available on this device.", null);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WinApp.FileResult result = pending;
        pending = null;
        if (requestCode != REQUEST_FILE || resultCode != RESULT_OK || data == null || data.getData() == null
                || result == null) {
            return;
        }
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            if (in == null) {
                throw new IOException("The file could not be opened.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_FILE_BYTES) {
                    throw new IOException("That file is too big.");
                }
            }
            result.file(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            Log.w("Shell", "file open failed", e);
            shell.taskbar.messageBox("Open", "Unable to read that file.\n\n" + e.getMessage(), null);
        }
    }
}
