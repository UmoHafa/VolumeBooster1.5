package android.media.audiofx;
public class AudioEffect {
    public static final int SUCCESS = 0;
    public static int live, peak, released;
    public static boolean createEqFails, createLoudnessFails, createDynamicsFails, noControl, enableFails, disableFails, mismatch;
    protected boolean enabled, closed;
    public AudioEffect() { live++; peak = Math.max(peak, live); }
    public static void reset() {
        live = peak = released = 0;
        createEqFails = createLoudnessFails = createDynamicsFails = noControl = enableFails = disableFails = mismatch = false;
    }
    public boolean hasControl() { return !noControl; }
    public boolean getEnabled() { return enabled; }
    public int setEnabled(boolean value) {
        if (!value && disableFails) throw new IllegalStateException("disable failed");
        if (enableFails) return -5;
        enabled = value; return SUCCESS;
    }
    public void release() { if (!closed) { live--; released++; closed = true; } }
    public interface OnControlStatusChangeListener { void onControlStatusChange(AudioEffect e, boolean control); }
    public interface OnEnableStatusChangeListener { void onEnableStatusChange(AudioEffect e, boolean enabled); }
    public void setControlStatusListener(OnControlStatusChangeListener listener) {}
    public void setEnableStatusListener(OnEnableStatusChangeListener listener) {}
}
