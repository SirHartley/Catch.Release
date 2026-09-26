package catchrelease.tools;

import java.util.Arrays;
import java.util.Random;

// A human-like angler fitted to recorded manual play by FishAnglerCalibration. On each display tick it compares
// where it expects the fish, seen fishDelay steps ago and led by its seen speed, with where the bar would stop,
// adds aim noise, and switches only after a minimum press or pause. Rerun the calibration after new recordings
// and paste its output here.
final class PlayerModel {

    // the recording machine drew the tuner at 32 Hz, so input could change only this often
    static final float TICK = 1f / 32f;
    static final float SPEED_SMOOTHING = 0.1f;
    static final Profile RECORDED = new Profile(18, 0.282f, 0.028f, 1.093f, 0.035f, 0.014f, 0.047f, 0.110f, 0.038f);

    // lead in seconds of seen fish speed; aim, noise and the two thresholds in track fractions; momentum 0 ignores
    // bar speed, 1 brakes exactly; minimum press and release in seconds
    record Profile(int fishDelay, float lead, float aim, float momentum, float noise, float pressBand, float releaseBand,
                   float minPress, float minRelease) {

    }

    private final Profile profile;
    private final Random random;
    private float[] fish = new float[256];
    private float[] fishSpeed = new float[256];
    private int steps;
    private float bar;
    private float barSpeed;
    private float nextTick;
    private float lastSwitch;
    private boolean held;

    PlayerModel(Profile profile, Random random) {
        this.profile = profile;
        this.random = random;
    }

    // only screen observations enter here, one call per simulation step
    boolean input(float time, float visibleFish, float barCentre, float lift, float gravity) {
        observe(visibleFish, barCentre);
        if (time + 1e-6f < nextTick) return held;
        nextTick += TICK;
        int seen = steps - 1 - profile.fishDelay;
        if (seen < 1) return held;
        float brake = barSpeed > 0f ? gravity : lift;
        double want = fish[seen] + fishSpeed[seen] * profile.lead + profile.aim
                - (bar + profile.momentum * barSpeed * Math.abs(barSpeed) / (2f * brake))
                + random.nextGaussian() * profile.noise;
        float since = time - lastSwitch;
        boolean next = held ? !(since >= profile.minPress && want < -profile.releaseBand)
                : since >= profile.minRelease && want > profile.pressBand;
        if (next != held) lastSwitch = time;
        held = next;
        return held;
    }

    private void observe(float visibleFish, float barCentre) {
        if (steps == fish.length) {
            fish = Arrays.copyOf(fish, steps * 2);
            fishSpeed = Arrays.copyOf(fishSpeed, steps * 2);
        }
        fish[steps] = visibleFish;
        if (steps > 0) {
            float sample = Math.max(-3f, Math.min(3f, (visibleFish - fish[steps - 1]) / FishTuningSession.STEP));
            float blend = 1f - (float) Math.exp(-FishTuningSession.STEP / SPEED_SMOOTHING);
            fishSpeed[steps] = fishSpeed[steps - 1] + (sample - fishSpeed[steps - 1]) * blend;
            barSpeed = (barCentre - bar) / FishTuningSession.STEP;
        }
        bar = barCentre;
        steps++;
    }
}
