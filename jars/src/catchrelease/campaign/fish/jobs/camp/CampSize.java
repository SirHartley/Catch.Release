package catchrelease.campaign.fish.jobs.camp;

import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.Random;

/**
 * How much is sitting on the fishing spot, which is the whole of the job's difficulty.
 * <p>
 * The three rungs are meant to be told apart from the bar, before the player has seen anything -
 * the fisher says how many there were and that is the only warning they get. So the words matter as
 * much as the numbers: "a couple of hulls" and "the better part of a raiding pack" have to mean
 * something reliable, or the estimate is worthless and the player stops listening to it.
 * <p>
 * The bribe scales harder than the reward. Buying off a big pack is deliberately a bad deal - it is
 * there so a player who cannot fight one still has a way through, not so that paying is the sensible
 * answer to everything.
 */
public enum CampSize {

    SMALL("a couple of hulls", 25f, 40f, 20000, 9000),
    MEDIUM("the better part of a squadron", 55f, 85f, 45000, 18000),
    LARGE("a raiding pack", 100f, 150f, 90000, 34000);

    /** What the fisher says they saw. */
    public final String describe;

    /** Combat strength the fleet is built to, in fleet points. */
    public final float minFP;
    public final float maxFP;

    /** What they will take to go away, and what the job pays. */
    public final int bribe;
    public final int value;

    CampSize(String describe, float minFP, float maxFP, int bribe, int value) {
        this.describe = describe;
        this.minFP = minFP;
        this.maxFP = maxFP;
        this.bribe = bribe;
        this.value = value;
    }

    public float rollFP(Random random) {
        return minFP + random.nextFloat() * (maxFP - minFP);
    }

    /**
     * Weighted towards the small end.
     * <p>
     * A raiding pack parked on a pond is a serious piece of work for a fleet that has been fishing
     * rather than fighting, and one turning up every other time it was offered would make the whole
     * family read as a combat job with a fish attached.
     */
    public static CampSize roll(Random random) {
        WeightedRandomPicker<CampSize> picker = new WeightedRandomPicker<>(random);

        picker.add(SMALL, 5f);
        picker.add(MEDIUM, 3f);
        picker.add(LARGE, 1.5f);

        return picker.pick();
    }
}
