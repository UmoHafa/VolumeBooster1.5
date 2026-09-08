package com.volumeboostersoundboost.app;
import android.media.audiofx.AudioEffect;
public class EngineTests {
    private static int checks;
    private static final Runnable NOOP = new Runnable() { @Override public void run() {} };
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(BoostConfig.clamp(-10, 60) == 0, "lower bound");
        check(BoostConfig.clamp(100, 60) == 40, "hard upper bound");
        check(BoostConfig.clamp(60, 25) == 25, "user maximum");
        check(BoostConfig.clamp(40, 0) == 0, "zero maximum");
        check(BoostConfig.clamp(40, -1) == 0, "invalid maximum");
        check(BoostConfig.gainMb(0) == 0, "zero gain");
        check(BoostConfig.gainMb(10) == 300, "10% scale");
        check(BoostConfig.gainMb(40) == 1200, "ceiling");
        check(BoostConfig.gainMb(999) == 1200, "ceiling enforced");
        check(BoostConfig.eqGainMb(60, -1500, 800, 1000000, false) == 800, "device cap");
        check(BoostConfig.eqGainMb(60, -1500, 1500, 60000, true) == 600, "low shaping");
        check(BoostConfig.eqGainMb(60, -1500, 1500, 14000000, true) == 600, "high shaping");
        check(BoostConfig.eqGainMb(60, -1500, 1500, 1000000, true) == 1200, "mid shaping");
        check(BoostConfig.engines(0, 0)[0] == BoostConfig.DYNAMICS, "global preference");
        check(BoostConfig.engines(0, 15)[0] == BoostConfig.DYNAMICS, "player preference");
        check(BoostConfig.engines(1, 15).length == 1, "manual EQ no fallback");
        check(BoostConfig.engines(2, 0).length == 1, "manual LE no fallback");
        for (int percent = 0; percent < BoostConfig.HARD_LIMIT; percent++)
            check(BoostConfig.gainMb(percent + 1) >= BoostConfig.gainMb(percent), "monotonic gain");

        AudioEffect.reset();
        SessionEffect effect = new SessionEffect(0, 0, 20, true, NOOP);
        check(effect.usable() && effect.enabledAndControlled(), "global engine enabled");
        check(effect.describe().startsWith("Dynamics Processing"), "correct global engine");
        check(AudioEffect.live == 1 && AudioEffect.peak == 1, "one effect only");
        effect.apply(0, true);
        check(effect.usable() && !effect.enabledAndControlled(), "zero disables");
        effect.release();
        check(AudioEffect.live == 0, "released");

        AudioEffect.reset();
        effect = new SessionEffect(32, 0, 20, true, NOOP);
        check(effect.describe().startsWith("Dynamics Processing"), "specific engine selected");
        effect.release();

        AudioEffect.reset();
        AudioEffect.createDynamicsFails = true;
        effect = new SessionEffect(0, 0, 20, true, NOOP);
        check(effect.enabledAndControlled(), "automatic fallback works");
        check(effect.describe().startsWith("Loudness Enhancer"), "first fallback engine");
        check(!effect.failures.isEmpty(), "failure retained for diagnostics");
        check(AudioEffect.peak == 1, "fallback not stacked");
        effect.release();

        AudioEffect.reset();
        AudioEffect.createDynamicsFails = true;
        AudioEffect.createLoudnessFails = true;
        effect = new SessionEffect(0, 0, 20, true, NOOP);
        check(effect.enabledAndControlled(), "second automatic fallback works");
        check(effect.describe().startsWith("Equalizer"), "second fallback engine");
        effect.release();


        AudioEffect.reset();
        AudioEffect.createDynamicsFails = true;
        effect = new SessionEffect(0, BoostConfig.DYNAMICS, 20, true, NOOP);
        check(!effect.usable() && AudioEffect.live == 0, "manual dynamics does not silently switch");
        effect.release();

        AudioEffect.reset();
        AudioEffect.createEqFails = true;
        effect = new SessionEffect(0, 1, 20, true, NOOP);
        check(!effect.usable() && AudioEffect.live == 0, "manual engine doesn't silently switch");
        effect.release();

        AudioEffect.reset();
        AudioEffect.noControl = true;
        effect = new SessionEffect(0, 0, 20, true, NOOP);
        check(!effect.usable() && !effect.enabledAndControlled(), "no false success without control");
        check(AudioEffect.live == 0, "uncontrolled candidates released");

        AudioEffect.reset();
        AudioEffect.enableFails = true;
        effect = new SessionEffect(0, 0, 20, true, NOOP);
        check(!effect.enabledAndControlled(), "enable failure visible");
        check(AudioEffect.live == 0, "failed effect released");

        AudioEffect.reset();
        AudioEffect.mismatch = true;
        effect = new SessionEffect(0, 0, 20, false, NOOP);
        check(!effect.usable(), "readback mismatch rejected");
        check(AudioEffect.live == 0, "mismatch engines released");

        AudioEffect.reset();
        effect = new SessionEffect(0, 0, 20, true, NOOP);
        AudioEffect.disableFails = true;
        effect.release();
        check(AudioEffect.live == 0 && AudioEffect.released == 1, "release survives disable error");
        effect.release();
        check(AudioEffect.released == 1, "release idempotent");
        System.out.println("PASS: " + checks + " deterministic/fake-backend checks.");
        System.out.println("These tests do not measure phone audio or verify Android runtime UI.");
    }
}
