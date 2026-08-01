package catchrelease.campaign.fish.data;

/**
 * How a fish moves inside the catch bar during the minigame. Each archetype picks its next target
 * position differently; {@link FishSpec#motionSpeed} and {@link FishSpec#restlessness} then say how
 * fast it gets there and how often it changes its mind.
 */
public enum FishMotion {

    /** Drifts to a new spot and settles. The baseline. */
    SMOOTH,

    /** Sits still, then bolts. Long pauses, sudden jumps. */
    DARTER,

    /** Favours the bottom of the bar. */
    SINKER,

    /** Favours the top of the bar. */
    FLOATER,

    /** Switches between the other archetypes as it goes. */
    MIXED;

    public static FishMotion parse(String name, FishMotion fallback) {
        if (name == null) return fallback;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
