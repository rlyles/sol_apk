package com.rlyles.wep.shell;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;

import com.rlyles.wep.win.WinApp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Shell.Platform {
    private static final int REQUEST_FILE = 1, REQUEST_FILES = 2;
    /** Card DLLs, level files and the game folders are small; refuse anything absurd. */
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;

    private Shell shell;
    private WinApp.FileResult pending;
    private Shell.FilesResult pendingFiles;

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
    public void pickFiles(Shell.FilesResult result) {
        pendingFiles = result;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQUEST_FILES);
        } catch (android.content.ActivityNotFoundException e) {
            pendingFiles = null;
            shell.taskbar.messageBox("Open", "No file picker is available on this device.", null);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILES) {
            Shell.FilesResult result = pendingFiles;
            pendingFiles = null;
            if (resultCode == RESULT_OK && data != null && result != null) {
                deliverFiles(data, result);
            }
            return;
        }
        WinApp.FileResult result = pending;
        pending = null;
        if (requestCode != REQUEST_FILE || resultCode != RESULT_OK || data == null || data.getData() == null
                || result == null) {
            return;
        }
        try {
            result.file(read(data.getData()));
        } catch (IOException | RuntimeException e) {
            Log.w("Shell", "file open failed", e);
            shell.taskbar.messageBox("Open", "Unable to read that file.\n\n" + e.getMessage(), null);
        }
    }

    private void deliverFiles(Intent data, Shell.FilesResult result) {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        List<String> names = new ArrayList<>();
        List<byte[]> bytes = new ArrayList<>();
        try {
            for (Uri uri : uris) {
                names.add(displayName(uri));
                bytes.add(read(uri));
            }
            result.files(names, bytes);
        } catch (IOException | RuntimeException e) {
            Log.w("Shell", "file open failed", e);
            shell.taskbar.messageBox("Open", "Unable to read those files.\n\n" + e.getMessage(), null);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            }
        } catch (RuntimeException e) {
            Log.w("Shell", "no name for " + uri, e);
        }
        String path = uri.getLastPathSegment();
        return path == null ? "" : path;
    }

    private byte[] read(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
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
            return out.toByteArray();
        }
    }
}
