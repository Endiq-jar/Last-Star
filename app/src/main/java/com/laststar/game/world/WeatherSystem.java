package com.laststar.game.world;

/** Deterministic regional weather that can be reproduced from save time. */
public final class WeatherSystem {

    public enum Type { CLEAR, RAIN, SNOW, MIST }

    public static final class Sample {
        public final Type type;
        /** Overall effect opacity in [0,1]. */
        public final float intensity;

        private Sample(Type type, float intensity) {
            this.type = type;
            this.intensity = intensity;
        }
    }

    private static final float WEATHER_PERIOD = 120f;

    private WeatherSystem() {
    }

    /**
     * Selects a stable weather front per region and two-minute simulation
     * interval. Snow fraction comes from the region's authored terrain profile.
     */
    public static Sample sample(int regionIndex, float snowFraction, float worldTime) {
        int interval = (int) Math.floor(Math.max(0f, worldTime) / WEATHER_PERIOD);
        int hash = regionIndex * 0x45d9f3b + interval * 0x119de1f3 + 0x2c1b3c6d;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        int roll = (hash & 0x7fffffff) % 100;
        if (snowFraction >= 0.35f) {
            if (roll < 48) return new Sample(Type.SNOW, 0.56f);
            if (roll < 68) return new Sample(Type.MIST, 0.28f);
            return new Sample(Type.CLEAR, 0f);
        }
        if (roll < 27) return new Sample(Type.RAIN, 0.48f);
        if (roll < 43) return new Sample(Type.MIST, 0.26f);
        return new Sample(Type.CLEAR, 0f);
    }
}
