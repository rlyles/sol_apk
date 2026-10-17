package com.rlyles.solitaire;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity implements SolitaireView.Host {
    private static final String TAG = "Solitaire";
    private static final int REQUEST_DLL = 1;
    /** Real cards.dll files are a few hundred KB; refuse anything absurd. */
    private static final int MAX_DLL_BYTES = 16 * 1024 * 1024;

    private Prefs prefs;
    private SolitaireView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        view = new SolitaireView(this, this, prefs);
        if (prefs.useDll) {
            try {
                view.setDll(CardsDll.parse(readAll(new FileInputStream(dllFile()))));
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Saved cards.dll could not be loaded", e);
                prefs.useDll = false;
                prefs.save();
            }
        }
        setContentView(view);
        view.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.onResumeGame();
    }

    @Override
    protected void onPause() {
        view.onPauseGame();
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!view.onBack()) {
            super.onBackPressed();
        }
    }

    @Override
    public void exitApp() {
        finish();
    }

    @Override
    public void minimize() {
        moveTaskToBack(true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void pickCardsDll() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_DLL);
        } catch (android.content.ActivityNotFoundException e) {
            view.showMessage("No file picker is available on this device.");
        }
    }

    @Override
    public void useBuiltInCards() {
        prefs.useDll = false;
        prefs.save();
        //noinspection ResultOfMethodCallIgnored
        dllFile().delete();
        view.setDll(null);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_DLL || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("The file could not be opened.");
            }
            byte[] bytes = readAll(in);
            CardsDll dll = CardsDll.parse(bytes);
            try (FileOutputStream out = new FileOutputStream(dllFile())) {
                out.write(bytes);
            }
            prefs.useDll = true;
            prefs.save();
            view.setDll(dll);
            view.showDeckDialog();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "cards.dll import failed", e);
            String why = e instanceof IOException && e.getMessage() != null ? e.getMessage() : "The file is not usable.";
            view.showMessage("Unable to load cards from that file.\n\n" + why);
        }
    }

    private File dllFile() {
        return new File(getFilesDir(), "cards.dll");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream src = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = src.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_DLL_BYTES) {
                    throw new IOException("That file is far too big to be cards.dll.");
                }
            }
            return out.toByteArray();
        }
    }
}
