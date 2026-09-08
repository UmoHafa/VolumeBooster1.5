package com.volumeboostersoundboost.app;

/** Percent is a control scale, not measured loudness. */
public final class BoostConfig {
    public static final int AUTO = 0, EQUALIZER = 1, LOUDNESS = 2, DYNAMICS = 3;
    public static final int HARD_LIMIT = 40, MAX_GAIN_MB = 1200;
    private BoostConfig() {}
    public static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(Math.max(0, Math.min(HARD_LIMIT, maximum)), value));
    }
    public static int gainMb(int percent) { return clamp(percent, HARD_LIMIT) * MAX_GAIN_MB / HARD_LIMIT; }
    public static float gainDb(int percent) { return gainMb(percent) / 100f; }
    public static int eqGainMb(int percent, int min, int max, int frequencyMilliHz, boolean nonUniform) {
        int target = gainMb(percent);
        if (nonUniform && (frequencyMilliHz < 250000 || frequencyMilliHz > 4000000)) target /= 2;
        return Math.max(min, Math.min(max, target));
    }
    public static int[] engines(int mode, int session) {
        if (mode == EQUALIZER) return new int[]{EQUALIZER};
        if (mode == LOUDNESS) return new int[]{LOUDNESS};
        if (mode == DYNAMICS) return new int[]{DYNAMICS};
        // DynamicsProcessing exposes a real input-gain stage plus a limiter on Android 9+.
        // LoudnessEnhancer and Equalizer remain fallbacks for vendor implementations.
        return new int[]{DYNAMICS, LOUDNESS, EQUALIZER};
    }
    public static String engineName(int mode) {
        if (mode == EQUALIZER) return "Equalizer";
        if (mode == LOUDNESS) return "Loudness Enhancer";
        if (mode == DYNAMICS) return "Dynamics Processing";
        return "Automatic";
    }
}
