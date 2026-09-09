package catchrelease.tools;

import java.util.ArrayDeque;
import java.util.Random;

final class SimulatedAngler {

    enum Skill {

        BEGINNER("Beginner", 0.12f, 0.08f, 0.018f, 0.05f),
        REGULAR("Regular", 0.085f, 0.05f, 0.008f, 0.12f),
        SKILLED("Skilled", 0.06f, 0.035f, 0.003f, 0.18f),
        MANUAL("Manual", 0, 0, 0, 0);

        final String label;
        final float delay;
        final float interval;
        final float error;
        final float anticipation;

        Skill(String label, float delay, float interval, float error, float anticipation) {
            this.label = label;
            this.delay = delay;
            this.interval = interval;
            this.error = error;
            this.anticipation = anticipation;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Skill skill;
    private final Random random;
    private final ArrayDeque<Observation> history = new ArrayDeque<>();
    private Observation previous;
    private float fishSpeed;
    private float decisionAt;
    private boolean held;

    private record Observation(float time, float fish, float bar) {

    }

    SimulatedAngler(Skill skill, long seed) {
        this.skill = skill;
        random = new Random(seed);
    }

    // Only screen observations enter here; no simulation object or future targets.
    boolean input(float time, float fish, float bar, float height, float lift, float gravity) {
        history.addLast(new Observation(time, fish, bar + height * 0.5f));
        Observation observed = null;
        while (!history.isEmpty() && history.peekFirst().time <= time - skill.delay) observed = history.removeFirst();
        if (observed == null) return held;
        if (previous == null) {
            previous = observed;
            return held;
        }
        if (time < decisionAt) return held;
        float dt = observed.time - previous.time;
        if (dt <= 0f) return held;
        float sampleSpeed = Math.max(-3f, Math.min(3f, (observed.fish - previous.fish) / dt));
        float blend = 1f - (float) Math.exp(-dt / 0.18f);
        fishSpeed += (sampleSpeed - fishSpeed) * blend;
        float barSpeed = (observed.bar - previous.bar) / dt;
        float currentBar = observed.bar + barSpeed * skill.delay;
        float target = observed.fish + fishSpeed * (skill.delay + skill.anticipation)
                + (random.nextFloat() * 2f - 1f) * skill.error;
        target = Math.max(height * 0.5f, Math.min(1f - height * 0.5f, target));
        float brake = barSpeed > 0f ? gravity : lift;
        float stoppingPoint = currentBar + barSpeed * Math.abs(barSpeed) / (2f * brake);
        held = target > stoppingPoint;
        previous = observed;
        decisionAt = time + skill.interval;
        return held;
    }
}
