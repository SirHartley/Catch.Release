package catchrelease.campaign.fish.jobs.camp;

import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.Random;

public enum CampSize {

    SMALL("a couple of hulls", 0.8f, 9000),
    MEDIUM("the better part of a squadron", 1f, 18000),
    LARGE("a raiding pack", 1.2f, 34000);

    // a camp stays a normal fight, 3 skulls in vanilla's danger rating (0.67 to 1.33)
    public static final float STRENGTH_SPREAD = 0.05f;

    public final String describe;
    // times the player's strength
    public final float strength;
    public final int value;

    CampSize(String describe, float strength, int value) {
        this.describe = describe;
        this.strength = strength;
        this.value = value;
    }

    public float rollStrength(Random random) {
        return strength * (1f - STRENGTH_SPREAD + random.nextFloat() * 2f * STRENGTH_SPREAD);
    }

    public static CampSize roll(Random random) {
        WeightedRandomPicker<CampSize> picker = new WeightedRandomPicker<>(random);

        picker.add(SMALL, 5f);
        picker.add(MEDIUM, 3f);
        picker.add(LARGE, 1.5f);

        return picker.pick();
    }
}
