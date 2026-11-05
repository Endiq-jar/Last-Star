package com.laststar.game.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

/**
 * Procedurally synthesised sound effects.
 *
 * <p>Every sound in the game is generated at start-up into a short PCM buffer
 * and played on a small pool of {@link AudioTrack} instances. This keeps the
 * APK free of audio assets and makes latency predictable.</p>
 */
public final class SoundFx {

    public enum Id {
        SWING,
        HIT,
        ENEMY_HIT,
        JUMP,
        LAND,
        SHARD,
        HURT,
        VICTORY,
        UI_TAP
    }

    private static final int SAMPLE_RATE = 22050;
    private static final int MAX_VOICES = 5;

    private final short[][] samples = new short[Id.values().length][];
    private final AudioTrack[] voices = new AudioTrack[MAX_VOICES];
    private int nextVoice;
    private boolean enabled = true;
    private float volume = 0.85f;

    public void init() {
        samples[Id.SWING.ordinal()] = swing();
        samples[Id.HIT.ordinal()] = thud(0.20f, 140f, 0.55f);
        samples[Id.ENEMY_HIT.ordinal()] = thud(0.16f, 90f, 0.65f);
        samples[Id.JUMP.ordinal()] = sweep(0.16f, 320f, 620f, 0.22f);
        samples[Id.LAND.ordinal()] = thud(0.14f, 70f, 0.45f);
        samples[Id.SHARD.ordinal()] = chime();
        samples[Id.HURT.ordinal()] = sweep(0.26f, 260f, 90f, 0.5f);
        samples[Id.VICTORY.ordinal()] = fanfare();
        samples[Id.UI_TAP.ordinal()] = sweep(0.07f, 900f, 1400f, 0.18f);

        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = createTrack();
        }
    }

    private AudioTrack createTrack() {
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, SAMPLE_RATE / 4);
        AudioTrack track;
        if (Build.VERSION.SDK_INT >= 23) {
            track = new AudioTrack.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
        } else {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufSize, AudioTrack.MODE_STATIC);
        }
        return track;
    }

    public void play(Id id) {
        play(id, 1f);
    }

    public void play(Id id, float gain) {
        if (!enabled) {
            return;
        }
        short[] data = samples[id.ordinal()];
        if (data == null) {
            return;
        }
        AudioTrack track = voices[nextVoice];
        nextVoice = (nextVoice + 1) % MAX_VOICES;
        try {
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                return;
            }
            track.stop();
            track.reloadStaticData();
            track.write(data, 0, data.length);
            track.setPlaybackHeadPosition(0);
            track.setVolume(volume * gain);
            track.play();
        } catch (IllegalStateException ignored) {
            // Audio is best-effort: never let sound break the game loop.
        }
    }

    public void setEnabled(boolean on) {
        enabled = on;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    public void release() {
        for (AudioTrack t : voices) {
            if (t != null) {
                try {
                    t.stop();
                } catch (IllegalStateException ignored) {
                    // already stopped
                }
                t.release();
            }
        }
    }

    // ------------------------------------------------------------ synthesis

    private static short[] allocate(float seconds) {
        return new short[Math.max(1, (int) (SAMPLE_RATE * seconds))];
    }

    private static short clamp16(float v) {
        int i = (int) (v * 32767f);
        return (short) Math.max(-32768, Math.min(32767, i));
    }

    /** Noise burst with a falling band-pass character: a blade whoosh. */
    private static short[] swing() {
        short[] out = allocate(0.22f);
        java.util.Random rnd = new java.util.Random(7);
        float lp = 0f;
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / out.length;
            float env = (float) Math.pow(1f - t, 1.6) * Math.min(1f, t * 18f);
            float white = rnd.nextFloat() * 2f - 1f;
            lp += (white - lp) * (0.55f - 0.4f * t);
            out[i] = clamp16(lp * env * 0.7f);
        }
        return out;
    }

    /** Low sine thump with a click transient. */
    private static short[] thud(float seconds, float freq, float gain) {
        short[] out = allocate(seconds);
        java.util.Random rnd = new java.util.Random(11);
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / out.length;
            float env = (float) Math.pow(1f - t, 2.4);
            float tone = (float) Math.sin(2.0 * Math.PI * freq * (1f - 0.3f * t) * i / SAMPLE_RATE);
            float click = (rnd.nextFloat() * 2f - 1f) * (float) Math.pow(1f - t, 12f) * 0.5f;
            out[i] = clamp16((tone * 0.8f + click) * env * gain);
        }
        return out;
    }

    /** Exponential frequency sweep, used for jumps, hurt and UI taps. */
    private static short[] sweep(float seconds, float f0, float f1, float gain) {
        short[] out = allocate(seconds);
        double phase = 0.0;
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / out.length;
            float f = f0 + (f1 - f0) * t;
            phase += 2.0 * Math.PI * f / SAMPLE_RATE;
            float env = (float) Math.sin(Math.PI * Math.pow(t, 0.55));
            float s = (float) Math.sin(phase) + 0.25f * (float) Math.sin(phase * 2f);
            out[i] = clamp16(s * env * gain);
        }
        return out;
    }

    /** Bright bell arpeggio for shard pickups. */
    private static short[] chime() {
        short[] out = allocate(0.55f);
        float[] freqs = {880f, 1320f, 1760f, 2640f};
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * 5.5);
            float s = 0f;
            for (int k = 0; k < freqs.length; k++) {
                float delay = k * 0.035f;
                if (t >= delay) {
                    float tl = t - delay;
                    s += (float) Math.sin(2.0 * Math.PI * freqs[k] * tl) * (float) Math.exp(-tl * 6f) / (k + 1.6f);
                }
            }
            out[i] = clamp16(s * env * 0.55f);
        }
        return out;
    }

    /** Rising major arpeggio for the victory screen. */
    private static short[] fanfare() {
        short[] out = allocate(1.5f);
        float[] notes = {523.25f, 659.25f, 783.99f, 1046.5f};
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / SAMPLE_RATE;
            float s = 0f;
            for (int k = 0; k < notes.length; k++) {
                float start = k * 0.19f;
                if (t >= start) {
                    float tl = t - start;
                    float env = (float) Math.exp(-tl * 2.2) * Math.min(1f, tl * 40f);
                    s += (float) Math.sin(2.0 * Math.PI * notes[k] * tl) * env * 0.28f;
                    s += (float) Math.sin(2.0 * Math.PI * notes[k] * 2f * tl) * env * 0.08f;
                }
            }
            out[i] = clamp16(s);
        }
        return out;
    }

    public static int sampleRate() {
        return SAMPLE_RATE;
    }
}
