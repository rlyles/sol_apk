package com.rlyles.solitaire;

import android.content.Context;
import android.content.SharedPreferences;

/** The Options dialog settings plus the saved game, persisted in SharedPreferences. */
final class Prefs {
    private final SharedPreferences sp;

    boolean drawThree;
    int scoring;
    boolean timed;
    boolean statusBar;
    boolean outline;
    boolean cumulative;
    boolean tapToMove;
    int back;
    boolean useDll;
    String game;

    Prefs(Context context) {
        sp = context.getSharedPreferences("solitaire", Context.MODE_PRIVATE);
        drawThree = sp.getBoolean("drawThree", true);
        scoring = sp.getInt("scoring", Game.SCORE_STANDARD);
        timed = sp.getBoolean("timed", true);
        statusBar = sp.getBoolean("statusBar", true);
        outline = sp.getBoolean("outline", false);
        cumulative = sp.getBoolean("cumulative", false);
        tapToMove = sp.getBoolean("tapToMove", true);
        back = sp.getInt("back", 0);
        useDll = sp.getBoolean("useDll", false);
        game = sp.getString("game", null);
    }

    void save() {
        sp.edit()
                .putBoolean("drawThree", drawThree)
                .putInt("scoring", scoring)
                .putBoolean("timed", timed)
                .putBoolean("statusBar", statusBar)
                .putBoolean("outline", outline)
                .putBoolean("cumulative", cumulative)
                .putBoolean("tapToMove", tapToMove)
                .putInt("back", back)
                .putBoolean("useDll", useDll)
                .putString("game", game)
                .apply();
    }
}
