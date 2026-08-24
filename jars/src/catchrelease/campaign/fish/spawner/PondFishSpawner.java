package catchrelease.campaign.fish.spawner;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishHabitat;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class PondFishSpawner {

    public static String pickFishId(LocationAPI location, CatchImplement how) {
        return pickFishId(location, how, 0f);
    }

    public static String pickFishId(LocationAPI location, CatchImplement how, float extraRarityBias) {
        FishSpec spec = pickFish(location, how, extraRarityBias);

        return spec == null ? null : spec.id;
    }

    public static FishSpec pickFish(LocationAPI location, CatchImplement how) {
        return pickFish(location, how, 0f);
    }

    public static FishSpec pickFish(LocationAPI location, CatchImplement how, float extraRarityBias) {
        FishHabitat where = FishHabitat.of(location);

        extraRarityBias += FishRumors.getRarityBias(location);

        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec.spawnWeight <= 0f) continue;
            if (!FishRanges.matches(spec, location, how)) continue;

            picker.add(spec, spec.spawnWeight * getRarityWeight(spec, extraRarityBias));
        }

        // a rumored stranger swims where it should not, competing at a fixed weight
        String strangerId = FishRumors.getStrangerId(location);
        if (strangerId != null) {
            FishSpec stranger = FishSpecLoader.getFishSpec(strangerId);
            if (stranger != null && stranger.canBeReachedBy(how)) {
                picker.add(stranger, FishermanConstants.RUMOR_STRANGER_WEIGHT);
            }
        }

        if (picker.isEmpty()) {
            Global.getLogger(PondFishSpawner.class).warn("Nothing lives in " + describe(where)
                    + " on " + how + " - check " + FishSpecLoader.PATH);
            return null;
        }

        return picker.pick();
    }

    protected static String describe(FishHabitat where) {
        if (where == null) return "nowhere";

        return where.region + " under " + where.star + ", " + where.age
                + ", aberration " + String.format("%.2f", where.aberration);
    }

    protected static float getRarityWeight(FishSpec spec, float extraBias) {
        float bias = TackleManager.get(Tackle.Fit.DRONE).rarityBias + extraBias;
        if (bias == 1f || spec.rarity == null) return 1f;

        return (float) Math.pow(bias, spec.rarity.rank);
    }
}
