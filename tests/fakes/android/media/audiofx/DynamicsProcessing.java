package android.media.audiofx;
public class DynamicsProcessing extends AudioEffect {
    private float gain;
    private final int channels = 2;
    public DynamicsProcessing(int priority, int session, Object config) {
        if (createDynamicsFails) { release(); throw new UnsupportedOperationException("Dynamics missing"); }
    }
    public void setInputGainAllChannelsTo(float value) { gain = value; }
    public int getChannelCount() { return channels; }
    public float getInputGainByChannelIndex(int channel) { return mismatch ? 0f : gain; }
}
