package android.media.audiofx;
public class Equalizer extends AudioEffect {
    private final short[] levels = new short[3];
    public Equalizer(int priority, int session) {
        if (createEqFails) { release(); throw new UnsupportedOperationException("EQ missing"); }
    }
    public short[] getBandLevelRange() { return new short[]{-1500, 1500}; }
    public short getNumberOfBands() { return 3; }
    public int getCenterFreq(short band) { return new int[]{60000, 1000000, 14000000}[band]; }
    public void setBandLevel(short band, short value) { levels[band] = value; }
    public short getBandLevel(short band) { return mismatch ? 0 : levels[band]; }
}
