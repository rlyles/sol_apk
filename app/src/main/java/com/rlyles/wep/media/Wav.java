package com.rlyles.wep.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads uncompressed PCM .WAV files (the originals are 8-bit mono, 11 or 22 kHz). */
public final class Wav {
    private Wav() {
    }

    /** Mono 16-bit samples; the sample rate goes in {@code rate[0]}. */
    public static short[] decode(byte[] d, int[] rate) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        if (d.length < 12 || bb.getInt(0) != 0x46464952 || bb.getInt(8) != 0x45564157) { // RIFF, WAVE
            throw new IOException("Not a WAV file.");
        }
        int channels = 0, bits = 0, dataAt = -1, dataLen = 0;
        int p = 12;
        while (p + 8 <= d.length) {
            int id = bb.getInt(p), len = bb.getInt(p + 4);
            if (len < 0) {
                break;
            }
            if (id == 0x20746D66 && p + 24 <= d.length) { // "fmt "
                if ((bb.getShort(p + 8) & 0xFFFF) != 1) {
                    throw new IOException("Compressed WAV files aren't supported.");
                }
                channels = bb.getShort(p + 10) & 0xFFFF;
                rate[0] = bb.getInt(p + 12);
                bits = bb.getShort(p + 22) & 0xFFFF;
            } else if (id == 0x61746164) { // "data"
                dataAt = p + 8;
                dataLen = Math.min(len, d.length - dataAt);
            }
            p += 8 + len + (len & 1);
        }
        if (dataAt < 0 || channels < 1 || (bits != 8 && bits != 16) || rate[0] < 4000 || rate[0] > 96000) {
            throw new IOException("Unsupported WAV file.");
        }
        int frame = channels * bits / 8;
        int n = dataLen / frame;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                int at = dataAt + i * frame + c * bits / 8;
                sum += bits == 8 ? ((d[at] & 0xFF) - 128) << 8 : bb.getShort(at);
            }
            out[i] = (short) (sum / channels);
        }
        return out;
    }
}
