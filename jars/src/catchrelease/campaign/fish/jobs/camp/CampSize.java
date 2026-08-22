package catchrelease.campaign.fish.jobs.camp;

import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.Random;


public enum CampSize {

    SMALL("a couple of hulls", 25f, 40f, 20000, 9000),
    MEDIUM("the better part of a squadron", 55f, 85f, 45000, 18000),
    LARGE("a raiding pack", 100f, 150f, 90000, 34000);


    public final String describe;


    public final float minFP;
    public final float maxFP;


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


    public static CampSize roll(Random random) {
        WeightedRandomPicker<CampSize> picker = new WeightedRandomPicker<>(random);

        picker.add(SMALL, 5f);
        picker.add(MEDIUM, 3f);
        picker.add(LARGE, 1.5f);

        return picker.pick();
    }
}
