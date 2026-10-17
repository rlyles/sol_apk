package com.rlyles.wep.media;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;

/** Background music from the user's original MIDI files (Android plays .MID natively). */
public final class Music {
    private static MediaPlayer player;
    private static String playing;

    private Music() {
    }

    /** Loops {@code name} (an imported .MID) if original sounds are on; stops otherwise. */
    public static void play(String name) {
        Media m = Media.get();
        if (!m.soundsOn() || !m.has(name)) {
            stop();
            return;
        }
        if (name.equals(playing) && player != null) {
            if (!player.isPlaying()) {
                player.start();
            }
            return;
        }
        stop();
        File f = m.file(name);
        try {
            MediaPlayer p = new MediaPlayer();
            p.setDataSource(f.getAbsolutePath());
            p.setLooping(true);
            p.prepare();
            p.start();
            player = p;
            playing = name;
        } catch (Exception e) { // IOException, IllegalStateException, or a device without MIDI
            Log.w("Music", "can't play " + name, e);
            stop();
        }
    }

    public static void stop() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // never started
            }
            player.release();
            player = null;
        }
        playing = null;
    }
}
