package com.laststar.game.core;

/** Deterministic, save-friendly day/night cycle driven by simulation seconds. */
public final class DayNightCycle {

    /** One complete in-game day/night in seconds (six real minutes). */
    public static final float DAY_LENGTH_SECONDS = 360f;

    private DayNightCycle() {
    }

    /**
     * Returns a smooth 0..1 night intensity: noon/day at cycle start,
     * sunset/dawn at quarter turns, and full night halfway through.
     */
    public static float nightAmount(float elapsedSeconds) {
        if (Float.isNaN(elapsedSeconds) || Float.isInfinite(elapsedSeconds)) return 0f;
        float phase = elapsedSeconds % DAY_LENGTH_SECONDS;
        if (phase < 0f) phase += DAY_LENGTH_SECONDS;
        double angle = phase * (Math.PI * 2.0 / DAY_LENGTH_SECONDS);
        return (float) (0.5 - 0.5 * Math.cos(angle));
    }

    /** Normalized time in the repeating day, in the half-open range [0, 1). */
    public static float phase(float elapsedSeconds) {
        if (Float.isNaN(elapsedSeconds) || Float.isInfinite(elapsedSeconds)) return 0f;
        float phase = (elapsedSeconds % DAY_LENGTH_SECONDS) / DAY_LENGTH_SECONDS;
        return phase < 0f ? phase + 1f : phase;
    }
}
