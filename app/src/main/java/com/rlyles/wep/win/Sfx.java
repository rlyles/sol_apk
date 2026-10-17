package com.rlyles.wep.win;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.rlyles.wep.media.Media;
import com.rlyles.wep.media.Wav;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sound effects. The originals' WAVs aren't shipped; if the user imported them (see
 * {@link Media}) games can play them by name, and otherwise get a little synthesized sound in
 * the same spirit, generated on first use.
 */
public final class Sfx {
    public static final int CLICK = 0, BLIP = 1, BOUNCE = 2, BOOM = 3, WIN = 4, LOSE = 5, LINE = 6,
            CHIME = 7, POP = 8, DROP = 9, COUNT = 10;
    private static final int RATE = 22050;

    private final AudioTrack[] tracks = new AudioTrack[COUNT];
    private final Map<String, AudioTrack> waves = new HashMap<>();
    private int wavesVersion = -1;
    public boolean enabled = true;

    /**
     * Plays one of the original WAV files if the user imported it and has original sounds on,
     * otherwise the built-in {@code fallback} (or nothing if that is negative).
     */
    public void play(String wav, int fallback) {
        if (!enabled) {
            return;
        }
        Media m = Media.get();
        if (m.version() != wavesVersion) {
            releaseWaves();
            wavesVersion = m.version();
        }
        if (m.soundsOn()) {
            String key = wav.toLowerCase(Locale.US);
            if (!waves.containsKey(key)) {
                waves.put(key, load(m.read(key)));
            }
            AudioTrack t = waves.get(key);
            if (t != null) {
                restart(t);
                return;
            }
        }
        if (fallback >= 0) {
            play(fallback);
        }
    }

    private static AudioTrack load(byte[] wav) {
        if (wav == null) {
            return null;
        }
        try {
            int[] rate = new int[1];
            short[] pcm = Wav.decode(wav, rate);
            if (pcm.length == 0) {
                return null;
            }
            AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, rate[0], AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
            t.write(pcm, 0, pcm.length);
            return t;
        } catch (java.io.IOException | RuntimeException e) {
            Log.w("Sfx", "bad wav", e);
            return null;
        }
    }

    private static void restart(AudioTrack t) {
        if (t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            t.stop();
        }
        t.reloadStaticData();
        t.play();
    }

    private void releaseWaves() {
        for (AudioTrack t : waves.values()) {
            if (t != null) {
                t.release();
            }
        }
        waves.clear();
    }

    public void play(int sound) {
        if (!enabled) {
            return;
        }
        try {
            AudioTrack t = tracks[sound];
            if (t == null) {
                short[] pcm = synth(sound);
                t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
                t.write(pcm, 0, pcm.length);
                tracks[sound] = t;
            }
            restart(t);
        } catch (RuntimeException e) {
            Log.w("Sfx", "sound failed", e);
            enabled = false;
        }
    }

    public void release() {
        releaseWaves();
        for (int i = 0; i < COUNT; i++) {
            if (tracks[i] != null) {
                tracks[i].release();
                tracks[i] = null;
            }
        }
    }

    private static short[] synth(int sound) {
        switch (sound) {
            case CLICK: return tones(new float[] {1800}, 0.02f, 0.5f, false);
            case BLIP: return tones(new float[] {880, 1320}, 0.05f, 0.4f, true);
            case BOUNCE: return tones(new float[] {420}, 0.04f, 0.35f, true);
            case BOOM: return noise(0.5f);
            case WIN: return tones(new float[] {523, 659, 784, 1047}, 0.12f, 0.4f, true);
            case LOSE: return tones(new float[] {392, 330, 262}, 0.16f, 0.4f, true);
            case LINE: return tones(new float[] {660, 880, 990}, 0.06f, 0.4f, true);
            case CHIME: return tones(new float[] {1568, 2093}, 0.09f, 0.3f, false);
            case POP: return tones(new float[] {300, 900}, 0.03f, 0.4f, true);
            default: return tones(new float[] {200, 150}, 0.05f, 0.4f, true);
        }
    }

    private static short[] tones(float[] freqs, float each, float vol, boolean square) {
        int per = (int) (RATE * each);
        short[] out = new short[per * freqs.length];
        for (int n = 0; n < freqs.length; n++) {
            for (int i = 0; i < per; i++) {
                double t = i / (double) RATE;
                double v = Math.sin(2 * Math.PI * freqs[n] * t);
                if (square) {
                    v = v >= 0 ? 0.6 : -0.6;
                }
                double env = Math.min(1, Math.min(i / 60.0, (per - i) / 200.0));
                out[n * per + i] = (short) (v * env * vol * 32767);
            }
        }
        return out;
    }

    private static short[] noise(float secs) {
        int len = (int) (RATE * secs);
        short[] out = new short[len];
        java.util.Random r = new java.util.Random(3);
        double lp = 0;
        for (int i = 0; i < len; i++) {
            lp = lp * 0.85 + (r.nextDouble() * 2 - 1) * 0.15;
            double env = 1 - i / (double) len;
            out[i] = (short) (lp * env * env * 3 * 32767 * 0.5);
        }
        return out;
    }
}
