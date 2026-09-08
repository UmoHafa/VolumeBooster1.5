package com.volumeboostersoundboost.app;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import java.util.ArrayList;

/** Owns exactly one native effect; fallback engines are tried only after release. */
final class SessionEffect {
    final ArrayList<String> failures = new ArrayList<String>();
    private AudioEffect effect;
    private String name = "No engine";
    private String detail = "No compatible effect";
    private boolean configured;
    private boolean released;
    private int percent;
    private boolean nonUniform;

    SessionEffect(int session, int mode, int boost, boolean shape, final Runnable changed) {
        for (int engine : BoostConfig.engines(mode, session)) {
            name = BoostConfig.engineName(engine);
            try {
                if (engine == BoostConfig.DYNAMICS) {
                    if (Build.VERSION.SDK_INT < 28) {
                        detail = "Requires Android 9+";
                        failures.add("Session " + session + " " + name + ": " + detail);
                        continue;
                    }
                    // High priority helps this client retain control when another EQ/effect app exists.
                    effect = new DynamicsProcessing(10000, session, null);
                } else if (engine == BoostConfig.EQUALIZER) {
                    effect = new Equalizer(10000, session);
                } else {
                    effect = new LoudnessEnhancer(session);
                }
                if (apply(boost, shape)) {
                    effect.setControlStatusListener(new AudioEffect.OnControlStatusChangeListener() {
                        @Override public void onControlStatusChange(AudioEffect source, boolean control) {
                            if (released) return;
                            if (control) apply(percent, nonUniform);
                            changed.run();
                        }
                    });
                    effect.setEnableStatusListener(new AudioEffect.OnEnableStatusChangeListener() {
                        @Override public void onEnableStatusChange(AudioEffect source, boolean enabled) {
                            if (!released) changed.run();
                        }
                    });
                    return;
                }
            } catch (Throwable error) {
                detail = error.getClass().getSimpleName();
            }
            failures.add("Session " + session + " " + name + ": " + detail);
            releaseNative();
        }
    }

    boolean apply(int boost, boolean shape) {
        percent = boost;
        nonUniform = shape;
        configured = false;
        if (effect == null) return false;
        try {
            if (!effect.hasControl()) { detail = "Control held by another client"; return false; }
            if (Build.VERSION.SDK_INT >= 28 && effect instanceof DynamicsProcessing) {
                DynamicsProcessing dp = (DynamicsProcessing) effect;
                float requestedDb = BoostConfig.gainDb(boost);
                dp.setInputGainAllChannelsTo(requestedDb);
                int channels = dp.getChannelCount();
                if (channels <= 0) {
                    detail = "No DynamicsProcessing channels";
                    effect.setEnabled(false);
                    return false;
                }
                float lowestReadback = Float.MAX_VALUE;
                for (int channel = 0; channel < channels; channel++) {
                    float readback = dp.getInputGainByChannelIndex(channel);
                    lowestReadback = Math.min(lowestReadback, readback);
                    if (Math.abs(readback - requestedDb) > 0.15f) {
                        detail = "Input gain readback mismatch on channel " + channel + ": " + readback + " dB";
                        effect.setEnabled(false);
                        return false;
                    }
                }
                detail = channels + " channels; input gain readback " + lowestReadback + " dB; limiter enabled by default";
            } else if (effect instanceof LoudnessEnhancer) {
                LoudnessEnhancer le = (LoudnessEnhancer) effect;
                int requested = BoostConfig.gainMb(boost);
                le.setTargetGain(requested);
                float readback = le.getTargetGain();
                if (Math.abs(readback - requested) > 1f) {
                    detail = "Gain readback mismatch: " + readback + " mB";
                    effect.setEnabled(false);
                    return false;
                }
                detail = "gain readback " + readback / 100f + " dB";
            } else {
                Equalizer eq = (Equalizer) effect;
                short[] range = eq.getBandLevelRange();
                short bands = eq.getNumberOfBands();
                if (bands <= 0 || range[1] <= 0) {
                    detail = "No positive-gain bands";
                    effect.setEnabled(false);
                    return false;
                }
                int highest = 0;
                for (short band = 0; band < bands; band++) {
                    int target = BoostConfig.eqGainMb(boost, range[0], range[1],
                            eq.getCenterFreq(band), shape);
                    eq.setBandLevel(band, (short) target);
                    int readback = eq.getBandLevel(band);
                    if (Math.abs(readback - target) > 50) {
                        detail = "Band " + band + " gain readback mismatch";
                        effect.setEnabled(false);
                        return false;
                    }
                    highest = Math.max(highest, readback);
                }
                detail = bands + " bands; highest readback " + highest / 100f + " dB";
            }
            boolean enable = boost > 0;
            int result = effect.setEnabled(enable);
            if (result != AudioEffect.SUCCESS || effect.getEnabled() != enable || !effect.hasControl()) {
                detail = "Enable/control check failed; Android result=" + result;
                releaseNative();
                return false;
            }
            configured = true;
            return true;
        } catch (Throwable error) {
            detail = error.getClass().getSimpleName();
            releaseNative();
            return false;
        }
    }

    boolean usable() {
        try { return configured && effect != null && effect.hasControl(); }
        catch (Throwable error) { return false; }
    }

    boolean enabledAndControlled() {
        try { return usable() && effect.getEnabled(); }
        catch (Throwable error) { return false; }
    }

    String describe() {
        return name + "; controlled/configured=" + usable() + "; enabled=" + enabledAndControlled() + "; " + detail;
    }

    void release() { released = true; releaseNative(); }

    private void releaseNative() {
        AudioEffect old = effect;
        effect = null;
        configured = false;
        if (old == null) return;
        try { old.setControlStatusListener(null); old.setEnableStatusListener(null); } catch (Throwable ignored) {}
        try { if (old.hasControl()) old.setEnabled(false); } catch (Throwable ignored) {}
        try { old.release(); } catch (Throwable ignored) {}
    }
}
