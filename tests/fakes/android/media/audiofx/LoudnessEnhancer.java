package android.media.audiofx;
public class LoudnessEnhancer extends AudioEffect {
    private int gain;
    public LoudnessEnhancer(int session) {
        if (createLoudnessFails) { release(); throw new UnsupportedOperationException("LE missing"); }
    }
    public void setTargetGain(int value) { gain = value; }
    public float getTargetGain() { return nonFinite ? Float.POSITIVE_INFINITY : mismatch ? 0 : gain; }
}
